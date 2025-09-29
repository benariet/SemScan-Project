package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.util.SessionLoggerUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Test controller to demonstrate session-specific logging functionality.
 * This controller is for testing purposes only and should be removed in production.
 */
@RestController
@RequestMapping("/api/v1/test/session-logging")
public class SessionLoggingTestController {

    /**
     * Test session-specific logging by creating a mock session
     */
    @PostMapping("/create-session")
    public ResponseEntity<Map<String, Object>> testSessionCreation(@RequestParam String sessionId) {
        try {
            // Simulate session creation
            SessionLoggerUtil.logSessionCreated(sessionId, "seminar-001", "presenter-001");
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Session logging test completed",
                "sessionId", sessionId,
                "logFile", "logs/sessions/session-" + sessionId + "-" + java.time.LocalDate.now() + ".log",
                "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to test session logging: " + e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }

    /**
     * Test session status change logging
     */
    @PostMapping("/change-status")
    public ResponseEntity<Map<String, Object>> testStatusChange(
            @RequestParam String sessionId,
            @RequestParam String newStatus) {
        try {
            SessionLoggerUtil.logSessionStatusChange(sessionId, "OPEN", newStatus);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Session status change logged",
                "sessionId", sessionId,
                "newStatus", newStatus,
                "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to log status change: " + e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }

    /**
     * Test attendance logging
     */
    @PostMapping("/log-attendance")
    public ResponseEntity<Map<String, Object>> testAttendanceLogging(
            @RequestParam String sessionId,
            @RequestParam String studentId) {
        try {
            SessionLoggerUtil.logAttendance(sessionId, studentId, "QR_SCAN", 
                LocalDateTime.now().toString());
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Attendance logged to session file",
                "sessionId", sessionId,
                "studentId", studentId,
                "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to log attendance: " + e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }

    /**
     * Test session closure logging
     */
    @PostMapping("/close-session")
    public ResponseEntity<Map<String, Object>> testSessionClosure(
            @RequestParam String sessionId,
            @RequestParam(defaultValue = "90") int durationMinutes,
            @RequestParam(defaultValue = "15") int attendanceCount) {
        try {
            SessionLoggerUtil.logSessionClosed(sessionId, durationMinutes, attendanceCount);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Session closure logged",
                "sessionId", sessionId,
                "durationMinutes", durationMinutes,
                "attendanceCount", attendanceCount,
                "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to log session closure: " + e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }

    /**
     * Test session statistics logging
     */
    @PostMapping("/log-statistics")
    public ResponseEntity<Map<String, Object>> testStatisticsLogging(
            @RequestParam String sessionId,
            @RequestParam(defaultValue = "25") int totalStudents,
            @RequestParam(defaultValue = "20") int attendedStudents) {
        try {
            double attendanceRate = (double) attendedStudents / totalStudents * 100;
            SessionLoggerUtil.logSessionStatistics(sessionId, totalStudents, attendedStudents, attendanceRate);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Session statistics logged",
                "sessionId", sessionId,
                "totalStudents", totalStudents,
                "attendedStudents", attendedStudents,
                "attendanceRate", String.format("%.2f%%", attendanceRate),
                "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to log statistics: " + e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }
}
