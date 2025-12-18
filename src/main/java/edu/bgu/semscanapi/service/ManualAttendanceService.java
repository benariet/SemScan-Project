package edu.bgu.semscanapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.bgu.semscanapi.dto.ManualAttendanceRequest;
import edu.bgu.semscanapi.dto.ManualAttendanceResponse;
import edu.bgu.semscanapi.entity.Attendance;
import edu.bgu.semscanapi.entity.Session;
import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.repository.AttendanceRepository;
import edu.bgu.semscanapi.repository.SessionRepository;
import edu.bgu.semscanapi.repository.UserRepository;
import edu.bgu.semscanapi.config.GlobalConfig;
import edu.bgu.semscanapi.service.DatabaseLoggerService;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class ManualAttendanceService {
    
    private static final Logger logger = LoggerUtil.getLogger(ManualAttendanceService.class);
    private static final ZoneId ISRAEL_TIMEZONE = ZoneId.of("Asia/Jerusalem");
    
    @Autowired
    private AttendanceRepository attendanceRepository;
    
    @Autowired
    private SessionRepository sessionRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private DatabaseLoggerService databaseLoggerService;
    
    @Autowired
    private GlobalConfig globalConfig;
    
    @Autowired(required = false)
    private AppConfigService appConfigService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Business rules
    private static final int SAFE_AUTO_APPROVE_CAP = 5;  // Base cap for auto-approval
    private static final double AUTO_APPROVE_PERCENTAGE = 0.05; // 5% of roster size
    
    /**
     * Get current time in Israel timezone to match session times
     * Session times are stored as LocalDateTime (no timezone) and interpreted as Israel time
     */
    private LocalDateTime nowIsrael() {
        return ZonedDateTime.now(ISRAEL_TIMEZONE).toLocalDateTime();
    }
    
    /**
     * Create a manual attendance request
     */
    public ManualAttendanceResponse createManualRequest(ManualAttendanceRequest request) {
        logger.info("Creating manual attendance request for student: {} in session: {}", 
                   request.getStudentUsername(), request.getSessionId());
        
        try {
            Long sessionId = request.getSessionId();
            String studentUsername = normalizeUsername(request.getStudentUsername());

            Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> {
                    String errorMsg = "Session not found: " + sessionId;
                    databaseLoggerService.logError("MANUAL_ATTENDANCE_SESSION_NOT_FOUND", errorMsg, null, 
                        studentUsername, String.format("sessionId=%s", sessionId));
                    return new IllegalArgumentException(errorMsg);
                });
            
            // Check time window first - allow requests even if session is CLOSED, as long as we're within the window
            // This allows students to request attendance after the presenter closes the session, within the configured window
            // CRITICAL: Use Israel timezone to match session times
            LocalDateTime now = nowIsrael();
            LocalDateTime sessionStart = session.getStartTime();
            if (sessionStart == null) {
                String errorMsg = "Session start time is not set: " + sessionId;
                logger.error(errorMsg);
                databaseLoggerService.logError("MANUAL_ATTENDANCE_NO_START_TIME", errorMsg, null, 
                    studentUsername, String.format("sessionId=%s", sessionId));
                throw new IllegalArgumentException(errorMsg);
            }
            
            // Use configurable window values from AppConfigService (fallback to GlobalConfig)
            int windowBeforeMinutes = appConfigService != null 
                    ? appConfigService.getIntegerConfig("student_attendance_window_before_minutes", 5)
                    : globalConfig.getManualAttendanceWindowBeforeMinutes();
            int windowAfterMinutes = appConfigService != null 
                    ? appConfigService.getIntegerConfig("student_attendance_window_after_minutes", 10)
                    : globalConfig.getManualAttendanceWindowAfterMinutes();
            
            LocalDateTime windowStart = sessionStart.minusMinutes(windowBeforeMinutes);
            
            // Window end should be: session end time (if exists), otherwise session start + windowAfterMinutes
            LocalDateTime windowEnd;
            LocalDateTime sessionEnd = session.getEndTime();
            if (sessionEnd != null) {
                // Use session end time as the window end - allows requests until session actually ends
                windowEnd = sessionEnd;
                logger.debug("Using session end time {} as window end for manual attendance", sessionEnd);
            } else {
                // No session end time - use configured window after start
                windowEnd = sessionStart.plusMinutes(windowAfterMinutes);
                logger.debug("No session end time, using {} minutes after start ({}) as window end", windowAfterMinutes, windowEnd);
            }
            
            // Use inclusive boundaries: !now.isBefore(start) && !now.isAfter(end)
            // This handles edge cases where windowStart == windowEnd (when both windowBeforeMinutes and windowAfterMinutes are 0)
            // Equivalent to: now >= windowStart && now <= windowEnd
            boolean inWindow = !now.isBefore(windowStart) && !now.isAfter(windowEnd);
            
            // Log time comparison for debugging
            LocalDateTime nowUtc = LocalDateTime.now(); // For logging comparison
            logger.info("⏰ Manual attendance time check - Session: {}, Now (UTC): {}, Now (Israel): {}, Window: {} to {}, In window: {}", 
                sessionId, nowUtc, now, windowStart, windowEnd, inWindow);
            
            // Allow requests if we're within the window, even if session is CLOSED
            // This enables students to request attendance after the presenter closes the session
            if (!inWindow) {
                String windowDescription = sessionEnd != null 
                    ? String.format("%d minutes before session start until session end", windowBeforeMinutes)
                    : String.format("%d minutes before to %d minutes after session start", windowBeforeMinutes, windowAfterMinutes);
                
                String errorMsg = String.format("Manual attendance request is outside the allowed time window. " +
                    "Window: %s. " +
                    "Session started at: %s%s, current time: %s", 
                    windowDescription, sessionStart, 
                    sessionEnd != null ? String.format(", session ends at: %s", sessionEnd) : "",
                    now);
                logger.error("❌ Manual attendance request outside window - Session: {}, Student: {}, Now (Israel): {}, Window: {} to {}", 
                    sessionId, studentUsername, now, windowStart, windowEnd);
                databaseLoggerService.logError("MANUAL_ATTENDANCE_OUTSIDE_WINDOW", errorMsg, null, 
                    studentUsername, String.format("sessionId=%s,windowStart=%s,windowEnd=%s,currentTime=%s", 
                        sessionId, windowStart, windowEnd, now));
                throw new IllegalArgumentException(errorMsg);
            }
            
            // Log if session is closed but request is allowed (within window)
            if (session.getStatus() != Session.SessionStatus.OPEN) {
                logger.info("Allowing manual attendance request for CLOSED session {} (status: {}) - within time window. Student: {}", 
                    sessionId, session.getStatus(), studentUsername);
                databaseLoggerService.logAttendance("MANUAL_ATTENDANCE_CLOSED_SESSION_ALLOWED", 
                    studentUsername, sessionId, "MANUAL_REQUEST");
            } else {
                logger.info("Session validation passed for manual attendance - SessionID: {} is OPEN", sessionId);
            }
            
            User student = userRepository.findByBguUsername(studentUsername)
                .orElseThrow(() -> {
                    String errorMsg = "Student not found: " + studentUsername;
                    databaseLoggerService.logError("MANUAL_ATTENDANCE_STUDENT_NOT_FOUND", errorMsg, null, 
                        studentUsername, String.format("sessionId=%s", sessionId));
                    return new IllegalArgumentException(errorMsg);
                });
            
            Optional<Attendance> existingAttendance = attendanceRepository
                .findBySessionIdAndStudentUsername(sessionId, studentUsername);
            
            if (existingAttendance.isPresent()) {
                Attendance existing = existingAttendance.get();
                if (existing.getRequestStatus() == Attendance.RequestStatus.CONFIRMED) {
                    String errorMsg = "Student already marked as present for this session";
                    databaseLoggerService.logError("MANUAL_ATTENDANCE_ALREADY_CONFIRMED", errorMsg, null, 
                        studentUsername, String.format("sessionId=%s,attendanceId=%s", sessionId, existing.getAttendanceId()));
                    throw new IllegalArgumentException(errorMsg);
                }
                if (existing.getRequestStatus() == Attendance.RequestStatus.PENDING_APPROVAL) {
                    return updateExistingRequest(existing, request);
                }
            }
            
            // Window validation already done above, reuse the calculated values
            
            Map<String, Object> autoFlags = generateAutoFlags(session, studentUsername, inWindow);
            
            Attendance attendance;
            if (existingAttendance.isPresent()) {
                attendance = existingAttendance.get();
            } else {
                attendance = new Attendance();
                attendance.setSessionId(sessionId);
                attendance.setStudentUsername(studentUsername);
            }
            attendance.setAttendanceTime(now);
            attendance.setMethod(Attendance.AttendanceMethod.MANUAL_REQUEST);
            attendance.setRequestStatus(Attendance.RequestStatus.PENDING_APPROVAL);
            attendance.setManualReason(request.getReason());
            attendance.setRequestedAt(now);
            attendance.setDeviceId(request.getDeviceId());
            attendance.setAutoFlags(objectMapper.writeValueAsString(autoFlags));
            
            attendance = attendanceRepository.save(attendance);
            
            logger.info("Manual attendance request created successfully - ID: {}, Student: {}, Session: {}", 
                       attendance.getAttendanceId(), studentUsername, sessionId);
            
            // Log to database
            databaseLoggerService.logAttendance("MANUAL_ATTENDANCE_REQUEST_CREATED", 
                studentUsername, sessionId, "MANUAL_REQUEST");
            
            return new ManualAttendanceResponse(
                attendance.getAttendanceId(),
                attendance.getSessionId(),
                attendance.getStudentUsername(),
                student.getFirstName() + " " + student.getLastName(),
                attendance.getManualReason(),
                attendance.getRequestStatus() != null ? attendance.getRequestStatus().name() : "PENDING_APPROVAL",
                attendance.getRequestedAt(),
                attendance.getAutoFlags(),
                "Request submitted successfully. Waiting for approval."
            );
            
        } catch (IllegalArgumentException e) {
            // Already logged above, just re-throw
            throw e;
        } catch (JsonProcessingException e) {
            String errorMsg = "Error serializing auto flags: " + e.getMessage();
            logger.error(errorMsg);
            databaseLoggerService.logError("MANUAL_ATTENDANCE_JSON_ERROR", errorMsg, e, 
                request.getStudentUsername(), String.format("sessionId=%s", request.getSessionId()));
            throw new RuntimeException("Error processing request", e);
        } catch (Exception e) {
            String errorMsg = "Error creating manual attendance request: " + e.getMessage();
            logger.error(errorMsg, e);
            databaseLoggerService.logError("MANUAL_ATTENDANCE_CREATE_ERROR", errorMsg, e, 
                request.getStudentUsername(), String.format("sessionId=%s,exceptionType=%s", 
                    request.getSessionId(), e.getClass().getName()));
            throw e;
        }
    }
    
    /**
     * Get pending manual attendance requests for a session
     */
    public List<ManualAttendanceResponse> getPendingRequests(Long sessionId) {
        logger.info("Retrieving pending manual attendance requests for session: {}", sessionId);
        
        try {
            List<Attendance> pendingRequests = attendanceRepository.findPendingRequestsBySessionId(sessionId);
            
            return pendingRequests.stream().map(attendance -> {
                try {
                    User student = userRepository.findByBguUsername(attendance.getStudentUsername()).orElse(null);
                    String studentName = student != null ? 
                        student.getFirstName() + " " + student.getLastName() : "Unknown Student";
                    
                    return new ManualAttendanceResponse(
                        attendance.getAttendanceId(),
                        attendance.getSessionId(),
                        attendance.getStudentUsername(),
                        studentName,
                        attendance.getManualReason(),
                        attendance.getRequestStatus() != null ? attendance.getRequestStatus().name() : "PENDING_APPROVAL",
                        attendance.getRequestedAt(),
                        attendance.getAutoFlags(),
                        null
                    );
                } catch (Exception e) {
                    logger.error("Error processing pending request: {}", e.getMessage());
                    return null;
                }
            }).filter(Objects::nonNull).collect(Collectors.toList());
            
        } catch (Exception e) {
            String errorMsg = "Error retrieving pending requests: " + e.getMessage();
            logger.error(errorMsg, e);
            databaseLoggerService.logError("MANUAL_ATTENDANCE_RETRIEVAL_ERROR", errorMsg, e, null, 
                String.format("sessionId=%s,exceptionType=%s", sessionId, e.getClass().getName()));
            throw new RuntimeException("Error retrieving pending requests", e);
        }
    }
    
    /**
     * Approve a manual attendance request
     */
    public ManualAttendanceResponse approveRequest(Long attendanceId, String approvedByUsername) {
        logger.info("Approving manual attendance request: {} by: {}", attendanceId, approvedByUsername);
        
        try {
            String normalizedApprover = normalizeUsername(approvedByUsername);
            Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> {
                    String errorMsg = "Attendance request not found: " + attendanceId;
                    databaseLoggerService.logError("MANUAL_ATTENDANCE_APPROVE_NOT_FOUND", errorMsg, null, 
                        approvedByUsername, String.format("attendanceId=%s", attendanceId));
                    return new IllegalArgumentException(errorMsg);
                });
            
            if (attendance.getRequestStatus() != Attendance.RequestStatus.PENDING_APPROVAL) {
                String errorMsg = "Request is not pending approval";
                databaseLoggerService.logError("MANUAL_ATTENDANCE_APPROVE_INVALID_STATUS", errorMsg, null, 
                    approvedByUsername, String.format("attendanceId=%s,currentStatus=%s", 
                        attendanceId, attendance.getRequestStatus()));
                throw new IllegalArgumentException(errorMsg);
            }
            
            attendance.setRequestStatus(Attendance.RequestStatus.CONFIRMED);
            attendance.setMethod(Attendance.AttendanceMethod.MANUAL);
            attendance.setApprovedByUsername(normalizedApprover);
            attendance.setApprovedAt(LocalDateTime.now());
            
            attendance = attendanceRepository.save(attendance);
            
            User student = userRepository.findByBguUsername(attendance.getStudentUsername()).orElse(null);
            String studentName = student != null ? 
                student.getFirstName() + " " + student.getLastName() : "Unknown Student";
            
            logger.info("Manual attendance request approved successfully - ID: {}, Student: {}", 
                       attendanceId, studentName);
            
            // Log to database
            databaseLoggerService.logAttendance("MANUAL_ATTENDANCE_APPROVED", 
                attendance.getStudentUsername(), attendance.getSessionId(), "MANUAL");
            
            return new ManualAttendanceResponse(
                attendance.getAttendanceId(),
                attendance.getSessionId(),
                attendance.getStudentUsername(),
                studentName,
                attendance.getManualReason(),
                attendance.getRequestStatus() != null ? attendance.getRequestStatus().name() : "CONFIRMED",
                attendance.getRequestedAt(),
                attendance.getAutoFlags(),
                "Request approved successfully"
            );
            
        } catch (IllegalArgumentException e) {
            // Already logged above, just re-throw
            throw e;
        } catch (Exception e) {
            String errorMsg = "Error approving manual attendance request: " + e.getMessage();
            logger.error(errorMsg, e);
            databaseLoggerService.logError("MANUAL_ATTENDANCE_APPROVE_ERROR", errorMsg, e, 
                approvedByUsername, String.format("attendanceId=%s,exceptionType=%s", 
                    attendanceId, e.getClass().getName()));
            throw e;
        }
    }
    
    /**
     * Reject a manual attendance request
     */
    public ManualAttendanceResponse rejectRequest(Long attendanceId, String approvedByUsername) {
        logger.info("Rejecting manual attendance request: {} by: {}", attendanceId, approvedByUsername);
        
        try {
            String normalizedApprover = normalizeUsername(approvedByUsername);
            Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> {
                    String errorMsg = "Attendance request not found: " + attendanceId;
                    databaseLoggerService.logError("MANUAL_ATTENDANCE_REJECT_NOT_FOUND", errorMsg, null, 
                        approvedByUsername, String.format("attendanceId=%s", attendanceId));
                    return new IllegalArgumentException(errorMsg);
                });
            
            if (attendance.getRequestStatus() != Attendance.RequestStatus.PENDING_APPROVAL) {
                String errorMsg = "Request is not pending approval";
                databaseLoggerService.logError("MANUAL_ATTENDANCE_REJECT_INVALID_STATUS", errorMsg, null, 
                    approvedByUsername, String.format("attendanceId=%s,currentStatus=%s", 
                        attendanceId, attendance.getRequestStatus()));
                throw new IllegalArgumentException(errorMsg);
            }
            
            attendance.setRequestStatus(Attendance.RequestStatus.REJECTED);
            attendance.setApprovedByUsername(normalizedApprover);
            attendance.setApprovedAt(LocalDateTime.now());
            
            attendance = attendanceRepository.save(attendance);
            
            User student = userRepository.findByBguUsername(attendance.getStudentUsername()).orElse(null);
            String studentName = student != null ? 
                student.getFirstName() + " " + student.getLastName() : "Unknown Student";
            
            logger.info("Manual attendance request rejected - ID: {}, Student: {}", 
                       attendanceId, studentName);
            
            // Log to database
            databaseLoggerService.logAttendance("MANUAL_ATTENDANCE_REJECTED", 
                attendance.getStudentUsername(), attendance.getSessionId(), "MANUAL_REQUEST");
            
            return new ManualAttendanceResponse(
                attendance.getAttendanceId(),
                attendance.getSessionId(),
                attendance.getStudentUsername(),
                studentName,
                attendance.getManualReason(),
                attendance.getRequestStatus() != null ? attendance.getRequestStatus().name() : "REJECTED",
                attendance.getRequestedAt(),
                attendance.getAutoFlags(),
                "Request rejected"
            );
            
        } catch (IllegalArgumentException e) {
            // Already logged above, just re-throw
            throw e;
        } catch (Exception e) {
            String errorMsg = "Error rejecting manual attendance request: " + e.getMessage();
            logger.error(errorMsg, e);
            databaseLoggerService.logError("MANUAL_ATTENDANCE_REJECT_ERROR", errorMsg, e, 
                approvedByUsername, String.format("attendanceId=%s,exceptionType=%s", 
                    attendanceId, e.getClass().getName()));
            throw e;
        }
    }
    
    /**
     * Check if session has pending requests (for export validation)
     */
    public boolean hasPendingRequests(Long sessionId) {
        return attendanceRepository.countPendingRequestsBySessionId(sessionId) > 0;
    }
    
    /**
     * Get count of pending requests for a session
     */
    public long getPendingRequestCount(Long sessionId) {
        return attendanceRepository.countPendingRequestsBySessionId(sessionId);
    }
    
    /**
     * Generate auto-flags for manual attendance request
     */
    private Map<String, Object> generateAutoFlags(Session session, String studentUsername, boolean inWindow) {
        Map<String, Object> flags = new HashMap<>();
        
        boolean hasExistingAttendance = attendanceRepository
            .findBySessionIdAndStudentUsername(session.getSessionId(), studentUsername)
            .map(attendance -> attendance.getRequestStatus() == Attendance.RequestStatus.CONFIRMED)
            .orElse(false);
        
        long totalAttendance = attendanceRepository.countBySessionId(session.getSessionId());
        int autoApproveCap = Math.max(SAFE_AUTO_APPROVE_CAP, (int) (totalAttendance * AUTO_APPROVE_PERCENTAGE));
        
        long pendingCount = attendanceRepository.countPendingRequestsBySessionId(session.getSessionId());
        boolean capExceeded = pendingCount >= autoApproveCap;
        
        flags.put("inWindow", inWindow);
        flags.put("duplicate", hasExistingAttendance);
        flags.put("capExceeded", capExceeded);
        flags.put("autoApproveCap", autoApproveCap);
        flags.put("pendingCount", pendingCount);
        flags.put("generatedAt", LocalDateTime.now().toString());
        
        return flags;
    }
    
    /**
     * Update existing pending request
     */
    private ManualAttendanceResponse updateExistingRequest(Attendance existing, ManualAttendanceRequest request) {
        try {
            String normalizedUsername = normalizeUsername(request.getStudentUsername());
            existing.setStudentUsername(normalizedUsername);
            existing.setManualReason(request.getReason());
            existing.setRequestedAt(LocalDateTime.now());
            existing.setDeviceId(request.getDeviceId());
            existing.setRequestStatus(Attendance.RequestStatus.PENDING_APPROVAL);
            existing.setMethod(Attendance.AttendanceMethod.MANUAL_REQUEST);
            
            Session session = sessionRepository.findById(request.getSessionId()).orElse(null);
            if (session != null && session.getStartTime() != null) {
                // CRITICAL: Use Israel timezone to match session times
                LocalDateTime now = nowIsrael();
                LocalDateTime sessionStart = session.getStartTime();
                
                // Use configurable window values from AppConfigService (fallback to GlobalConfig)
                int windowBeforeMinutes = appConfigService != null 
                        ? appConfigService.getIntegerConfig("student_attendance_window_before_minutes", 5)
                        : globalConfig.getManualAttendanceWindowBeforeMinutes();
                int windowAfterMinutes = appConfigService != null 
                        ? appConfigService.getIntegerConfig("student_attendance_window_after_minutes", 10)
                        : globalConfig.getManualAttendanceWindowAfterMinutes();
                
                LocalDateTime windowStart = sessionStart.minusMinutes(windowBeforeMinutes);
                
                // Window end should be: session end time (if exists), otherwise session start + windowAfterMinutes
                LocalDateTime windowEnd;
                LocalDateTime sessionEnd = session.getEndTime();
                if (sessionEnd != null) {
                    windowEnd = sessionEnd;
                } else {
                    windowEnd = sessionStart.plusMinutes(windowAfterMinutes);
                }
                
                // Use inclusive boundaries: !now.isBefore(start) && !now.isAfter(end)
                // This handles edge cases where windowStart == windowEnd (when both windowBeforeMinutes and windowAfterMinutes are 0)
                // Equivalent to: now >= windowStart && now <= windowEnd
                boolean inWindow = !now.isBefore(windowStart) && !now.isAfter(windowEnd);
                
                Map<String, Object> autoFlags = generateAutoFlags(session, normalizedUsername, inWindow);
                existing.setAutoFlags(objectMapper.writeValueAsString(autoFlags));
            }
            
            existing = attendanceRepository.save(existing);
            
            User student = userRepository.findByBguUsername(normalizedUsername).orElse(null);
            String studentName = student != null ? 
                student.getFirstName() + " " + student.getLastName() : "Unknown Student";
            
            return new ManualAttendanceResponse(
                existing.getAttendanceId(),
                existing.getSessionId(),
                existing.getStudentUsername(),
                studentName,
                existing.getManualReason(),
                existing.getRequestStatus() != null ? existing.getRequestStatus().name() : "PENDING_APPROVAL",
                existing.getRequestedAt(),
                existing.getAutoFlags(),
                "Request updated successfully. Waiting for approval."
            );
            
        } catch (JsonProcessingException e) {
            String errorMsg = "Error updating existing request: " + e.getMessage();
            logger.error(errorMsg);
            databaseLoggerService.logError("MANUAL_ATTENDANCE_UPDATE_JSON_ERROR", errorMsg, e, 
                request.getStudentUsername(), String.format("sessionId=%s", request.getSessionId()));
            throw new RuntimeException("Error updating request", e);
        }
    }
    
    private String normalizeUsername(String username) {
        if (username == null) {
            return null;
        }
        String trimmed = username.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }
}
