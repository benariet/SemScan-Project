package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.dto.ErrorResponse;
import edu.bgu.semscanapi.entity.Attendance;
import edu.bgu.semscanapi.entity.Seminar;
import edu.bgu.semscanapi.entity.SeminarSlot;
import edu.bgu.semscanapi.entity.Session;
import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.repository.SeminarRepository;
import edu.bgu.semscanapi.repository.SeminarSlotRepository;
import edu.bgu.semscanapi.repository.SessionRepository;
import edu.bgu.semscanapi.repository.UserRepository;
import edu.bgu.semscanapi.service.AttendanceService;
import edu.bgu.semscanapi.service.AuthenticationService;
import edu.bgu.semscanapi.service.DatabaseLoggerService;
import edu.bgu.semscanapi.service.EmailService;
import edu.bgu.semscanapi.service.ManualAttendanceService;
import edu.bgu.semscanapi.service.SessionService;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

    @Autowired
    private SessionService sessionService;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SeminarRepository seminarRepository;

    @Autowired
    private SeminarSlotRepository seminarSlotRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private DatabaseLoggerService databaseLoggerService;

    /**
     * Export attendance data as CSV
     */
    @GetMapping("/csv")
    public ResponseEntity<Object> exportCsv(
            @RequestParam Long sessionId) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Exporting CSV for session: {} with API key authentication", sessionId);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/export/csv?sessionId=" + sessionId, null);

        try {
            // No API key validation for POC

            // Check for pending manual attendance requests
            if (manualAttendanceService.hasPendingRequests(sessionId)) {
                long pendingCount = manualAttendanceService.getPendingRequestCount(sessionId);
                String errorMsg = String.format("Cannot export CSV for session: %s - %d pending manual attendance requests", 
                    sessionId, pendingCount);
                logger.warn(errorMsg);
                LoggerUtil.logApiResponse(logger, "GET", "/api/v1/export/csv", 409,
                        "Conflict - " + pendingCount + " pending requests");
                
                // Log to database
                databaseLoggerService.logError("EXPORT_BLOCKED_PENDING_REQUESTS", errorMsg, null, null, 
                    String.format("sessionId=%s,pendingCount=%d,format=CSV", sessionId, pendingCount));

                ErrorResponse errorResponse = new ErrorResponse(
                        "Cannot export while " + pendingCount
                                + " manual attendance requests are pending approval. Please review and resolve all pending requests before exporting.",
                        "Conflict",
                        409,
                        "/api/v1/export/csv");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
            }

            // Get attendance records for the session
            List<Attendance> attendanceList = attendanceService.getAttendanceBySession(sessionId);

            // Get session, slot, and presenter info for filename
            String filename = generateExportFilename(sessionId);
            if (filename == null) {
                filename = "attendance_" + sessionId + ".csv";
            }

            // Generate CSV data
            byte[] csvData = generateCsvForSession(sessionId, attendanceList);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", filename);

            logger.info("CSV export successful for session: {} - {} records",
                    sessionId, attendanceList.size());
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/export/csv", 200,
                    "CSV file with " + attendanceList.size() + " records");
            
            // Log successful export to database
            databaseLoggerService.logBusinessEvent("EXPORT_CSV_SUCCESS", 
                String.format("CSV export successful for session %s - %d records", sessionId, attendanceList.size()), 
                null);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(csvData);

        } catch (Exception e) {
            String errorMsg = String.format("Failed to export CSV for session: %s", sessionId);
            logger.error(errorMsg, e);
            LoggerUtil.logError(logger, "Failed to export CSV for session", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/export/csv", 500, "Internal Server Error");
            
            // Log error to database
            databaseLoggerService.logError("EXPORT_CSV_ERROR", errorMsg, e, null, 
                String.format("sessionId=%s,exceptionType=%s", sessionId, e.getClass().getName()));

            ErrorResponse errorResponse = new ErrorResponse(
                    "An unexpected error occurred while exporting CSV",
                    "Internal Server Error",
                    500,
                    "/api/v1/export/csv");
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
            @RequestParam Long sessionId) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Exporting XLSX for session: {} with API key authentication", sessionId);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/export/xlsx?sessionId=" + sessionId, null);

        try {
            // No API key validation for POC

            // Check for pending manual attendance requests
            if (manualAttendanceService.hasPendingRequests(sessionId)) {
                long pendingCount = manualAttendanceService.getPendingRequestCount(sessionId);
                String errorMsg = String.format("Cannot export XLSX for session: %s - %d pending manual attendance requests", 
                    sessionId, pendingCount);
                logger.warn(errorMsg);
                LoggerUtil.logApiResponse(logger, "GET", "/api/v1/export/xlsx", 409,
                        "Conflict - " + pendingCount + " pending requests");
                
                // Log to database
                databaseLoggerService.logError("EXPORT_BLOCKED_PENDING_REQUESTS", errorMsg, null, null, 
                    String.format("sessionId=%s,pendingCount=%d,format=XLSX", sessionId, pendingCount));

                ErrorResponse errorResponse = new ErrorResponse(
                        "Cannot export while " + pendingCount
                                + " manual attendance requests are pending approval. Please review and resolve all pending requests before exporting.",
                        "Conflict",
                        409,
                        "/api/v1/export/xlsx");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
            }

            // Get attendance records for the session
            List<Attendance> attendanceList = attendanceService.getAttendanceBySession(sessionId);

            // Get session, slot, and presenter info for filename
            String filename = generateExportFilename(sessionId);
            if (filename == null) {
                filename = "attendance_" + sessionId + ".xlsx";
            } else {
                // Replace .csv with .xlsx
                filename = filename.replace(".csv", ".xlsx");
            }

            // Generate Excel data (simplified - returns CSV format for now)
            byte[] excelData = generateExcelForSession(sessionId, attendanceList);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(
                    MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", filename);

            logger.info("XLSX export successful for session: {} - {} records",
                    sessionId, attendanceList.size());
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/export/xlsx", 200,
                    "XLSX file with " + attendanceList.size() + " records");
            
            // Log successful export to database
            databaseLoggerService.logBusinessEvent("EXPORT_XLSX_SUCCESS", 
                String.format("XLSX export successful for session %s - %d records", sessionId, attendanceList.size()), 
                null);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(excelData);

        } catch (Exception e) {
            String errorMsg = String.format("Failed to export XLSX for session: %s", sessionId);
            logger.error(errorMsg, e);
            LoggerUtil.logError(logger, "Failed to export XLSX for session", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/export/xlsx", 500, "Internal Server Error");
            
            // Log error to database
            databaseLoggerService.logError("EXPORT_XLSX_ERROR", errorMsg, e, null, 
                String.format("sessionId=%s,exceptionType=%s", sessionId, e.getClass().getName()));

            ErrorResponse errorResponse = new ErrorResponse(
                    "An unexpected error occurred while exporting XLSX",
                    "Internal Server Error",
                    500,
                    "/api/v1/export/xlsx");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }

    /**
     * Send export file via email
     * POST /api/v1/export/send-email?sessionId=123&format=csv
     */
    @PostMapping("/send-email")
    public ResponseEntity<Object> sendExportEmail(
            @RequestParam Long sessionId,
            @RequestParam(defaultValue = "csv") String format) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Sending export email for session: {} in format: {}", sessionId, format);
        LoggerUtil.logApiRequest(logger, "POST", "/api/v1/export/send-email?sessionId=" + sessionId + "&format=" + format, null);

        try {
            // Validate format
            if (!format.equalsIgnoreCase("csv") && !format.equalsIgnoreCase("xlsx")) {
                ErrorResponse errorResponse = new ErrorResponse(
                        "Invalid format. Supported formats: csv, xlsx",
                        "Bad Request",
                        400,
                        "/api/v1/export/send-email");
                LoggerUtil.logApiResponse(logger, "POST", "/api/v1/export/send-email", 400, "Invalid format");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
            }

            // Check for pending manual attendance requests
            if (manualAttendanceService.hasPendingRequests(sessionId)) {
                long pendingCount = manualAttendanceService.getPendingRequestCount(sessionId);
                String errorMsg = String.format("Cannot send export email for session: %s - %d pending manual attendance requests", 
                    sessionId, pendingCount);
                logger.warn(errorMsg);
                LoggerUtil.logApiResponse(logger, "POST", "/api/v1/export/send-email", 409,
                        "Conflict - " + pendingCount + " pending requests");
                
                // Log to database
                databaseLoggerService.logError("EXPORT_EMAIL_BLOCKED_PENDING_REQUESTS", errorMsg, null, null, 
                    String.format("sessionId=%s,pendingCount=%d,format=%s", sessionId, pendingCount, format));

                ErrorResponse errorResponse = new ErrorResponse(
                        "Cannot export while " + pendingCount
                                + " manual attendance requests are pending approval. Please review and resolve all pending requests before exporting.",
                        "Conflict",
                        409,
                        "/api/v1/export/send-email");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
            }

            // Check if email service is configured
            if (!emailService.isEmailConfigured()) {
                String errorMsg = "Email service is not configured. Please configure SMTP settings in application properties.";
                LoggerUtil.logApiResponse(logger, "POST", "/api/v1/export/send-email", 503, "Email service not configured");
                
                // Log to database
                databaseLoggerService.logError("EXPORT_EMAIL_NOT_CONFIGURED", errorMsg, null, null, 
                    String.format("sessionId=%s,format=%s", sessionId, format));
                
                ErrorResponse errorResponse = new ErrorResponse(
                        errorMsg,
                        "Service Unavailable",
                        503,
                        "/api/v1/export/send-email");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
            }

            // Get attendance records for the session
            List<Attendance> attendanceList = attendanceService.getAttendanceBySession(sessionId);

            // Generate export data
            byte[] exportData;
            String filename;
            if (format.equalsIgnoreCase("xlsx")) {
                exportData = generateExcelForSession(sessionId, attendanceList);
                filename = generateExportFilename(sessionId);
                if (filename == null) {
                    filename = "attendance_" + sessionId + ".xlsx";
                } else {
                    filename = filename.replace(".csv", ".xlsx");
                }
            } else {
                exportData = generateCsvForSession(sessionId, attendanceList);
                filename = generateExportFilename(sessionId);
                if (filename == null) {
                    filename = "attendance_" + sessionId + ".csv";
                }
            }

            // Send email
            boolean emailSent = emailService.sendExportEmail(sessionId, filename, exportData, format);
            if (!emailSent) {
                String errorMsg = "Failed to send export email. Please check email configuration and try again.";
                LoggerUtil.logApiResponse(logger, "POST", "/api/v1/export/send-email", 500, "Failed to send email");
                
                // Log email send failure to database
                databaseLoggerService.logError("EXPORT_EMAIL_SEND_FAILED", errorMsg, null, null, 
                    String.format("sessionId=%s,format=%s,recordCount=%d", sessionId, format, attendanceList.size()));
                
                ErrorResponse errorResponse = new ErrorResponse(
                        errorMsg,
                        "Internal Server Error",
                        500,
                        "/api/v1/export/send-email");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            }

            String successMsg = String.format("Export email sent successfully for session: %s - %d records, format: %s", 
                sessionId, attendanceList.size(), format);
            logger.info(successMsg);
            LoggerUtil.logApiResponse(logger, "POST", "/api/v1/export/send-email", 200,
                    "Email sent with " + attendanceList.size() + " records");
            
            // Log successful email send to database
            databaseLoggerService.logBusinessEvent("EXPORT_EMAIL_SENT", successMsg, null);

            Map<String, Object> response = Map.of(
                    "success", true,
                    "message", "Export email sent successfully",
                    "sessionId", sessionId,
                    "format", format,
                    "filename", filename,
                    "records", attendanceList.size(),
                    "recipients", emailService.getEmailRecipients()
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            String errorMsg = String.format("Failed to send export email for session: %s", sessionId);
            logger.error(errorMsg, e);
            LoggerUtil.logError(logger, "Failed to send export email for session", e);
            LoggerUtil.logApiResponse(logger, "POST", "/api/v1/export/send-email", 500, "Internal Server Error");
            
            // Log error to database
            databaseLoggerService.logError("EXPORT_EMAIL_ERROR", errorMsg, e, null, 
                String.format("sessionId=%s,format=%s,exceptionType=%s", sessionId, format, e.getClass().getName()));

            ErrorResponse errorResponse = new ErrorResponse(
                    "An unexpected error occurred while sending export email",
                    "Internal Server Error",
                    500,
                    "/api/v1/export/send-email");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }

    /**
     * Generate export filename with date, presenter name, and time slot
     * Format: day_month_year_presenter_name_time-slot.csv
     * Example: 9_11_2025_john_doe_13-15.csv
     */
    private String generateExportFilename(Long sessionId) {
        try {
            // Get session
            Optional<Session> sessionOpt = sessionRepository.findById(sessionId);
            if (sessionOpt.isEmpty()) {
                logger.warn("Session not found for filename generation: {}", sessionId);
                return null;
            }
            Session session = sessionOpt.get();

            // Get slot from session (find slot where legacySessionId = sessionId)
            List<SeminarSlot> slots = seminarSlotRepository.findAll();
            Optional<SeminarSlot> slotOpt = slots.stream()
                    .filter(slot -> slot.getLegacySessionId() != null && slot.getLegacySessionId().equals(sessionId))
                    .findFirst();

            // Get seminar
            Optional<Seminar> seminarOpt = seminarRepository.findById(session.getSeminarId());
            if (seminarOpt.isEmpty()) {
                logger.warn("Seminar not found for session: {}", session.getSeminarId());
                return null;
            }
            Seminar seminar = seminarOpt.get();

            // Get presenter user
            Optional<User> presenterOpt = userRepository.findByBguUsernameIgnoreCase(seminar.getPresenterUsername());
            if (presenterOpt.isEmpty()) {
                logger.warn("Presenter not found: {}", seminar.getPresenterUsername());
                return null;
            }
            User presenter = presenterOpt.get();

            // Format date: day_month_year (e.g., 9_11_2025)
            LocalDate date;
            if (slotOpt.isPresent()) {
                date = slotOpt.get().getSlotDate();
            } else {
                date = session.getStartTime() != null ? session.getStartTime().toLocalDate() : LocalDate.now();
            }
            String dateStr = String.format("%d_%d_%d", date.getDayOfMonth(), date.getMonthValue(), date.getYear());

            // Format presenter name: first_last (e.g., john_doe)
            String firstName = presenter.getFirstName() != null ? presenter.getFirstName().toLowerCase(Locale.ROOT).replace(" ", "_") : "unknown";
            String lastName = presenter.getLastName() != null ? presenter.getLastName().toLowerCase(Locale.ROOT).replace(" ", "_") : "unknown";
            String presenterName = firstName + "_" + lastName;

            // Format time slot: start-end (e.g., 13-15) - hours only
            String timeSlot = "00-00";
            if (slotOpt.isPresent()) {
                SeminarSlot slot = slotOpt.get();
                if (slot.getStartTime() != null && slot.getEndTime() != null) {
                    timeSlot = formatTimeForFilename(slot.getStartTime()) + "-" + formatTimeForFilename(slot.getEndTime());
                }
            } else if (session.getStartTime() != null && session.getEndTime() != null) {
                timeSlot = formatTimeForFilename(session.getStartTime().toLocalTime()) + "-" + formatTimeForFilename(session.getEndTime().toLocalTime());
            }

            String filename = String.format("%s_%s_%s.csv", dateStr, presenterName, timeSlot);
            logger.debug("Generated export filename: {}", filename);
            return filename;
        } catch (Exception e) {
            logger.error("Error generating export filename for session: {}", sessionId, e);
            return null;
        }
    }

    /**
     * Format time for filename (HH format - hours only)
     * Example: 13:00 -> 13, 15:30 -> 15
     */
    private String formatTimeForFilename(LocalTime time) {
        if (time == null) {
            return "00";
        }
        return String.format("%02d", time.getHour());
    }

    /**
     * Generate CSV data for a session
     */
    private byte[] generateCsvForSession(Long sessionId, List<Attendance> attendanceList) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(outputStream);

        // Get session and slot info for time slot column
        Optional<Session> sessionOpt = sessionRepository.findById(sessionId);
        String timeSlot = getTimeSlotForSession(sessionId, sessionOpt.orElse(null));

        // Get user info map for first name, last name, and presenter
        Map<String, User> userMap = attendanceList.stream()
                .map(Attendance::getStudentUsername)
                .distinct()
                .filter(username -> username != null)
                .map(username -> userRepository.findByBguUsernameIgnoreCase(username))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toMap(
                        user -> user.getBguUsername().toLowerCase(Locale.ROOT),
                        user -> user,
                        (existing, replacement) -> existing
                ));

        // Get presenter info
        String presenterName = getPresenterNameForSession(sessionId, sessionOpt.orElse(null));

        // CSV Header: Method, Attendance Time, Username, First Name, Last Name, National ID, Time Slot, Presenter
        writer.println("Method,Attendance Time,Username,First Name,Last Name,National ID,Time Slot,Presenter");

        // CSV Data
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (Attendance attendance : attendanceList) {
            String username = attendance.getStudentUsername();
            User user = userMap.get(username != null ? username.toLowerCase(Locale.ROOT) : null);
            
            String firstName = user != null && user.getFirstName() != null ? user.getFirstName() : "";
            String lastName = user != null && user.getLastName() != null ? user.getLastName() : "";
            String nationalId = user != null && user.getNationalIdNumber() != null ? user.getNationalIdNumber() : "";
            String method = attendance.getMethod() != null ? attendance.getMethod().toString() : "";
            String attendanceTime = attendance.getAttendanceTime() != null ? attendance.getAttendanceTime().format(formatter) : "";
            String studentUsername = username != null ? username : "";

            writer.printf("%s,%s,%s,%s,%s,%s,%s,%s%n",
                    escapeCsv(method),
                    escapeCsv(attendanceTime),
                    escapeCsv(studentUsername),
                    escapeCsv(firstName),
                    escapeCsv(lastName),
                    escapeCsv(nationalId),
                    escapeCsv(timeSlot),
                    escapeCsv(presenterName));
        }

        writer.flush();
        writer.close();

        return outputStream.toByteArray();
    }

    /**
     * Get time slot string for a session
     */
    private String getTimeSlotForSession(Long sessionId, Session session) {
        if (session == null) {
            return "";
        }

        // Try to get from slot first
        List<SeminarSlot> slots = seminarSlotRepository.findAll();
        Optional<SeminarSlot> slotOpt = slots.stream()
                .filter(slot -> slot.getLegacySessionId() != null && slot.getLegacySessionId().equals(sessionId))
                .findFirst();

        if (slotOpt.isPresent()) {
            SeminarSlot slot = slotOpt.get();
            if (slot.getStartTime() != null && slot.getEndTime() != null) {
                return formatTimeRange(slot.getStartTime(), slot.getEndTime());
            }
        }

        // Fallback to session times
        if (session.getStartTime() != null && session.getEndTime() != null) {
            return formatTimeRange(session.getStartTime().toLocalTime(), session.getEndTime().toLocalTime());
        }

        return "";
    }

    /**
     * Format time range (HH:mm-HH:mm)
     */
    private String formatTimeRange(LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            return "";
        }
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        return start.format(timeFormatter) + "-" + end.format(timeFormatter);
    }


    private String getPresenterNameForSession(Long sessionId, Session session) {
        if (session == null) {
            logger.warn("Cannot get presenter name - session is null for sessionId: {}", sessionId);
            return "";
        }

        try {
            // CRITICAL: Get seminar associated with THIS session
            // sessions.seminar_id → seminars.presenter_username → users (presenter info)
            Optional<Seminar> seminarOpt = seminarRepository.findById(session.getSeminarId());
            if (seminarOpt.isEmpty()) {
                logger.warn("Seminar not found for session {} (seminarId: {})", sessionId, session.getSeminarId());
                databaseLoggerService.logError("EXPORT_SEMINAR_NOT_FOUND", 
                    String.format("Seminar not found for session %s (seminarId: %s)", sessionId, session.getSeminarId()),
                    null, null, String.format("sessionId=%s,seminarId=%s", sessionId, session.getSeminarId()));
                return "";
            }
            Seminar seminar = seminarOpt.get();

            logger.debug("Found seminar {} for session {} - presenter_username: {}", 
                seminar.getSeminarId(), sessionId, seminar.getPresenterUsername());

            Optional<User> presenterOpt = userRepository.findByBguUsernameIgnoreCase(seminar.getPresenterUsername());
            if (presenterOpt.isEmpty()) {
                logger.warn("Presenter user not found for session {} (presenter_username: {})", 
                    sessionId, seminar.getPresenterUsername());
                databaseLoggerService.logError("EXPORT_PRESENTER_NOT_FOUND", 
                    String.format("Presenter user not found for session %s (presenter_username: %s)", 
                        sessionId, seminar.getPresenterUsername()),
                    null, null, String.format("sessionId=%s,presenterUsername=%s", sessionId, seminar.getPresenterUsername()));
                return "";
            }
            User presenter = presenterOpt.get();

            String firstName = presenter.getFirstName() != null ? presenter.getFirstName() : "";
            String lastName = presenter.getLastName() != null ? presenter.getLastName() : "";
            String presenterName = (firstName + " " + lastName).trim();
            
            logger.info("Presenter name for session {}: {} (seminarId: {}, presenter_username: {})", 
                sessionId, presenterName, seminar.getSeminarId(), seminar.getPresenterUsername());
            
            // Log to database for verification
            databaseLoggerService.logAction("INFO", "EXPORT_PRESENTER_RESOLVED", 
                String.format("Resolved presenter for session %s: %s", sessionId, presenterName),
                null, String.format("sessionId=%s,seminarId=%s,presenterUsername=%s,presenterName=%s", 
                    sessionId, seminar.getSeminarId(), seminar.getPresenterUsername(), presenterName));
            
            return presenterName;
        } catch (Exception e) {
            logger.error("Error getting presenter name for session: {}", sessionId, e);
            databaseLoggerService.logError("EXPORT_PRESENTER_ERROR", 
                String.format("Error getting presenter name for session %s", sessionId), e, null,
                String.format("sessionId=%s,exceptionType=%s", sessionId, e.getClass().getName()));
            return "";
        }
    }

    /**
     * Escape CSV field (handle commas and quotes)
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        // If value contains comma, quote, or newline, wrap in quotes and escape quotes
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Generate Excel data for a session using Apache POI
     */
    private byte[] generateExcelForSession(Long sessionId, List<Attendance> attendanceList) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Attendance Report");

            // Get session and slot info for time slot column
            Optional<Session> sessionOpt = sessionRepository.findById(sessionId);
            String timeSlot = getTimeSlotForSession(sessionId, sessionOpt.orElse(null));
            String presenterName = getPresenterNameForSession(sessionId, sessionOpt.orElse(null));

            // Get user info map for first name, last name
            Map<String, User> userMap = attendanceList.stream()
                    .map(Attendance::getStudentUsername)
                    .distinct()
                    .filter(username -> username != null)
                    .map(username -> userRepository.findByBguUsernameIgnoreCase(username))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toMap(
                            user -> user.getBguUsername().toLowerCase(Locale.ROOT),
                            user -> user,
                            (existing, replacement) -> existing
                    ));

            // Create header row
            Row headerRow = sheet.createRow(0);
            String[] headers = {
                    "Method", "Attendance Time", "Username", "First Name", "Last Name", "National ID", "Time Slot", "Presenter"
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

                String username = attendance.getStudentUsername();
                User user = userMap.get(username != null ? username.toLowerCase(Locale.ROOT) : null);
                
                String firstName = user != null && user.getFirstName() != null ? user.getFirstName() : "";
                String lastName = user != null && user.getLastName() != null ? user.getLastName() : "";
                String nationalId = user != null && user.getNationalIdNumber() != null ? user.getNationalIdNumber() : "";
                String method = attendance.getMethod() != null ? attendance.getMethod().toString() : "";
                String attendanceTime = attendance.getAttendanceTime() != null ? attendance.getAttendanceTime().format(formatter) : "";
                String studentUsername = username != null ? username : "";

                dataRow.createCell(0).setCellValue(method);
                dataRow.createCell(1).setCellValue(attendanceTime);
                dataRow.createCell(2).setCellValue(studentUsername);
                dataRow.createCell(3).setCellValue(firstName);
                dataRow.createCell(4).setCellValue(lastName);
                dataRow.createCell(5).setCellValue(nationalId);
                dataRow.createCell(6).setCellValue(timeSlot);
                dataRow.createCell(7).setCellValue(presenterName);
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

    private String toStringOrBlank(Object value) {
        return value == null ? "" : value.toString();
    }
}
