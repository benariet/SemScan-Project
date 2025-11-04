package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.dto.ErrorResponse;
import edu.bgu.semscanapi.entity.Attendance;
import edu.bgu.semscanapi.service.AttendanceService;
import edu.bgu.semscanapi.service.AuthenticationService;
import edu.bgu.semscanapi.service.DatabaseLoggerService;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    
    @Autowired
    private AuthenticationService authenticationService;
    
    @Autowired
    private DatabaseLoggerService databaseLoggerService;
    
    /**
     * Record attendance for a student in a session
     */
    @PostMapping
    public ResponseEntity<Object> recordAttendance(@RequestBody Attendance attendance) {
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
            
            String userId = LoggerUtil.getCurrentUserId();
            Long userIdLong = null;
            try {
                userIdLong = userId != null && !userId.isEmpty() ? Long.parseLong(userId) : null;
            } catch (NumberFormatException ignored) {}
            String payload = String.format("correlationId=%s", LoggerUtil.getCurrentCorrelationId());
            databaseLoggerService.logError("ATTENDANCE_VALIDATION_ERROR", e.getMessage(), e, userIdLong, payload);
            
            ErrorResponse errorResponse = new ErrorResponse(
                e.getMessage(),
                "Bad Request",
                400,
                "/api/v1/attendance"
            );
            return ResponseEntity.badRequest().body(errorResponse);
            
        } catch (Exception e) {
            logger.error("Failed to record attendance", e);
            LoggerUtil.logError(logger, "Failed to record attendance", e);
            LoggerUtil.logApiResponse(logger, "POST", "/api/v1/attendance", 500, "Internal Server Error");
            
            String userId = LoggerUtil.getCurrentUserId();
            Long userIdLong = null;
            try {
                userIdLong = userId != null && !userId.isEmpty() ? Long.parseLong(userId) : null;
            } catch (NumberFormatException ignored) {}
            String payload = String.format("correlationId=%s", LoggerUtil.getCurrentCorrelationId());
            databaseLoggerService.logError("ATTENDANCE_RECORDING_ERROR", "Failed to record attendance", e, userIdLong, payload);
            
            ErrorResponse errorResponse = new ErrorResponse(
                "An unexpected error occurred while recording attendance",
                "Internal Server Error",
                500,
                "/api/v1/attendance"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Get attendance by ID
     */
    @GetMapping("/{attendanceId:\\d+}")
    public ResponseEntity<Object> getAttendanceById(@PathVariable Long attendanceId) {
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
                
                ErrorResponse errorResponse = new ErrorResponse(
                    "Attendance not found with ID: " + attendanceId,
                    "Not Found",
                    404,
                    "/api/v1/attendance/" + attendanceId
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }
            
        } catch (Exception e) {
            logger.error("Failed to retrieve attendance: {}", attendanceId, e);
            LoggerUtil.logError(logger, "Failed to retrieve attendance", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/attendance/" + attendanceId, 500, "Internal Server Error");
            
            ErrorResponse errorResponse = new ErrorResponse(
                "An unexpected error occurred while retrieving attendance",
                "Internal Server Error",
                500,
                "/api/v1/attendance/" + attendanceId
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Get attendance records by session
     */
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<Object> getAttendanceBySession(@PathVariable Long sessionId) {
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
            
            ErrorResponse errorResponse = new ErrorResponse(
                "An unexpected error occurred while retrieving attendance records for session",
                "Internal Server Error",
                500,
                "/api/v1/attendance/session/" + sessionId
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Get attendance records by student
     */
    @GetMapping("/student/{studentId}")
    public ResponseEntity<Object> getAttendanceByStudent(@PathVariable Long studentId) {
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
            
            ErrorResponse errorResponse = new ErrorResponse(
                "An unexpected error occurred while retrieving attendance records for student",
                "Internal Server Error",
                500,
                "/api/v1/attendance/student/" + studentId
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Check if student attended a session
     */
    @GetMapping("/check")
    public ResponseEntity<Object> hasStudentAttended(@RequestParam Long sessionId, 
                                                     @RequestParam Long studentId) {
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
            
            ErrorResponse errorResponse = new ErrorResponse(
                "An unexpected error occurred while checking attendance",
                "Internal Server Error",
                500,
                "/api/v1/attendance/check"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Get attendance records by method
     */
    @GetMapping("/method/{method}")
    public ResponseEntity<Object> getAttendanceByMethod(@PathVariable Attendance.AttendanceMethod method) {
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
            
            ErrorResponse errorResponse = new ErrorResponse(
                "An unexpected error occurred while retrieving attendance records by method",
                "Internal Server Error",
                500,
                "/api/v1/attendance/method/" + method
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Get attendance records within date range
     */
    @GetMapping("/date-range")
    public ResponseEntity<Object> getAttendanceBetweenDates(
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
            
            ErrorResponse errorResponse = new ErrorResponse(
                "An unexpected error occurred while retrieving attendance records between dates",
                "Internal Server Error",
                500,
                "/api/v1/attendance/date-range"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Get attendance statistics for a session
     */
    @GetMapping("/session/{sessionId}/stats")
    public ResponseEntity<Object> getSessionAttendanceStats(@PathVariable Long sessionId) {
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
            
            ErrorResponse errorResponse = new ErrorResponse(
                "An unexpected error occurred while calculating attendance statistics",
                "Internal Server Error",
                500,
                "/api/v1/attendance/session/" + sessionId + "/stats"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Delete attendance record
     */
    @DeleteMapping("/{attendanceId:\\d+}")
    public ResponseEntity<Object> deleteAttendance(@PathVariable Long attendanceId) {
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
            
            ErrorResponse errorResponse = new ErrorResponse(
                e.getMessage(),
                "Not Found",
                404,
                "/api/v1/attendance/" + attendanceId
            );
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            
        } catch (Exception e) {
            logger.error("Failed to delete attendance record: {}", attendanceId, e);
            LoggerUtil.logError(logger, "Failed to delete attendance record", e);
            LoggerUtil.logApiResponse(logger, "DELETE", "/api/v1/attendance/" + attendanceId, 500, "Internal Server Error");
            
            ErrorResponse errorResponse = new ErrorResponse(
                "An unexpected error occurred while deleting attendance record",
                "Internal Server Error",
                500,
                "/api/v1/attendance/" + attendanceId
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Get attendance records by session with API key authentication
     */
    @GetMapping
    public ResponseEntity<Object> getAttendanceBySessionQuery(
            @RequestParam Long sessionId) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Retrieving attendance records for session: {} with API key authentication", sessionId);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/attendance?sessionId=" + sessionId, null);
        
        try {
            // No API key validation for POC
            
            // Get attendance records for the session
            List<Attendance> attendanceList = attendanceService.getAttendanceBySession(sessionId);
            logger.info("Retrieved {} attendance records for session: {}", 
                attendanceList.size(), sessionId);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/attendance", 200, 
                "List of " + attendanceList.size() + " attendance records");
            
            return ResponseEntity.ok(attendanceList);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve attendance records for session: {}", sessionId, e);
            LoggerUtil.logError(logger, "Failed to retrieve attendance records for session", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/attendance", 500, "Internal Server Error");
            
            ErrorResponse errorResponse = new ErrorResponse(
                "An unexpected error occurred while retrieving attendance records for session",
                "Internal Server Error",
                500,
                "/api/v1/attendance"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Get pending manual attendance requests for a session
     * Delegates to ManualAttendanceController
     */
    @GetMapping("/pending-requests")
    public ResponseEntity<Object> getPendingRequests(@RequestParam Long sessionId) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Retrieving pending manual attendance requests for session: {}", sessionId);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/attendance/pending-requests?sessionId=" + sessionId, null);
        
        try {
            // Redirect to the manual attendance service
            List<Attendance> attendanceList = attendanceService.getAttendanceBySession(sessionId);
            
            // Filter for pending requests (PENDING_APPROVAL status)
            List<Attendance> pendingRequests = attendanceList.stream()
                .filter(attendance -> attendance.getRequestStatus() == Attendance.RequestStatus.PENDING_APPROVAL)
                .collect(java.util.stream.Collectors.toList());
            
            logger.info("Retrieved {} pending requests for session: {}", pendingRequests.size(), sessionId);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/attendance/pending-requests", 200, 
                "List of " + pendingRequests.size() + " pending requests");
            
            return ResponseEntity.ok(pendingRequests);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve pending manual attendance requests for session: {}", sessionId, e);
            LoggerUtil.logError(logger, "Failed to retrieve pending manual attendance requests", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/attendance/pending-requests", 500, "Internal Server Error");
            
            ErrorResponse errorResponse = new ErrorResponse(
                "An unexpected error occurred while retrieving pending requests",
                "Internal Server Error",
                500,
                "/api/v1/attendance/pending-requests"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Export attendance data as CSV
     */
    @GetMapping("/export/csv")
    public ResponseEntity<Object> exportCsv(
            @RequestParam Long sessionId) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Exporting CSV for session: {} with API key authentication", sessionId);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/attendance/export/csv?sessionId=" + sessionId, null);
        
        try {
            // No API key validation for POC
            
            // Get attendance records for the session
            List<Attendance> attendanceList = attendanceService.getAttendanceBySession(sessionId);
            
            // Generate CSV data
            byte[] csvData = generateCsvForSession(sessionId, attendanceList);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "attendance_" + sessionId + ".csv");
            
            logger.info("CSV export successful for session: {} - {} records", 
                sessionId, attendanceList.size());
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/attendance/export/csv", 200, 
                "CSV file with " + attendanceList.size() + " records");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(csvData);
            
        } catch (Exception e) {
            logger.error("Failed to export CSV for session: {}", sessionId, e);
            LoggerUtil.logError(logger, "Failed to export CSV for session", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/attendance/export/csv", 500, "Internal Server Error");
            
            ErrorResponse errorResponse = new ErrorResponse(
                "An unexpected error occurred while exporting CSV",
                "Internal Server Error",
                500,
                "/api/v1/attendance/export/csv"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Export attendance data as Excel (XLSX)
     */
    @GetMapping("/export/xlsx")
    public ResponseEntity<Object> exportXlsx(
            @RequestParam Long sessionId) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Exporting XLSX for session: {} with API key authentication", sessionId);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/attendance/export/xlsx?sessionId=" + sessionId, null);
        
        try {
            // No API key validation for POC
            
            // Get attendance records for the session
            List<Attendance> attendanceList = attendanceService.getAttendanceBySession(sessionId);
            
            // Generate Excel data (simplified - returns CSV format for now)
            byte[] excelData = generateExcelForSession(sessionId, attendanceList);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "attendance_" + sessionId + ".xlsx");
            
            logger.info("XLSX export successful for session: {} - {} records", 
                sessionId, attendanceList.size());
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/attendance/export/xlsx", 200, 
                "XLSX file with " + attendanceList.size() + " records");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(excelData);
            
        } catch (Exception e) {
            logger.error("Failed to export XLSX for session: {}", sessionId, e);
            LoggerUtil.logError(logger, "Failed to export XLSX for session", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/attendance/export/xlsx", 500, "Internal Server Error");
            
            ErrorResponse errorResponse = new ErrorResponse(
                "An unexpected error occurred while exporting XLSX",
                "Internal Server Error",
                500,
                "/api/v1/attendance/export/xlsx"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Generate CSV data for a session
     */
    private byte[] generateCsvForSession(Long sessionId, List<Attendance> attendanceList) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(outputStream);
        
        // CSV Header
        writer.println("Attendance ID,Session ID,Student ID,Attendance Time,Method");
        
        // CSV Data
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (Attendance attendance : attendanceList) {
            writer.printf("%s,%s,%s,%s,%s%n",
                attendance.getAttendanceId(),
                attendance.getSessionId(),
                attendance.getStudentId(),
                attendance.getAttendanceTime() != null ? attendance.getAttendanceTime().format(formatter) : "",
                attendance.getMethod() != null ? attendance.getMethod().toString() : ""
            );
        }
        
        writer.flush();
        writer.close();
        
        return outputStream.toByteArray();
    }
    
    /**
     * Generate Excel data for a session (simplified implementation)
     * Note: For a full Excel implementation, you would need Apache POI dependency
     */
    private byte[] generateExcelForSession(Long sessionId, List<Attendance> attendanceList) throws IOException {
        // For now, return CSV format as Excel
        // In a real implementation, you would use Apache POI to create actual Excel files
        return generateCsvForSession(sessionId, attendanceList);
    }
}
