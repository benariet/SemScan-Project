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
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    
    @Autowired
    private AttendanceRepository attendanceRepository;
    
    @Autowired
    private SessionRepository sessionRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Business rules
    private static final int WINDOW_START_MINUTES = -10; // 10 minutes before session start
    private static final int WINDOW_END_MINUTES = 15;    // 15 minutes after session start
    private static final int SAFE_AUTO_APPROVE_CAP = 5;  // Base cap for auto-approval
    private static final double AUTO_APPROVE_PERCENTAGE = 0.05; // 5% of roster size
    
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
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
            
            User student = userRepository.findByBguUsername(studentUsername)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentUsername));
            
            Optional<Attendance> existingAttendance = attendanceRepository
                .findBySessionIdAndStudentUsername(sessionId, studentUsername);
            
            if (existingAttendance.isPresent()) {
                Attendance existing = existingAttendance.get();
                if (existing.getRequestStatus() == Attendance.RequestStatus.CONFIRMED) {
                    throw new IllegalArgumentException("Student already marked as present for this session");
                }
                if (existing.getRequestStatus() == Attendance.RequestStatus.PENDING_APPROVAL) {
                    return updateExistingRequest(existing, request);
                }
            }
            
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime sessionStart = session.getStartTime();
            LocalDateTime windowStart = sessionStart.plusMinutes(WINDOW_START_MINUTES);
            LocalDateTime windowEnd = sessionStart.plusMinutes(WINDOW_END_MINUTES);
            
            boolean inWindow = now.isAfter(windowStart) && now.isBefore(windowEnd);
            
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
            
        } catch (JsonProcessingException e) {
            logger.error("Error serializing auto flags: {}", e.getMessage());
            throw new RuntimeException("Error processing request", e);
        } catch (Exception e) {
            logger.error("Error creating manual attendance request: {}", e.getMessage());
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
            logger.error("Error retrieving pending requests: {}", e.getMessage());
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
                .orElseThrow(() -> new IllegalArgumentException("Attendance request not found: " + attendanceId));
            
            if (attendance.getRequestStatus() != Attendance.RequestStatus.PENDING_APPROVAL) {
                throw new IllegalArgumentException("Request is not pending approval");
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
            
        } catch (Exception e) {
            logger.error("Error approving manual attendance request: {}", e.getMessage());
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
                .orElseThrow(() -> new IllegalArgumentException("Attendance request not found: " + attendanceId));
            
            if (attendance.getRequestStatus() != Attendance.RequestStatus.PENDING_APPROVAL) {
                throw new IllegalArgumentException("Request is not pending approval");
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
            
        } catch (Exception e) {
            logger.error("Error rejecting manual attendance request: {}", e.getMessage());
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
            if (session != null) {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime sessionStart = session.getStartTime();
                LocalDateTime windowStart = sessionStart.plusMinutes(WINDOW_START_MINUTES);
                LocalDateTime windowEnd = sessionStart.plusMinutes(WINDOW_END_MINUTES);
                boolean inWindow = now.isAfter(windowStart) && now.isBefore(windowEnd);
                
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
            logger.error("Error updating existing request: {}", e.getMessage());
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
