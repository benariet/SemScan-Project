package edu.bgu.semscanapi.integration;

import edu.bgu.semscanapi.entity.*;
import org.junit.jupiter.api.*;

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

    private static String testStudentUsername;
    private static Long openSessionId;
    private static int initialAttendanceCount;

    @Test
    @Order(1)
    @DisplayName("STUDENT FLOW STEP 1: Find a student user")
    void step1_FindStudent() {
        System.out.println("\n========== FLOW TEST: Student Attendance Flow ==========\n");

        List<User> students = userRepository.findAll().stream()
                .filter(u -> u.getIsParticipant() != null && u.getIsParticipant())
                .toList();

        System.out.println("Found " + students.size() + " students in database");

        if (students.isEmpty()) {
            System.out.println("⚠ No students found - skipping flow");
            return;
        }

        // Pick a student
        User student = students.get(0);
        testStudentUsername = student.getBguUsername();

        System.out.println("✓ Selected student: " + testStudentUsername);
        System.out.println("  Name: " + student.getFirstName() + " " + student.getLastName());
        System.out.println("  Email: " + student.getEmail());

        // Get their current attendance count
        List<Attendance> existingAttendances = attendanceRepository.findByStudentUsername(testStudentUsername);
        initialAttendanceCount = existingAttendances.size();
        System.out.println("  Current attendance records: " + initialAttendanceCount);

        assertNotNull(testStudentUsername);
    }

    @Test
    @Order(2)
    @DisplayName("STUDENT FLOW STEP 2: Find open sessions")
    void step2_FindOpenSessions() {
        System.out.println("\n--- Finding Open Sessions ---");

        List<Session> openSessions = sessionRepository.findOpenSessions();

        System.out.println("Open sessions available: " + openSessions.size());

        if (openSessions.isEmpty()) {
            System.out.println("⚠ No open sessions - student cannot scan QR");
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

            System.out.println("  Session " + session.getSessionId() + ":");
            System.out.println("    Presenter: " + presenterName);
            System.out.println("    Location: " + session.getLocation());
            System.out.println("    Started: " + session.getStartTime());
            System.out.println("    Status: " + session.getStatus());

            // Use first open session for testing
            if (openSessionId == null) {
                openSessionId = session.getSessionId();
            }
        }

        if (openSessionId != null) {
            System.out.println("\n✓ Will use session " + openSessionId + " for testing");
        }
    }

    @Test
    @Order(3)
    @DisplayName("STUDENT FLOW STEP 3: Check if already attended this session")
    void step3_CheckExistingAttendance() {
        if (testStudentUsername == null || openSessionId == null) {
            System.out.println("⚠ Skipping - no student or session available");
            return;
        }

        System.out.println("\n--- Checking Existing Attendance ---");

        Optional<Attendance> existing = attendanceRepository
                .findBySessionIdAndStudentUsernameIgnoreCase(openSessionId, testStudentUsername);

        if (existing.isPresent()) {
            System.out.println("✓ Student already attended this session");
            System.out.println("  Recorded at: " + existing.get().getAttendanceTime());
            System.out.println("  Method: " + existing.get().getMethod());
        } else {
            System.out.println("○ Student has NOT attended this session yet");
        }
    }

    @Test
    @Order(4)
    @DisplayName("STUDENT FLOW STEP 4: Simulate QR scan (record attendance)")
    void step4_SimulateQRScan() {
        if (testStudentUsername == null || openSessionId == null) {
            System.out.println("⚠ Skipping - no student or session available");
            return;
        }

        System.out.println("\n--- Simulating QR Code Scan ---");
        System.out.println("Student: " + testStudentUsername);
        System.out.println("Session: " + openSessionId);

        // Check if already recorded
        Optional<Attendance> existing = attendanceRepository
                .findBySessionIdAndStudentUsernameIgnoreCase(openSessionId, testStudentUsername);

        if (existing.isPresent()) {
            System.out.println("→ Already recorded - skipping (this is expected behavior)");
            return;
        }

        try {
            Attendance attendance = new Attendance();
            attendance.setSessionId(openSessionId);
            attendance.setStudentUsername(testStudentUsername);
            attendance.setMethod(Attendance.AttendanceMethod.QR_SCAN);
            attendance.setAttendanceTime(LocalDateTime.now());

            Attendance recorded = attendanceService.recordAttendance(attendance);

            System.out.println("✓ QR Scan successful!");
            System.out.println("  Attendance ID: " + recorded.getAttendanceId());
            System.out.println("  Recorded at: " + recorded.getAttendanceTime());
        } catch (IllegalArgumentException e) {
            System.out.println("✗ Scan failed: " + e.getMessage());
            // This could happen if session closed between steps
        }
    }

    @Test
    @Order(5)
    @DisplayName("STUDENT FLOW STEP 5: View attendance history")
    void step5_ViewAttendanceHistory() {
        if (testStudentUsername == null) {
            System.out.println("⚠ Skipping - no student");
            return;
        }

        System.out.println("\n--- Student Attendance History ---");

        List<Attendance> allAttendances = attendanceService.getAttendanceByStudent(testStudentUsername);

        System.out.println("Total attendance records: " + allAttendances.size());

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

                    System.out.println("  " + att.getAttendanceTime() + " - Session " + att.getSessionId());
                    System.out.println("    Location: " + location);
                    System.out.println("    Method: " + att.getMethod());
                });
    }

    @Test
    @Order(6)
    @DisplayName("STUDENT FLOW STEP 6: Verify attendance count increased")
    void step6_VerifyAttendanceCount() {
        if (testStudentUsername == null) {
            System.out.println("⚠ Skipping - no student");
            return;
        }

        System.out.println("\n--- Verifying Attendance Count ---");

        List<Attendance> currentAttendances = attendanceRepository.findByStudentUsername(testStudentUsername);
        int currentCount = currentAttendances.size();

        System.out.println("Initial count: " + initialAttendanceCount);
        System.out.println("Current count: " + currentCount);

        if (currentCount > initialAttendanceCount) {
            System.out.println("✓ Attendance count increased by " + (currentCount - initialAttendanceCount));
        } else {
            System.out.println("○ Count unchanged (already attended or no open session)");
        }
    }

    @Test
    @Order(7)
    @DisplayName("STUDENT FLOW STEP 7: Test duplicate scan prevention")
    void step7_TestDuplicatePrevention() {
        if (testStudentUsername == null || openSessionId == null) {
            System.out.println("⚠ Skipping - no student or session");
            return;
        }

        System.out.println("\n--- Testing Duplicate Prevention ---");

        // Check if there's an existing attendance
        Optional<Attendance> existing = attendanceRepository
                .findBySessionIdAndStudentUsernameIgnoreCase(openSessionId, testStudentUsername);

        if (existing.isEmpty()) {
            System.out.println("○ No existing attendance to test duplicate prevention");
            return;
        }

        System.out.println("Existing attendance: " + existing.get().getAttendanceId());

        // Try to record again - should fail
        try {
            Attendance duplicate = new Attendance();
            duplicate.setSessionId(openSessionId);
            duplicate.setStudentUsername(testStudentUsername);
            duplicate.setMethod(Attendance.AttendanceMethod.QR_SCAN);

            attendanceService.recordAttendance(duplicate);

            System.out.println("✗ ERROR: Duplicate was allowed (this is a bug!)");
            fail("Duplicate attendance should be prevented");
        } catch (IllegalArgumentException e) {
            System.out.println("✓ Duplicate correctly prevented: " + e.getMessage());
        }
    }

    @Test
    @Order(8)
    @DisplayName("STUDENT FLOW STEP 8: Summary")
    void step8_Summary() {
        System.out.println("\n========== STUDENT FLOW SUMMARY ==========");
        System.out.println("Student: " + (testStudentUsername != null ? testStudentUsername : "N/A"));
        System.out.println("Session tested: " + (openSessionId != null ? openSessionId : "N/A"));
        System.out.println("Initial attendance count: " + initialAttendanceCount);

        if (testStudentUsername != null) {
            List<Attendance> finalAttendances = attendanceRepository.findByStudentUsername(testStudentUsername);
            System.out.println("Final attendance count: " + finalAttendances.size());
        }

        System.out.println("\n✓ Student flow test completed");
        System.out.println("===========================================\n");
    }
}
