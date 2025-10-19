package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.dto.ErrorResponse;
import edu.bgu.semscanapi.entity.Attendance;
import edu.bgu.semscanapi.service.AttendanceService;
import edu.bgu.semscanapi.service.AuthenticationService;
import edu.bgu.semscanapi.service.ManualAttendanceService;
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
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * REST Controller for Export operations
 * Provides export functionality for attendance data
 */
@RestController
@RequestMapping("/api/v1/export")
public class ExportController {
    
    private static final Logger logger = LoggerUtil.getLogger(ExportController.class);
    
    @Autowired
    private AttendanceService attendanceService;
    
    @Autowired
    private AuthenticationService authenticationService;
    
    @Autowired
    private ManualAttendanceService manualAttendanceService;
    
    /**
     * Export attendance data as CSV
     */
    @GetMapping("/csv")
    public ResponseEntity<Object> exportCsv(
            @RequestParam String sessionId,
            @RequestHeader("x-api-key") String apiKey) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Exporting CSV for session: {} with API key authentication", sessionId);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/export/csv?sessionId=" + sessionId, null);
        
        try {
            // Validate API key
            if (apiKey == null || apiKey.trim().isEmpty()) {
                logger.warn("No API key provided for CSV export request");
                LoggerUtil.logApiResponse(logger, "GET", "/api/v1/export/csv", 401, "Unauthorized - No API key");
                
                ErrorResponse errorResponse = new ErrorResponse(
                    "API key is required for this request",
                    "Unauthorized",
                    401,
                    "/api/v1/export/csv"
                );
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }
            
            var presenter = authenticationService.validateApiKey(apiKey);
            if (presenter.isEmpty()) {
                logger.warn("Invalid API key provided for CSV export request");
                LoggerUtil.logApiResponse(logger, "GET", "/api/v1/export/csv", 401, "Unauthorized - Invalid API key");
                
                ErrorResponse errorResponse = new ErrorResponse(
                    "Invalid API key provided",
                    "Unauthorized",
                    401,
                    "/api/v1/export/csv"
                );
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }
            
            // Check for pending manual attendance requests
            if (manualAttendanceService.hasPendingRequests(sessionId)) {
                long pendingCount = manualAttendanceService.getPendingRequestCount(sessionId);
                logger.warn("Cannot export CSV for session: {} - {} pending manual attendance requests", 
                           sessionId, pendingCount);
                LoggerUtil.logApiResponse(logger, "GET", "/api/v1/export/csv", 409, 
                    "Conflict - " + pendingCount + " pending requests");
                
                ErrorResponse errorResponse = new ErrorResponse(
                    "Cannot export while " + pendingCount + " manual attendance requests are pending approval. Please review and resolve all pending requests before exporting.",
                    "Conflict",
                    409,
                    "/api/v1/export/csv"
                );
                return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
            }
            
            // Get attendance records for the session
            List<Attendance> attendanceList = attendanceService.getAttendanceBySession(sessionId);
            
            // Generate CSV data
            byte[] csvData = generateCsvForSession(sessionId, attendanceList);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "attendance_" + sessionId + ".csv");
            
            logger.info("CSV export successful for session: {} by presenter: {} - {} records", 
                sessionId, presenter.get().getUserId(), attendanceList.size());
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/export/csv", 200, 
                "CSV file with " + attendanceList.size() + " records");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(csvData);
            
        } catch (Exception e) {
            logger.error("Failed to export CSV for session: {}", sessionId, e);
            LoggerUtil.logError(logger, "Failed to export CSV for session", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/export/csv", 500, "Internal Server Error");
            
            ErrorResponse errorResponse = new ErrorResponse(
                "An unexpected error occurred while exporting CSV",
                "Internal Server Error",
                500,
                "/api/v1/export/csv"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Export attendance data as Excel (XLSX)
     */
    @GetMapping("/xlsx")
    public ResponseEntity<Object> exportXlsx(
            @RequestParam String sessionId,
            @RequestHeader("x-api-key") String apiKey) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Exporting XLSX for session: {} with API key authentication", sessionId);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/export/xlsx?sessionId=" + sessionId, null);
        
        try {
            // Validate API key
            if (apiKey == null || apiKey.trim().isEmpty()) {
                logger.warn("No API key provided for XLSX export request");
                LoggerUtil.logApiResponse(logger, "GET", "/api/v1/export/xlsx", 401, "Unauthorized - No API key");
                
                ErrorResponse errorResponse = new ErrorResponse(
                    "API key is required for this request",
                    "Unauthorized",
                    401,
                    "/api/v1/export/xlsx"
                );
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }
            
            var presenter = authenticationService.validateApiKey(apiKey);
            if (presenter.isEmpty()) {
                logger.warn("Invalid API key provided for XLSX export request");
                LoggerUtil.logApiResponse(logger, "GET", "/api/v1/export/xlsx", 401, "Unauthorized - Invalid API key");
                
                ErrorResponse errorResponse = new ErrorResponse(
                    "Invalid API key provided",
                    "Unauthorized",
                    401,
                    "/api/v1/export/xlsx"
                );
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }
            
            // Check for pending manual attendance requests
            if (manualAttendanceService.hasPendingRequests(sessionId)) {
                long pendingCount = manualAttendanceService.getPendingRequestCount(sessionId);
                logger.warn("Cannot export XLSX for session: {} - {} pending manual attendance requests", 
                           sessionId, pendingCount);
                LoggerUtil.logApiResponse(logger, "GET", "/api/v1/export/xlsx", 409, 
                    "Conflict - " + pendingCount + " pending requests");
                
                ErrorResponse errorResponse = new ErrorResponse(
                    "Cannot export while " + pendingCount + " manual attendance requests are pending approval. Please review and resolve all pending requests before exporting.",
                    "Conflict",
                    409,
                    "/api/v1/export/xlsx"
                );
                return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
            }
            
            // Get attendance records for the session
            List<Attendance> attendanceList = attendanceService.getAttendanceBySession(sessionId);
            
            // Generate Excel data (simplified - returns CSV format for now)
            byte[] excelData = generateExcelForSession(sessionId, attendanceList);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "attendance_" + sessionId + ".xlsx");
            
            logger.info("XLSX export successful for session: {} by presenter: {} - {} records", 
                sessionId, presenter.get().getUserId(), attendanceList.size());
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/export/xlsx", 200, 
                "XLSX file with " + attendanceList.size() + " records");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(excelData);
            
        } catch (Exception e) {
            logger.error("Failed to export XLSX for session: {}", sessionId, e);
            LoggerUtil.logError(logger, "Failed to export XLSX for session", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/export/xlsx", 500, "Internal Server Error");
            
            ErrorResponse errorResponse = new ErrorResponse(
                "An unexpected error occurred while exporting XLSX",
                "Internal Server Error",
                500,
                "/api/v1/export/xlsx"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Generate CSV data for a session
     */
    private byte[] generateCsvForSession(String sessionId, List<Attendance> attendanceList) throws IOException {
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
    private byte[] generateExcelForSession(String sessionId, List<Attendance> attendanceList) throws IOException {
        // For now, return CSV format as Excel
        // In a real implementation, you would use Apache POI to create actual Excel files
        return generateCsvForSession(sessionId, attendanceList);
    }
}
