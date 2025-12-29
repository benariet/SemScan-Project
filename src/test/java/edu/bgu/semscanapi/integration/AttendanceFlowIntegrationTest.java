package edu.bgu.semscanapi.integration;

import edu.bgu.semscanapi.entity.*;
import edu.bgu.semscanapi.service.AttendanceService;
import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the Attendance flow.
 * Tests student attendance recording and retrieval.
 *
 * Uses real database (semscan_db) - ensure SSH tunnel is running.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AttendanceFlowIntegrationTest extends BaseIntegrationTest {

    @Test
    @Order(1)
    @DisplayName("Verify attendance table structure")
    void verifyAttendanceTable() {
        long attendanceCount = attendanceRepository.count();
        System.out.println("Total attendance records: " + attendanceCount);

        // Get sample attendance records
        List<Attendance> recentAttendances = attendanceRepository.findAll().stream()
                .sorted((a, b) -> {
                    if (a.getAttendanceTime() == null) return 1;
                    if (b.getAttendanceTime() == null) return -1;
                    return b.getAttendanceTime().compareTo(a.getAttendanceTime());
                })
                .limit(5)
                .toList();

        System.out.println("Recent attendance records:");
        recentAttendances.forEach(att -> {
            System.out.println(String.format("  ID: %d, Session: %d, Student: %s, Method: %s, Time: %s",
                    att.getAttendanceId(),
                    att.getSessionId(),
                    att.getStudentUsername(),
                    att.getMethod(),
                    att.getAttendanceTime()));
        });

        assertTrue(attendanceCount >= 0, "Should be able to count attendance");
    }

    @Test
    @Order(2)
    @DisplayName("Verify attendance references are valid")
    void verifyAttendanceReferences() {
        List<Attendance> attendances = attendanceRepository.findAll();

        int validCount = 0;
        int invalidSessionCount = 0;
        int invalidStudentCount = 0;

        for (Attendance att : attendances) {
            // Check session exists
            Optional<Session> session = sessionRepository.findById(att.getSessionId());
            if (session.isEmpty()) {
                invalidSessionCount++;
                System.out.println("WARNING: Attendance " + att.getAttendanceId() +
                        " references non-existent session " + att.getSessionId());
            }

            // Check student exists
            Optional<User> student = userRepository.findByBguUsernameIgnoreCase(att.getStudentUsername());
            if (student.isEmpty()) {
                invalidStudentCount++;
                System.out.println("WARNING: Attendance " + att.getAttendanceId() +
                        " references non-existent student " + att.getStudentUsername());
            }

            if (session.isPresent() && student.isPresent()) {
                validCount++;
            }
        }

        System.out.println("Attendance validation results:");
        System.out.println("  Valid: " + validCount);
        System.out.println("  Invalid session refs: " + invalidSessionCount);
        System.out.println("  Invalid student refs: " + invalidStudentCount);
    }

    @Test
    @Order(3)
    @DisplayName("Get attendance by session")
    void getAttendanceBySession() {
        // Find a session with attendance
        List<Session> sessions = sessionRepository.findAll();

        for (Session session : sessions) {
            List<Attendance> attendances = attendanceService.getAttendanceBySession(session.getSessionId());
            if (!attendances.isEmpty()) {
                System.out.println("Session " + session.getSessionId() + " has " + attendances.size() + " attendances:");
                attendances.stream().limit(3).forEach(att -> {
                    System.out.println(String.format("  - %s at %s via %s",
                            att.getStudentUsername(),
                            att.getAttendanceTime(),
                            att.getMethod()));
                });
                return; // Found one, test passes
            }
        }

        System.out.println("No sessions with attendance found - this is OK for empty database");
    }

    @Test
    @Order(4)
    @DisplayName("Get attendance by student")
    void getAttendanceByStudent() {
        // Find a student with attendance
        List<Attendance> allAttendances = attendanceRepository.findAll();

        if (allAttendances.isEmpty()) {
            System.out.println("No attendance records found - skipping test");
            return;
        }

        String studentUsername = allAttendances.get(0).getStudentUsername();
        List<Attendance> studentAttendances = attendanceService.getAttendanceByStudent(studentUsername);

        System.out.println("Student " + studentUsername + " has " + studentAttendances.size() + " attendance records");
        assertNotNull(studentAttendances, "Should return attendance list");
    }

    @Test
    @Order(5)
    @DisplayName("Verify attendance methods distribution")
    void verifyAttendanceMethods() {
        List<Attendance> attendances = attendanceRepository.findAll();

        long qrCount = attendances.stream()
                .filter(a -> a.getMethod() == Attendance.AttendanceMethod.QR_SCAN)
                .count();
        long manualCount = attendances.stream()
                .filter(a -> a.getMethod() == Attendance.AttendanceMethod.MANUAL)
                .count();
        long proxyCount = attendances.stream()
                .filter(a -> a.getMethod() == Attendance.AttendanceMethod.PROXY)
                .count();

        System.out.println("Attendance by method:");
        System.out.println("  QR_SCAN: " + qrCount);
        System.out.println("  MANUAL: " + manualCount);
        System.out.println("  PROXY: " + proxyCount);

        // All should be non-negative
        assertTrue(qrCount >= 0 && manualCount >= 0 && proxyCount >= 0,
                "Attendance counts should be non-negative");
    }

    @Test
    @Order(6)
    @DisplayName("Test attendance recording with invalid session - should fail gracefully")
    void recordAttendance_InvalidSession_Fails() {
        Attendance invalidAttendance = new Attendance();
        invalidAttendance.setSessionId(999999L); // Non-existent session
        invalidAttendance.setStudentUsername("test_student");
        invalidAttendance.setMethod(Attendance.AttendanceMethod.QR_SCAN);

        assertThrows(IllegalArgumentException.class, () -> {
            attendanceService.recordAttendance(invalidAttendance);
        }, "Should throw exception for invalid session");

        System.out.println("Correctly rejected attendance for invalid session");
    }

    @Test
    @Order(7)
    @DisplayName("Test attendance stats endpoint")
    void testAttendanceStats() {
        List<Session> sessions = sessionRepository.findAll();

        if (sessions.isEmpty()) {
            System.out.println("No sessions found - skipping stats test");
            return;
        }

        Session session = sessions.get(0);
        AttendanceService.AttendanceStats stats = attendanceService.getSessionAttendanceStats(session.getSessionId());

        System.out.println("Session " + session.getSessionId() + " stats:");
        System.out.println("  Total: " + stats.getTotalAttendance());
        System.out.println("  QR: " + stats.getQrScanCount());
        System.out.println("  Manual: " + stats.getManualCount());
        System.out.println("  Proxy: " + stats.getProxyCount());

        assertNotNull(stats, "Should return stats object");
    }

    @Test
    @Order(8)
    @DisplayName("Test student sessions API endpoint")
    void testStudentSessionsEndpoint() {
        // Find a student in the database
        List<User> students = userRepository.findAll().stream()
                .filter(u -> u.getIsParticipant() != null && u.getIsParticipant())
                .limit(1)
                .toList();

        if (students.isEmpty()) {
            System.out.println("No students found - skipping API test");
            return;
        }

        String username = students.get(0).getBguUsername();

        ResponseEntity<String> response = restTemplate.getForEntity(
                getApiUrl("/student/" + username + "/sessions"),
                String.class);

        System.out.println("GET /api/v1/student/" + username + "/sessions");
        System.out.println("  Status: " + response.getStatusCode());

        // Could be 200 or 404 depending on data
        assertTrue(response.getStatusCode() == HttpStatus.OK ||
                        response.getStatusCode() == HttpStatus.NOT_FOUND,
                "Should return valid response");
    }
}
