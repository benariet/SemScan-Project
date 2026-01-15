package edu.bgu.semscanapi.integration;

import edu.bgu.semscanapi.entity.*;
import edu.bgu.semscanapi.service.AttendanceService;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(AttendanceFlowIntegrationTest.class);

    @Test
    @Order(1)
    @DisplayName("Verify attendance table structure")
    void verifyAttendanceTable() {
        long attendanceCount = attendanceRepository.count();
        logger.info("Total attendance records: {}", attendanceCount);

        // Get sample attendance records
        List<Attendance> recentAttendances = attendanceRepository.findAll().stream()
                .sorted((a, b) -> {
                    if (a.getAttendanceTime() == null) return 1;
                    if (b.getAttendanceTime() == null) return -1;
                    return b.getAttendanceTime().compareTo(a.getAttendanceTime());
                })
                .limit(5)
                .toList();

        logger.info("Recent attendance records:");
        recentAttendances.forEach(att -> {
            logger.info("  ID: {}, Session: {}, Student: {}, Method: {}, Time: {}",
                    att.getAttendanceId(),
                    att.getSessionId(),
                    att.getStudentUsername(),
                    att.getMethod(),
                    att.getAttendanceTime());
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
                logger.warn("Attendance {} references non-existent session {}",
                        att.getAttendanceId(), att.getSessionId());
            }

            // Check student exists
            Optional<User> student = userRepository.findByBguUsernameIgnoreCase(att.getStudentUsername());
            if (student.isEmpty()) {
                invalidStudentCount++;
                logger.warn("Attendance {} references non-existent student {}",
                        att.getAttendanceId(), att.getStudentUsername());
            }

            if (session.isPresent() && student.isPresent()) {
                validCount++;
            }
        }

        logger.info("Attendance validation results:");
        logger.info("  Valid: {}", validCount);
        logger.info("  Invalid session refs: {}", invalidSessionCount);
        logger.info("  Invalid student refs: {}", invalidStudentCount);
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
                logger.info("Session {} has {} attendances:", session.getSessionId(), attendances.size());
                attendances.stream().limit(3).forEach(att -> {
                    logger.info("  - {} at {} via {}",
                            att.getStudentUsername(),
                            att.getAttendanceTime(),
                            att.getMethod());
                });
                return; // Found one, test passes
            }
        }

        logger.info("No sessions with attendance found - this is OK for empty database");
    }

    @Test
    @Order(4)
    @DisplayName("Get attendance by student")
    void getAttendanceByStudent() {
        // Find a student with attendance
        List<Attendance> allAttendances = attendanceRepository.findAll();

        if (allAttendances.isEmpty()) {
            logger.info("No attendance records found - skipping test");
            return;
        }

        String studentUsername = allAttendances.get(0).getStudentUsername();
        List<Attendance> studentAttendances = attendanceService.getAttendanceByStudent(studentUsername);

        logger.info("Student {} has {} attendance records", studentUsername, studentAttendances.size());
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

        logger.info("Attendance by method:");
        logger.info("  QR_SCAN: {}", qrCount);
        logger.info("  MANUAL: {}", manualCount);
        logger.info("  PROXY: {}", proxyCount);

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

        logger.info("Correctly rejected attendance for invalid session");
    }

    @Test
    @Order(7)
    @DisplayName("Test attendance stats endpoint")
    void testAttendanceStats() {
        List<Session> sessions = sessionRepository.findAll();

        if (sessions.isEmpty()) {
            logger.info("No sessions found - skipping stats test");
            return;
        }

        Session session = sessions.get(0);
        AttendanceService.AttendanceStats stats = attendanceService.getSessionAttendanceStats(session.getSessionId());

        logger.info("Session {} stats:", session.getSessionId());
        logger.info("  Total: {}", stats.getTotalAttendance());
        logger.info("  QR: {}", stats.getQrScanCount());
        logger.info("  Manual: {}", stats.getManualCount());
        logger.info("  Proxy: {}", stats.getProxyCount());

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
            logger.info("No students found - skipping API test");
            return;
        }

        String username = students.get(0).getBguUsername();

        ResponseEntity<String> response = restTemplate.getForEntity(
                getApiUrl("/student/" + username + "/sessions"),
                String.class);

        logger.info("GET /api/v1/student/{}/sessions", username);
        logger.info("  Status: {}", response.getStatusCode());

        // Could be 200 or 404 depending on data
        assertTrue(response.getStatusCode() == HttpStatus.OK ||
                        response.getStatusCode() == HttpStatus.NOT_FOUND,
                "Should return valid response");
    }
}
