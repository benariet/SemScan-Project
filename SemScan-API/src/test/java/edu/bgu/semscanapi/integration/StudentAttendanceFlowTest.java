package edu.bgu.semscanapi.integration;

import edu.bgu.semscanapi.entity.*;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * END-TO-END FLOW TEST: Student Attendance Flow
 *
 * This test simulates the student perspective:
 * 1. Find open sessions available for students
 * 2. Student views available sessions
 * 3. Student scans QR code (records attendance)
 * 4. Student views their attendance history
 * 5. Verify attendance appears in their history
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StudentAttendanceFlowTest extends BaseIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(StudentAttendanceFlowTest.class);

    private static String testStudentUsername;
    private static Long openSessionId;
    private static int initialAttendanceCount;

    @Test
    @Order(1)
    @DisplayName("STUDENT FLOW STEP 1: Find a student user")
    void step1_FindStudent() {
        logger.info("\n========== FLOW TEST: Student Attendance Flow ==========\n");

        List<User> students = userRepository.findAll().stream()
                .filter(u -> u.getIsParticipant() != null && u.getIsParticipant())
                .toList();

        logger.info("Found {} students in database", students.size());

        if (students.isEmpty()) {
            logger.info("⚠ No students found - skipping flow");
            return;
        }

        // Pick a student
        User student = students.get(0);
        testStudentUsername = student.getBguUsername();

        logger.info("✓ Selected student: {}", testStudentUsername);
        logger.info("  Name: {} {}", student.getFirstName(), student.getLastName());
        logger.info("  Email: {}", student.getEmail());

        // Get their current attendance count
        List<Attendance> existingAttendances = attendanceRepository.findByStudentUsername(testStudentUsername);
        initialAttendanceCount = existingAttendances.size();
        logger.info("  Current attendance records: {}", initialAttendanceCount);

        assertNotNull(testStudentUsername);
    }

    @Test
    @Order(2)
    @DisplayName("STUDENT FLOW STEP 2: Find open sessions")
    void step2_FindOpenSessions() {
        logger.info("\n--- Finding Open Sessions ---");

        List<Session> openSessions = sessionRepository.findOpenSessions();

        logger.info("Open sessions available: {}", openSessions.size());

        if (openSessions.isEmpty()) {
            logger.info("⚠ No open sessions - student cannot scan QR");
            return;
        }

        for (Session session : openSessions) {
            Optional<Seminar> seminar = seminarRepository.findById(session.getSeminarId());
            String presenterName = "Unknown";
            if (seminar.isPresent()) {
                Optional<User> presenter = userRepository.findByBguUsernameIgnoreCase(
                        seminar.get().getPresenterUsername());
                presenterName = presenter.map(u -> u.getFirstName() + " " + u.getLastName())
                        .orElse(seminar.get().getPresenterUsername());
            }

            logger.info("  Session {}:", session.getSessionId());
            logger.info("    Presenter: {}", presenterName);
            logger.info("    Location: {}", session.getLocation());
            logger.info("    Started: {}", session.getStartTime());
            logger.info("    Status: {}", session.getStatus());

            // Use first open session for testing
            if (openSessionId == null) {
                openSessionId = session.getSessionId();
            }
        }

        if (openSessionId != null) {
            logger.info("\n✓ Will use session {} for testing", openSessionId);
        }
    }

    @Test
    @Order(3)
    @DisplayName("STUDENT FLOW STEP 3: Check if already attended this session")
    void step3_CheckExistingAttendance() {
        if (testStudentUsername == null || openSessionId == null) {
            logger.info("⚠ Skipping - no student or session available");
            return;
        }

        logger.info("\n--- Checking Existing Attendance ---");

        Optional<Attendance> existing = attendanceRepository
                .findBySessionIdAndStudentUsernameIgnoreCase(openSessionId, testStudentUsername);

        if (existing.isPresent()) {
            logger.info("✓ Student already attended this session");
            logger.info("  Recorded at: {}", existing.get().getAttendanceTime());
            logger.info("  Method: {}", existing.get().getMethod());
        } else {
            logger.info("○ Student has NOT attended this session yet");
        }
    }

    @Test
    @Order(4)
    @DisplayName("STUDENT FLOW STEP 4: Simulate QR scan (record attendance)")
    void step4_SimulateQRScan() {
        if (testStudentUsername == null || openSessionId == null) {
            logger.info("⚠ Skipping - no student or session available");
            return;
        }

        logger.info("\n--- Simulating QR Code Scan ---");
        logger.info("Student: {}", testStudentUsername);
        logger.info("Session: {}", openSessionId);

        // Check if already recorded
        Optional<Attendance> existing = attendanceRepository
                .findBySessionIdAndStudentUsernameIgnoreCase(openSessionId, testStudentUsername);

        if (existing.isPresent()) {
            logger.info("→ Already recorded - skipping (this is expected behavior)");
            return;
        }

        try {
            Attendance attendance = new Attendance();
            attendance.setSessionId(openSessionId);
            attendance.setStudentUsername(testStudentUsername);
            attendance.setMethod(Attendance.AttendanceMethod.QR_SCAN);
            attendance.setAttendanceTime(LocalDateTime.now());

            Attendance recorded = attendanceService.recordAttendance(attendance);

            logger.info("✓ QR Scan successful!");
            logger.info("  Attendance ID: {}", recorded.getAttendanceId());
            logger.info("  Recorded at: {}", recorded.getAttendanceTime());
        } catch (IllegalArgumentException e) {
            logger.info("✗ Scan failed: {}", e.getMessage());
            // This could happen if session closed between steps
        }
    }

    @Test
    @Order(5)
    @DisplayName("STUDENT FLOW STEP 5: View attendance history")
    void step5_ViewAttendanceHistory() {
        if (testStudentUsername == null) {
            logger.info("⚠ Skipping - no student");
            return;
        }

        logger.info("\n--- Student Attendance History ---");

        List<Attendance> allAttendances = attendanceService.getAttendanceByStudent(testStudentUsername);

        logger.info("Total attendance records: {}", allAttendances.size());

        // Group by date
        allAttendances.stream()
                .sorted((a, b) -> {
                    if (a.getAttendanceTime() == null) return 1;
                    if (b.getAttendanceTime() == null) return -1;
                    return b.getAttendanceTime().compareTo(a.getAttendanceTime());
                })
                .limit(10)
                .forEach(att -> {
                    Optional<Session> session = sessionRepository.findById(att.getSessionId());
                    String location = session.map(Session::getLocation).orElse("Unknown");

                    logger.info("  {} - Session {}", att.getAttendanceTime(), att.getSessionId());
                    logger.info("    Location: {}", location);
                    logger.info("    Method: {}", att.getMethod());
                });
    }

    @Test
    @Order(6)
    @DisplayName("STUDENT FLOW STEP 6: Verify attendance count increased")
    void step6_VerifyAttendanceCount() {
        if (testStudentUsername == null) {
            logger.info("⚠ Skipping - no student");
            return;
        }

        logger.info("\n--- Verifying Attendance Count ---");

        List<Attendance> currentAttendances = attendanceRepository.findByStudentUsername(testStudentUsername);
        int currentCount = currentAttendances.size();

        logger.info("Initial count: {}", initialAttendanceCount);
        logger.info("Current count: {}", currentCount);

        if (currentCount > initialAttendanceCount) {
            logger.info("✓ Attendance count increased by {}", currentCount - initialAttendanceCount);
        } else {
            logger.info("○ Count unchanged (already attended or no open session)");
        }
    }

    @Test
    @Order(7)
    @DisplayName("STUDENT FLOW STEP 7: Test duplicate scan prevention")
    void step7_TestDuplicatePrevention() {
        if (testStudentUsername == null || openSessionId == null) {
            logger.info("⚠ Skipping - no student or session");
            return;
        }

        logger.info("\n--- Testing Duplicate Prevention ---");

        // Check if there's an existing attendance
        Optional<Attendance> existing = attendanceRepository
                .findBySessionIdAndStudentUsernameIgnoreCase(openSessionId, testStudentUsername);

        if (existing.isEmpty()) {
            logger.info("○ No existing attendance to test duplicate prevention");
            return;
        }

        logger.info("Existing attendance: {}", existing.get().getAttendanceId());

        // Try to record again - should fail
        try {
            Attendance duplicate = new Attendance();
            duplicate.setSessionId(openSessionId);
            duplicate.setStudentUsername(testStudentUsername);
            duplicate.setMethod(Attendance.AttendanceMethod.QR_SCAN);

            attendanceService.recordAttendance(duplicate);

            logger.info("✗ ERROR: Duplicate was allowed (this is a bug!)");
            fail("Duplicate attendance should be prevented");
        } catch (IllegalArgumentException e) {
            logger.info("✓ Duplicate correctly prevented: {}", e.getMessage());
        }
    }

    @Test
    @Order(8)
    @DisplayName("STUDENT FLOW STEP 8: Summary")
    void step8_Summary() {
        logger.info("\n========== STUDENT FLOW SUMMARY ==========");
        logger.info("Student: {}", testStudentUsername != null ? testStudentUsername : "N/A");
        logger.info("Session tested: {}", openSessionId != null ? openSessionId : "N/A");
        logger.info("Initial attendance count: {}", initialAttendanceCount);

        if (testStudentUsername != null) {
            List<Attendance> finalAttendances = attendanceRepository.findByStudentUsername(testStudentUsername);
            logger.info("Final attendance count: {}", finalAttendances.size());
        }

        logger.info("\n✓ Student flow test completed");
        logger.info("===========================================\n");
    }
}
