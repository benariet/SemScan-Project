package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.entity.Attendance;
import edu.bgu.semscanapi.service.AttendanceService;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * REST Controller for Attendance operations
 * Provides comprehensive logging for all API endpoints
 */
@RestController
@RequestMapping("/api/v1/attendance")
public class AttendanceController {
    
    private static final Logger logger = LoggerUtil.getLogger(AttendanceController.class);
    
    @Autowired
    private AttendanceService attendanceService;
    
    /**
     * Record attendance for a student in a session
     */
    @PostMapping
    public ResponseEntity<Attendance> recordAttendance(@RequestBody Attendance attendance) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Recording attendance for student: {} in session: {}", 
            attendance.getStudentId(), attendance.getSessionId());
        LoggerUtil.logApiRequest(logger, "POST", "/api/v1/attendance", attendance.toString());
        
        try {
            Attendance recordedAttendance = attendanceService.recordAttendance(attendance);
            logger.info("Attendance recorded successfully - ID: {}, Student: {}, Session: {}", 
                recordedAttendance.getAttendanceId(), recordedAttendance.getStudentId(), 
                recordedAttendance.getSessionId());
            LoggerUtil.logApiResponse(logger, "POST", "/api/v1/attendance", 201, recordedAttendance.toString());
            
            return ResponseEntity.status(HttpStatus.CREATED).body(recordedAttendance);
            
        } catch (IllegalArgumentException e) {
            logger.error("Invalid attendance data: {}", e.getMessage());
            LoggerUtil.logApiResponse(logger, "POST", "/api/v1/attendance", 400, "Bad Request: " + e.getMessage());
            return ResponseEntity.badRequest().build();
            
        } catch (Exception e) {
            logger.error("Failed to record attendance", e);
            LoggerUtil.logError(logger, "Failed to record attendance", e);
            LoggerUtil.logApiResponse(logger, "POST", "/api/v1/attendance", 500, "Internal Server Error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Get attendance by ID
     */
    @GetMapping("/{attendanceId}")
    public ResponseEntity<Attendance> getAttendanceById(@PathVariable String attendanceId) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Retrieving attendance by ID: {}", attendanceId);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/attendance/" + attendanceId, null);
        
        try {
            Optional<Attendance> attendance = attendanceService.getAttendanceById(attendanceId);
            
            if (attendance.isPresent()) {
                logger.info("Attendance found - ID: {}, Student: {}, Session: {}", 
                    attendanceId, attendance.get().getStudentId(), attendance.get().getSessionId());
                LoggerUtil.logApiResponse(logger, "GET", "/api/v1/attendance/" + attendanceId, 200, attendance.get().toString());
                return ResponseEntity.ok(attendance.get());
            } else {
                logger.warn("Attendance not found: {}", attendanceId);
                LoggerUtil.logApiResponse(logger, "GET", "/api/v1/attendance/" + attendanceId, 404, "Not Found");
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            logger.error("Failed to retrieve attendance: {}", attendanceId, e);
            LoggerUtil.logError(logger, "Failed to retrieve attendance", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/attendance/" + attendanceId, 500, "Internal Server Error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Get attendance records by session
     */
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<List<Attendance>> getAttendanceBySession(@PathVariable String sessionId) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Retrieving attendance records for session: {}", sessionId);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/attendance/session/" + sessionId, null);
        
        try {
            List<Attendance> attendanceList = attendanceService.getAttendanceBySession(sessionId);
            logger.info("Retrieved {} attendance records for session: {}", attendanceList.size(), sessionId);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/attendance/session/" + sessionId, 200, 
                "List of " + attendanceList.size() + " attendance records");
            
            return ResponseEntity.ok(attendanceList);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve attendance records for session: {}", sessionId, e);
            LoggerUtil.logError(logger, "Failed to retrieve attendance records for session", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/attendance/session/" + sessionId, 500, "Internal Server Error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Get attendance records by student
     */
    @GetMapping("/student/{studentId}")
    public ResponseEntity<List<Attendance>> getAttendanceByStudent(@PathVariable String studentId) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Retrieving attendance records for student: {}", studentId);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/attendance/student/" + studentId, null);
        
        try {
            List<Attendance> attendanceList = attendanceService.getAttendanceByStudent(studentId);
            logger.info("Retrieved {} attendance records for student: {}", attendanceList.size(), studentId);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/attendance/student/" + studentId, 200, 
                "List of " + attendanceList.size() + " attendance records");
            
            return ResponseEntity.ok(attendanceList);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve attendance records for student: {}", studentId, e);
            LoggerUtil.logError(logger, "Failed to retrieve attendance records for student", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/attendance/student/" + studentId, 500, "Internal Server Error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Check if student attended a session
     */
    @GetMapping("/check")
    public ResponseEntity<Boolean> hasStudentAttended(@RequestParam String sessionId, 
                                                     @RequestParam String studentId) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Checking if student attended session - Student: {}, Session: {}", studentId, sessionId);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/attendance/check", 
            "sessionId=" + sessionId + "&studentId=" + studentId);
        
        try {
            boolean attended = attendanceService.hasStudentAttended(sessionId, studentId);
            logger.info("Student {} {} attended session {}", 
                studentId, attended ? "has" : "has not", sessionId);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/attendance/check", 200, String.valueOf(attended));
            
            return ResponseEntity.ok(attended);
            
        } catch (Exception e) {
            logger.error("Failed to check attendance for student: {} in session: {}", studentId, sessionId, e);
            LoggerUtil.logError(logger, "Failed to check attendance", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/attendance/check", 500, "Internal Server Error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Get attendance records by method
     */
    @GetMapping("/method/{method}")
    public ResponseEntity<List<Attendance>> getAttendanceByMethod(@PathVariable Attendance.AttendanceMethod method) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Retrieving attendance records by method: {}", method);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/attendance/method/" + method, null);
        
        try {
            List<Attendance> attendanceList = attendanceService.getAttendanceByMethod(method);
            logger.info("Retrieved {} attendance records by method: {}", attendanceList.size(), method);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/attendance/method/" + method, 200, 
                "List of " + attendanceList.size() + " attendance records");
            
            return ResponseEntity.ok(attendanceList);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve attendance records by method: {}", method, e);
            LoggerUtil.logError(logger, "Failed to retrieve attendance records by method", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/attendance/method/" + method, 500, "Internal Server Error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Get attendance records within date range
     */
    @GetMapping("/date-range")
    public ResponseEntity<List<Attendance>> getAttendanceBetweenDates(
            @RequestParam String startDate, 
            @RequestParam String endDate) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Retrieving attendance records between dates: {} and {}", startDate, endDate);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/attendance/date-range", 
            "startDate=" + startDate + "&endDate=" + endDate);
        
        try {
            LocalDateTime start = LocalDateTime.parse(startDate);
            LocalDateTime end = LocalDateTime.parse(endDate);
            
            List<Attendance> attendanceList = attendanceService.getAttendanceBetweenDates(start, end);
            logger.info("Retrieved {} attendance records between dates", attendanceList.size());
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/attendance/date-range", 200, 
                "List of " + attendanceList.size() + " attendance records");
            
            return ResponseEntity.ok(attendanceList);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve attendance records between dates", e);
            LoggerUtil.logError(logger, "Failed to retrieve attendance records between dates", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/attendance/date-range", 500, "Internal Server Error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Get attendance statistics for a session
     */
    @GetMapping("/session/{sessionId}/stats")
    public ResponseEntity<AttendanceService.AttendanceStats> getSessionAttendanceStats(@PathVariable String sessionId) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Calculating attendance statistics for session: {}", sessionId);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/attendance/session/" + sessionId + "/stats", null);
        
        try {
            AttendanceService.AttendanceStats stats = attendanceService.getSessionAttendanceStats(sessionId);
            logger.info("Session {} attendance stats: Total={}, QR={}, Manual={}, Proxy={}", 
                sessionId, stats.getTotalAttendance(), stats.getQrScanCount(), 
                stats.getManualCount(), stats.getProxyCount());
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/attendance/session/" + sessionId + "/stats", 200, stats.toString());
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            logger.error("Failed to calculate attendance statistics for session: {}", sessionId, e);
            LoggerUtil.logError(logger, "Failed to calculate attendance statistics", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/attendance/session/" + sessionId + "/stats", 500, "Internal Server Error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Delete attendance record
     */
    @DeleteMapping("/{attendanceId}")
    public ResponseEntity<Void> deleteAttendance(@PathVariable String attendanceId) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Deleting attendance record: {}", attendanceId);
        LoggerUtil.logApiRequest(logger, "DELETE", "/api/v1/attendance/" + attendanceId, null);
        
        try {
            attendanceService.deleteAttendance(attendanceId);
            logger.info("Attendance record deleted successfully: {}", attendanceId);
            LoggerUtil.logApiResponse(logger, "DELETE", "/api/v1/attendance/" + attendanceId, 204, "No Content");
            
            return ResponseEntity.noContent().build();
            
        } catch (IllegalArgumentException e) {
            logger.error("Attendance record not found for deletion: {}", e.getMessage());
            LoggerUtil.logApiResponse(logger, "DELETE", "/api/v1/attendance/" + attendanceId, 404, "Not Found");
            return ResponseEntity.notFound().build();
            
        } catch (Exception e) {
            logger.error("Failed to delete attendance record: {}", attendanceId, e);
            LoggerUtil.logError(logger, "Failed to delete attendance record", e);
            LoggerUtil.logApiResponse(logger, "DELETE", "/api/v1/attendance/" + attendanceId, 500, "Internal Server Error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            LoggerUtil.clearContext();
        }
    }
}
