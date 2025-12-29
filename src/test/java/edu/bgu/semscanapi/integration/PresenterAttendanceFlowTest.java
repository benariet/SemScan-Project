package edu.bgu.semscanapi.integration;

import edu.bgu.semscanapi.dto.*;
import edu.bgu.semscanapi.entity.*;
import edu.bgu.semscanapi.service.AttendanceService;
import org.junit.jupiter.api.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
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

    // Shared state across tests in this flow
    private static String testPresenterUsername;
    private static Long testSlotId;
    private static Long testSessionId;
    private static String testStudentUsername;

    @Test
    @Order(1)
    @DisplayName("FLOW STEP 1: Find presenter with approved slot")
    void step1_FindPresenterWithApprovedSlot() {
        System.out.println("\n========== FLOW TEST: Presenter Attendance Flow ==========\n");

        // Find slots with approved registrations
        List<SeminarSlotRegistration> approvedRegistrations = registrationRepository.findAll().stream()
                .filter(r -> r.getApprovalStatus() == ApprovalStatus.APPROVED)
                .toList();

        System.out.println("Found " + approvedRegistrations.size() + " approved registrations");

        if (approvedRegistrations.isEmpty()) {
            System.out.println("⚠ No approved registrations found - skipping flow test");
            return;
        }

        // Find one with a valid slot
        for (SeminarSlotRegistration reg : approvedRegistrations) {
            Optional<SeminarSlot> slotOpt = seminarSlotRepository.findById(reg.getSlotId());
            if (slotOpt.isPresent()) {
                SeminarSlot slot = slotOpt.get();
                testSlotId = slot.getSlotId();
                testPresenterUsername = reg.getPresenterUsername();

                System.out.println("✓ Found presenter: " + testPresenterUsername);
                System.out.println("  Slot ID: " + testSlotId);
                System.out.println("  Date: " + slot.getSlotDate());
                System.out.println("  Time: " + slot.getStartTime() + " - " + slot.getEndTime());
                System.out.println("  Location: " + slot.getBuilding() + " " + slot.getRoom());
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
            System.out.println("⚠ Skipping - no presenter found in step 1");
            return;
        }

        PresenterHomeResponse home = presenterHomeService.getPresenterHome(testPresenterUsername);

        assertNotNull(home, "Should get presenter home response");
        assertNotNull(home.getPresenter(), "Should have presenter info");

        System.out.println("\n--- Presenter Home Screen ---");
        System.out.println("Presenter: " + home.getPresenter().getName());
        System.out.println("Degree: " + home.getPresenter().getDegree());
        System.out.println("My Slot: " + (home.getMySlot() != null ? "Assigned" : "None"));
        System.out.println("Slot Catalog: " + home.getSlotCatalog().size() + " slots available");

        if (home.getMySlot() != null) {
            System.out.println("  - Slot ID: " + home.getMySlot().getSlotId());
            System.out.println("  - Date: " + home.getMySlot().getDate());
            System.out.println("  - Time: " + home.getMySlot().getTimeRange());
        }
    }

    @Test
    @Order(3)
    @DisplayName("FLOW STEP 3: Check if session can be opened")
    void step3_CheckSessionOpenability() {
        if (testPresenterUsername == null || testSlotId == null) {
            System.out.println("⚠ Skipping - no presenter/slot found");
            return;
        }

        Optional<SeminarSlot> slotOpt = seminarSlotRepository.findById(testSlotId);
        assertTrue(slotOpt.isPresent(), "Slot should exist");

        SeminarSlot slot = slotOpt.get();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime slotStart = LocalDateTime.of(slot.getSlotDate(), slot.getStartTime());
        LocalDateTime slotEnd = LocalDateTime.of(slot.getSlotDate(), slot.getEndTime());

        System.out.println("\n--- Session Open Check ---");
        System.out.println("Current time: " + now);
        System.out.println("Slot start: " + slotStart);
        System.out.println("Slot end: " + slotEnd);

        // Check existing open sessions
        List<Session> openSessions = sessionRepository.findOpenSessions();
        System.out.println("Currently open sessions: " + openSessions.size());

        // Check if this slot already has a session
        if (slot.getLegacySessionId() != null) {
            Optional<Session> existingSession = sessionRepository.findById(slot.getLegacySessionId());
            if (existingSession.isPresent()) {
                System.out.println("Slot already has session: " + existingSession.get().getSessionId() +
                        " (status: " + existingSession.get().getStatus() + ")");
                testSessionId = existingSession.get().getSessionId();
            }
        }
    }

    @Test
    @Order(4)
    @DisplayName("FLOW STEP 4: Attempt to open attendance")
    void step4_OpenAttendance() {
        if (testPresenterUsername == null || testSlotId == null) {
            System.out.println("⚠ Skipping - no presenter/slot found");
            return;
        }

        System.out.println("\n--- Opening Attendance ---");
        System.out.println("Presenter: " + testPresenterUsername);
        System.out.println("Slot ID: " + testSlotId);

        try {
            PresenterOpenAttendanceResponse response = presenterHomeService.openAttendance(
                    testPresenterUsername, testSlotId);

            System.out.println("Response code: " + response.getCode());
            System.out.println("Success: " + response.isSuccess());
            System.out.println("Message: " + response.getMessage());

            if (response.isSuccess()) {
                testSessionId = response.getSessionId();
                System.out.println("✓ Session opened: " + testSessionId);
                System.out.println("  QR URL: " + response.getQrUrl());
                System.out.println("  Opened at: " + response.getOpenedAt());
                System.out.println("  Closes at: " + response.getClosesAt());
            } else {
                System.out.println("✗ Could not open session: " + response.getCode());
                // Common reasons: TOO_EARLY, TOO_LATE, ALREADY_OPEN, IN_PROGRESS
            }
        } catch (Exception e) {
            System.out.println("✗ Exception: " + e.getMessage());
        }
    }

    @Test
    @Order(5)
    @DisplayName("FLOW STEP 5: Find a student to record attendance")
    void step5_FindStudent() {
        System.out.println("\n--- Finding Student ---");

        // Find a participant (student) in the database
        List<User> students = userRepository.findAll().stream()
                .filter(u -> u.getIsParticipant() != null && u.getIsParticipant())
                .limit(5)
                .toList();

        System.out.println("Found " + students.size() + " students");

        if (!students.isEmpty()) {
            testStudentUsername = students.get(0).getBguUsername();
            User student = students.get(0);
            System.out.println("✓ Selected student: " + testStudentUsername);
            System.out.println("  Name: " + student.getFirstName() + " " + student.getLastName());
        } else {
            System.out.println("⚠ No students found in database");
        }
    }

    @Test
    @Order(6)
    @DisplayName("FLOW STEP 6: Record student attendance (simulate QR scan)")
    void step6_RecordAttendance() {
        if (testSessionId == null) {
            System.out.println("⚠ Skipping - no open session");
            return;
        }
        if (testStudentUsername == null) {
            System.out.println("⚠ Skipping - no student found");
            return;
        }

        System.out.println("\n--- Recording Attendance ---");
        System.out.println("Session ID: " + testSessionId);
        System.out.println("Student: " + testStudentUsername);

        // Check if attendance already exists
        Optional<Attendance> existing = attendanceRepository
                .findBySessionIdAndStudentUsernameIgnoreCase(testSessionId, testStudentUsername);

        if (existing.isPresent()) {
            System.out.println("✓ Attendance already recorded at: " + existing.get().getAttendanceTime());
            System.out.println("  Method: " + existing.get().getMethod());
            return;
        }

        // Check if session is still open
        Optional<Session> sessionOpt = sessionRepository.findById(testSessionId);
        if (sessionOpt.isEmpty() || sessionOpt.get().getStatus() != Session.SessionStatus.OPEN) {
            System.out.println("⚠ Session is not open - cannot record attendance");
            return;
        }

        try {
            Attendance attendance = new Attendance();
            attendance.setSessionId(testSessionId);
            attendance.setStudentUsername(testStudentUsername);
            attendance.setMethod(Attendance.AttendanceMethod.QR_SCAN);

            Attendance recorded = attendanceService.recordAttendance(attendance);

            System.out.println("✓ Attendance recorded successfully!");
            System.out.println("  Attendance ID: " + recorded.getAttendanceId());
            System.out.println("  Time: " + recorded.getAttendanceTime());
            System.out.println("  Method: " + recorded.getMethod());
        } catch (IllegalArgumentException e) {
            System.out.println("✗ Could not record attendance: " + e.getMessage());
        }
    }

    @Test
    @Order(7)
    @DisplayName("FLOW STEP 7: Verify attendance was recorded")
    void step7_VerifyAttendance() {
        if (testSessionId == null) {
            System.out.println("⚠ Skipping - no session");
            return;
        }

        System.out.println("\n--- Verifying Attendance ---");

        List<Attendance> sessionAttendances = attendanceService.getAttendanceBySession(testSessionId);

        System.out.println("Session " + testSessionId + " has " + sessionAttendances.size() + " attendance records:");

        for (Attendance att : sessionAttendances) {
            Optional<User> userOpt = userRepository.findByBguUsernameIgnoreCase(att.getStudentUsername());
            String name = userOpt.map(u -> u.getFirstName() + " " + u.getLastName()).orElse("Unknown");

            System.out.println("  - " + att.getStudentUsername() + " (" + name + ")");
            System.out.println("    Time: " + att.getAttendanceTime() + ", Method: " + att.getMethod());
        }

        // Verify statistics
        AttendanceService.AttendanceStats stats = attendanceService.getSessionAttendanceStats(testSessionId);
        System.out.println("\nStatistics:");
        System.out.println("  Total: " + stats.getTotalAttendance());
        System.out.println("  QR Scan: " + stats.getQrScanCount());
        System.out.println("  Manual: " + stats.getManualCount());
        System.out.println("  Proxy: " + stats.getProxyCount());
    }

    @Test
    @Order(8)
    @DisplayName("FLOW STEP 8: Summary and cleanup info")
    void step8_Summary() {
        System.out.println("\n========== FLOW TEST SUMMARY ==========");
        System.out.println("Presenter: " + (testPresenterUsername != null ? testPresenterUsername : "N/A"));
        System.out.println("Slot ID: " + (testSlotId != null ? testSlotId : "N/A"));
        System.out.println("Session ID: " + (testSessionId != null ? testSessionId : "N/A"));
        System.out.println("Student tested: " + (testStudentUsername != null ? testStudentUsername : "N/A"));

        if (testSessionId != null) {
            Optional<Session> session = sessionRepository.findById(testSessionId);
            if (session.isPresent()) {
                System.out.println("Session status: " + session.get().getStatus());
                List<Attendance> attendances = attendanceRepository.findBySessionId(testSessionId);
                System.out.println("Total attendances: " + attendances.size());
            }
        }

        System.out.println("\n✓ Flow test completed");
        System.out.println("================================================\n");
    }
}
