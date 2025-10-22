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

// Apache POI imports for Excel generation
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

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
            @RequestParam String sessionId) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Exporting CSV for session: {} with API key authentication", sessionId);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/export/csv?sessionId=" + sessionId, null);
        
        try {
            // No API key validation for POC
            
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
            
            logger.info("CSV export successful for session: {} - {} records", 
                sessionId, attendanceList.size());
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
            @RequestParam String sessionId) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Exporting XLSX for session: {} with API key authentication", sessionId);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/export/xlsx?sessionId=" + sessionId, null);
        
        try {
            // No API key validation for POC
            
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
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", "attendance_" + sessionId + ".xlsx");
            
            logger.info("XLSX export successful for session: {} - {} records", 
                sessionId, attendanceList.size());
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
     * Generate Excel data for a session using Apache POI
     */
    private byte[] generateExcelForSession(String sessionId, List<Attendance> attendanceList) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Attendance Report");
            
            // Create header row
            Row headerRow = sheet.createRow(0);
            String[] headers = {
                "Attendance ID", "Session ID", "Student ID", "Attendance Time", 
                "Method", "Request Status", "Manual Reason", "Requested At", 
                "Approved By", "Approved At"
            };
            
            // Style for header row
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            
            // Create header cells
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Create data rows
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            for (int i = 0; i < attendanceList.size(); i++) {
                Attendance attendance = attendanceList.get(i);
                Row dataRow = sheet.createRow(i + 1);
                
                dataRow.createCell(0).setCellValue(attendance.getAttendanceId() != null ? attendance.getAttendanceId() : "");
                dataRow.createCell(1).setCellValue(attendance.getSessionId() != null ? attendance.getSessionId() : "");
                dataRow.createCell(2).setCellValue(attendance.getStudentId() != null ? attendance.getStudentId() : "");
                dataRow.createCell(3).setCellValue(attendance.getAttendanceTime() != null ? 
                    attendance.getAttendanceTime().format(formatter) : "");
                dataRow.createCell(4).setCellValue(attendance.getMethod() != null ? attendance.getMethod().toString() : "");
                dataRow.createCell(5).setCellValue(attendance.getRequestStatus() != null ? attendance.getRequestStatus().toString() : "");
                dataRow.createCell(6).setCellValue(attendance.getManualReason() != null ? attendance.getManualReason() : "");
                dataRow.createCell(7).setCellValue(attendance.getRequestedAt() != null ? 
                    attendance.getRequestedAt().format(formatter) : "");
                dataRow.createCell(8).setCellValue(attendance.getApprovedBy() != null ? attendance.getApprovedBy() : "");
                dataRow.createCell(9).setCellValue(attendance.getApprovedAt() != null ? 
                    attendance.getApprovedAt().format(formatter) : "");
            }
            
            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            // Write to byte array
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}
