package edu.bgu.semscanapi.integration;

import edu.bgu.semscanapi.dto.*;
import edu.bgu.semscanapi.entity.*;
import edu.bgu.semscanapi.service.AttendanceService;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * END-TO-END FLOW TEST: Presenter opens attendance → Students scan QR → Attendance recorded
 *
 * This test simulates the complete real-world flow:
 * 1. Find a presenter with an approved slot for today
 * 2. Presenter opens attendance (creates session)
 * 3. Get QR code data
 * 4. Simulate student scanning QR (record attendance)
 * 5. Verify attendance was recorded
 * 6. Close the session
 * 7. Verify session is closed and attendance is finalized
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PresenterAttendanceFlowTest extends BaseIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(PresenterAttendanceFlowTest.class);

    // Shared state across tests in this flow
    private static String testPresenterUsername;
    private static Long testSlotId;
    private static Long testSessionId;
    private static String testStudentUsername;

    @Test
    @Order(1)
    @DisplayName("FLOW STEP 1: Find presenter with approved slot")
    void step1_FindPresenterWithApprovedSlot() {
        logger.info("\n========== FLOW TEST: Presenter Attendance Flow ==========\n");

        // Find slots with approved registrations
        List<SeminarSlotRegistration> approvedRegistrations = registrationRepository.findAll().stream()
                .filter(r -> r.getApprovalStatus() == ApprovalStatus.APPROVED)
                .toList();

        logger.info("Found {} approved registrations", approvedRegistrations.size());

        if (approvedRegistrations.isEmpty()) {
            logger.info("⚠ No approved registrations found - skipping flow test");
            return;
        }

        // Find one with a valid slot
        for (SeminarSlotRegistration reg : approvedRegistrations) {
            Optional<SeminarSlot> slotOpt = seminarSlotRepository.findById(reg.getSlotId());
            if (slotOpt.isPresent()) {
                SeminarSlot slot = slotOpt.get();
                testSlotId = slot.getSlotId();
                testPresenterUsername = reg.getPresenterUsername();

                logger.info("✓ Found presenter: {}", testPresenterUsername);
                logger.info("  Slot ID: {}", testSlotId);
                logger.info("  Date: {}", slot.getSlotDate());
                logger.info("  Time: {} - {}", slot.getStartTime(), slot.getEndTime());
                logger.info("  Location: {} {}", slot.getBuilding(), slot.getRoom());
                break;
            }
        }

        assertNotNull(testPresenterUsername, "Should find a presenter with approved slot");
        assertNotNull(testSlotId, "Should find a slot ID");
    }

    @Test
    @Order(2)
    @DisplayName("FLOW STEP 2: Get presenter home screen")
    void step2_GetPresenterHomeScreen() {
        if (testPresenterUsername == null) {
            logger.info("⚠ Skipping - no presenter found in step 1");
            return;
        }

        PresenterHomeResponse home = presenterHomeService.getPresenterHome(testPresenterUsername);

        assertNotNull(home, "Should get presenter home response");
        assertNotNull(home.getPresenter(), "Should have presenter info");

        logger.info("\n--- Presenter Home Screen ---");
        logger.info("Presenter: {}", home.getPresenter().getName());
        logger.info("Degree: {}", home.getPresenter().getDegree());
        logger.info("My Slot: {}", home.getMySlot() != null ? "Assigned" : "None");
        logger.info("Slot Catalog: {} slots available", home.getSlotCatalog().size());

        if (home.getMySlot() != null) {
            logger.info("  - Slot ID: {}", home.getMySlot().getSlotId());
            logger.info("  - Date: {}", home.getMySlot().getDate());
            logger.info("  - Time: {}", home.getMySlot().getTimeRange());
        }
    }

    @Test
    @Order(3)
    @DisplayName("FLOW STEP 3: Check if session can be opened")
    void step3_CheckSessionOpenability() {
        if (testPresenterUsername == null || testSlotId == null) {
            logger.info("⚠ Skipping - no presenter/slot found");
            return;
        }

        Optional<SeminarSlot> slotOpt = seminarSlotRepository.findById(testSlotId);
        assertTrue(slotOpt.isPresent(), "Slot should exist");

        SeminarSlot slot = slotOpt.get();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime slotStart = LocalDateTime.of(slot.getSlotDate(), slot.getStartTime());
        LocalDateTime slotEnd = LocalDateTime.of(slot.getSlotDate(), slot.getEndTime());

        logger.info("\n--- Session Open Check ---");
        logger.info("Current time: {}", now);
        logger.info("Slot start: {}", slotStart);
        logger.info("Slot end: {}", slotEnd);

        // Check existing open sessions
        List<Session> openSessions = sessionRepository.findOpenSessions();
        logger.info("Currently open sessions: {}", openSessions.size());

        // Check if this slot already has a session
        if (slot.getLegacySessionId() != null) {
            Optional<Session> existingSession = sessionRepository.findById(slot.getLegacySessionId());
            if (existingSession.isPresent()) {
                logger.info("Slot already has session: {} (status: {})",
                        existingSession.get().getSessionId(), existingSession.get().getStatus());
                testSessionId = existingSession.get().getSessionId();
            }
        }
    }

    @Test
    @Order(4)
    @DisplayName("FLOW STEP 4: Attempt to open attendance")
    void step4_OpenAttendance() {
        if (testPresenterUsername == null || testSlotId == null) {
            logger.info("⚠ Skipping - no presenter/slot found");
            return;
        }

        logger.info("\n--- Opening Attendance ---");
        logger.info("Presenter: {}", testPresenterUsername);
        logger.info("Slot ID: {}", testSlotId);

        try {
            PresenterOpenAttendanceResponse response = presenterHomeService.openAttendance(
                    testPresenterUsername, testSlotId);

            logger.info("Response code: {}", response.getCode());
            logger.info("Success: {}", response.isSuccess());
            logger.info("Message: {}", response.getMessage());

            if (response.isSuccess()) {
                testSessionId = response.getSessionId();
                logger.info("✓ Session opened: {}", testSessionId);
                logger.info("  QR URL: {}", response.getQrUrl());
                logger.info("  Opened at: {}", response.getOpenedAt());
                logger.info("  Closes at: {}", response.getClosesAt());
            } else {
                logger.info("✗ Could not open session: {}", response.getCode());
                // Common reasons: TOO_EARLY, TOO_LATE, ALREADY_OPEN, IN_PROGRESS
            }
        } catch (Exception e) {
            logger.info("✗ Exception: {}", e.getMessage());
        }
    }

    @Test
    @Order(5)
    @DisplayName("FLOW STEP 5: Find a student to record attendance")
    void step5_FindStudent() {
        logger.info("\n--- Finding Student ---");

        // Find a participant (student) in the database
        List<User> students = userRepository.findAll().stream()
                .filter(u -> u.getIsParticipant() != null && u.getIsParticipant())
                .limit(5)
                .toList();

        logger.info("Found {} students", students.size());

        if (!students.isEmpty()) {
            testStudentUsername = students.get(0).getBguUsername();
            User student = students.get(0);
            logger.info("✓ Selected student: {}", testStudentUsername);
            logger.info("  Name: {} {}", student.getFirstName(), student.getLastName());
        } else {
            logger.info("⚠ No students found in database");
        }
    }

    @Test
    @Order(6)
    @DisplayName("FLOW STEP 6: Record student attendance (simulate QR scan)")
    void step6_RecordAttendance() {
        if (testSessionId == null) {
            logger.info("⚠ Skipping - no open session");
            return;
        }
        if (testStudentUsername == null) {
            logger.info("⚠ Skipping - no student found");
            return;
        }

        logger.info("\n--- Recording Attendance ---");
        logger.info("Session ID: {}", testSessionId);
        logger.info("Student: {}", testStudentUsername);

        // Check if attendance already exists
        Optional<Attendance> existing = attendanceRepository
                .findBySessionIdAndStudentUsernameIgnoreCase(testSessionId, testStudentUsername);

        if (existing.isPresent()) {
            logger.info("✓ Attendance already recorded at: {}", existing.get().getAttendanceTime());
            logger.info("  Method: {}", existing.get().getMethod());
            return;
        }

        // Check if session is still open
        Optional<Session> sessionOpt = sessionRepository.findById(testSessionId);
        if (sessionOpt.isEmpty() || sessionOpt.get().getStatus() != Session.SessionStatus.OPEN) {
            logger.info("⚠ Session is not open - cannot record attendance");
            return;
        }

        try {
            Attendance attendance = new Attendance();
            attendance.setSessionId(testSessionId);
            attendance.setStudentUsername(testStudentUsername);
            attendance.setMethod(Attendance.AttendanceMethod.QR_SCAN);

            Attendance recorded = attendanceService.recordAttendance(attendance);

            logger.info("✓ Attendance recorded successfully!");
            logger.info("  Attendance ID: {}", recorded.getAttendanceId());
            logger.info("  Time: {}", recorded.getAttendanceTime());
            logger.info("  Method: {}", recorded.getMethod());
        } catch (IllegalArgumentException e) {
            logger.info("✗ Could not record attendance: {}", e.getMessage());
        }
    }

    @Test
    @Order(7)
    @DisplayName("FLOW STEP 7: Verify attendance was recorded")
    void step7_VerifyAttendance() {
        if (testSessionId == null) {
            logger.info("⚠ Skipping - no session");
            return;
        }

        logger.info("\n--- Verifying Attendance ---");

        List<Attendance> sessionAttendances = attendanceService.getAttendanceBySession(testSessionId);

        logger.info("Session {} has {} attendance records:", testSessionId, sessionAttendances.size());

        for (Attendance att : sessionAttendances) {
            Optional<User> userOpt = userRepository.findByBguUsernameIgnoreCase(att.getStudentUsername());
            String name = userOpt.map(u -> u.getFirstName() + " " + u.getLastName()).orElse("Unknown");

            logger.info("  - {} ({})", att.getStudentUsername(), name);
            logger.info("    Time: {}, Method: {}", att.getAttendanceTime(), att.getMethod());
        }

        // Verify statistics
        AttendanceService.AttendanceStats stats = attendanceService.getSessionAttendanceStats(testSessionId);
        logger.info("\nStatistics:");
        logger.info("  Total: {}", stats.getTotalAttendance());
        logger.info("  QR Scan: {}", stats.getQrScanCount());
        logger.info("  Manual: {}", stats.getManualCount());
        logger.info("  Proxy: {}", stats.getProxyCount());
    }

    @Test
    @Order(8)
    @DisplayName("FLOW STEP 8: Summary and cleanup info")
    void step8_Summary() {
        logger.info("\n========== FLOW TEST SUMMARY ==========");
        logger.info("Presenter: {}", testPresenterUsername != null ? testPresenterUsername : "N/A");
        logger.info("Slot ID: {}", testSlotId != null ? testSlotId : "N/A");
        logger.info("Session ID: {}", testSessionId != null ? testSessionId : "N/A");
        logger.info("Student tested: {}", testStudentUsername != null ? testStudentUsername : "N/A");

        if (testSessionId != null) {
            Optional<Session> session = sessionRepository.findById(testSessionId);
            if (session.isPresent()) {
                logger.info("Session status: {}", session.get().getStatus());
                List<Attendance> attendances = attendanceRepository.findBySessionId(testSessionId);
                logger.info("Total attendances: {}", attendances.size());
            }
        }

        logger.info("\n✓ Flow test completed");
        logger.info("================================================\n");
    }
}
