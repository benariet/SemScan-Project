package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.entity.Attendance;
import edu.bgu.semscanapi.entity.Session;
import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.repository.AttendanceRepository;
import edu.bgu.semscanapi.repository.SessionRepository;
import edu.bgu.semscanapi.repository.UserRepository;
import edu.bgu.semscanapi.util.LoggerUtil;
import edu.bgu.semscanapi.util.SessionLoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Service class for Attendance business logic
 * Provides comprehensive logging for all operations
 */
@Service
@Transactional
public class AttendanceService {
    
    private static final Logger logger = LoggerUtil.getLogger(AttendanceService.class);
    
    @Autowired
    private AttendanceRepository attendanceRepository;
    
    @Autowired
    private SessionRepository sessionRepository;
    
    @Autowired
    private DatabaseLoggerService databaseLoggerService;
    
    @Autowired
    private UserRepository userRepository;
    
    /**
     * Record attendance for a student in a session
     */
    public Attendance recordAttendance(Attendance attendance) {
        logger.info("Recording attendance for student: {} in session: {}", 
            attendance.getStudentUsername(), attendance.getSessionId());
        LoggerUtil.setStudentUsername(attendance.getStudentUsername());
        LoggerUtil.setSessionId(attendance.getSessionId() != null ? attendance.getSessionId().toString() : null);
        
        attendance.setStudentUsername(normalizeUsername(attendance.getStudentUsername()));
        
        try {
            // CRITICAL FIX: Query session by sessionId (NOT by slotId)
            // This ensures we validate the exact session provided in the request,
            // allowing multiple presenters to have separate sessions for the same time slot
            Optional<Session> session = sessionRepository.findById(attendance.getSessionId());
            if (session.isEmpty()) {
                String errorMsg = "Session not found: " + attendance.getSessionId();
                logger.error(errorMsg);
                databaseLoggerService.logError("ATTENDANCE_SESSION_NOT_FOUND", errorMsg, null, 
                    attendance.getStudentUsername(), String.format("sessionId=%s", attendance.getSessionId()));
                throw new IllegalArgumentException(errorMsg);
            }
            
            Session sessionEntity = session.get();
            logger.info("Validating session for attendance - SessionID: {}, Status: {}, StartTime: {}, EndTime: {}", 
                sessionEntity.getSessionId(), sessionEntity.getStatus(), 
                sessionEntity.getStartTime(), sessionEntity.getEndTime());
            
            // CRITICAL: Validate that THIS specific session is OPEN
            // Do NOT check other sessions in the slot - only validate the exact sessionId provided
            // This allows multiple presenters to have separate sessions for the same slot
            if (sessionEntity.getStatus() != Session.SessionStatus.OPEN) {
                String errorMessage = "Session is not open: " + attendance.getSessionId() + " (status: " + sessionEntity.getStatus() + ")";
                logger.error("Session is not open: {} (status: {}). Student: {} cannot attend.", 
                    attendance.getSessionId(), sessionEntity.getStatus(), attendance.getStudentUsername());
                databaseLoggerService.logError("ATTENDANCE_SESSION_NOT_OPEN", errorMessage, null, 
                    attendance.getStudentUsername(), String.format("sessionId=%s,status=%s", 
                        attendance.getSessionId(), sessionEntity.getStatus()));
                throw new IllegalArgumentException(errorMessage);
            }
            
            logger.info("Session validation passed - SessionID: {} is OPEN, allowing attendance submission", 
                sessionEntity.getSessionId());
            
            // Verify student exists in database using case-insensitive username lookup (handles username variations)
            Optional<User> student = userRepository.findByBguUsernameIgnoreCase(attendance.getStudentUsername());
            if (student.isEmpty()) {
                String errorMsg = "Student not found: " + attendance.getStudentUsername();
                logger.error(errorMsg);
                databaseLoggerService.logError("ATTENDANCE_STUDENT_NOT_FOUND", errorMsg, null, 
                    attendance.getStudentUsername(), String.format("sessionId=%s", attendance.getSessionId()));
                throw new IllegalArgumentException(errorMsg);
            }
            
            logger.debug("Student found: {}", student.get().getBguUsername());
            
            // Prevent duplicate attendance: check if student already recorded attendance for this session (case-insensitive)
            String normalizedUsername = attendance.getStudentUsername();
            boolean alreadyAttended = attendanceRepository.existsBySessionIdAndStudentUsernameIgnoreCase(
                attendance.getSessionId(), normalizedUsername);
            
            logger.debug("Checking duplicate attendance - Session: {}, Student: {}, AlreadyAttended: {}", 
                attendance.getSessionId(), normalizedUsername, alreadyAttended);
            
            if (alreadyAttended) {
                // Get the existing attendance record for logging
                Optional<Attendance> existingAttendance = attendanceRepository.findBySessionIdAndStudentUsernameIgnoreCase(
                    attendance.getSessionId(), normalizedUsername);
                if (existingAttendance.isPresent()) {
                    String errorMsg = "Student already attended this session";
                    logger.warn("Student already attended session: {} - {} (Attendance ID: {}, Username in DB: {})", 
                        normalizedUsername, attendance.getSessionId(), 
                        existingAttendance.get().getAttendanceId(),
                        existingAttendance.get().getStudentUsername());
                    databaseLoggerService.logError("ATTENDANCE_DUPLICATE", errorMsg, null, 
                        normalizedUsername, String.format("sessionId=%s,existingAttendanceId=%s", 
                            attendance.getSessionId(), existingAttendance.get().getAttendanceId()));
                    throw new IllegalArgumentException(errorMsg);
                }
            }
            
            // Default attendance time to current timestamp if not provided in request
            if (attendance.getAttendanceTime() == null) {
                attendance.setAttendanceTime(LocalDateTime.now());
                logger.debug("Set attendance time to current time");
            }
            
            // Default attendance method to QR_SCAN if not specified in request
            if (attendance.getMethod() == null) {
                attendance.setMethod(Attendance.AttendanceMethod.QR_SCAN);
                logger.debug("Set default attendance method: QR_SCAN");
            }
            
            // CRITICAL: Ensure sessionId is set correctly before saving
            // The attendance object should already have sessionId from the request, but verify it
            Long requestedSessionId = attendance.getSessionId();
            if (requestedSessionId == null) {
                String errorMsg = "Attendance request missing sessionId";
                logger.error(errorMsg);
                databaseLoggerService.logError("ATTENDANCE_MISSING_SESSION_ID", errorMsg, null, 
                    attendance.getStudentUsername(), "attendance object missing sessionId");
                throw new IllegalArgumentException(errorMsg);
            }
            
            // Verify the sessionId matches the validated session
            if (!Objects.equals(requestedSessionId, sessionEntity.getSessionId())) {
                String errorMsg = String.format("SessionId mismatch! Request: %s, Validated: %s", 
                    requestedSessionId, sessionEntity.getSessionId());
                logger.error(errorMsg);
                databaseLoggerService.logError("ATTENDANCE_SESSION_ID_MISMATCH", errorMsg, null, 
                    attendance.getStudentUsername(), String.format("requestedSessionId=%s,validatedSessionId=%s", 
                        requestedSessionId, sessionEntity.getSessionId()));
                throw new IllegalStateException(errorMsg);
            }
            
            // Explicitly set sessionId to ensure it's correct (defensive programming)
            attendance.setSessionId(requestedSessionId);
            
            logger.info("Saving attendance record - Student: {}, SessionId: {} (verified)", 
                attendance.getStudentUsername(), attendance.getSessionId());
            
            // Keep string IDs for API consistency - database will handle conversion
            // The database triggers will generate the string IDs automatically
            
            Attendance savedAttendance = attendanceRepository.save(attendance);
            
            // CRITICAL: Verify the saved attendance record has the correct sessionId
            if (!Objects.equals(savedAttendance.getSessionId(), requestedSessionId)) {
                String errorMsg = String.format("CRITICAL ERROR: Saved attendance record has wrong sessionId! Expected: %s, Got: %s", 
                    requestedSessionId, savedAttendance.getSessionId());
                logger.error(errorMsg);
                databaseLoggerService.logError("ATTENDANCE_SAVED_WRONG_SESSION_ID", errorMsg, null, 
                    attendance.getStudentUsername(), String.format("expectedSessionId=%s,actualSessionId=%s,attendanceId=%s", 
                        requestedSessionId, savedAttendance.getSessionId(), savedAttendance.getAttendanceId()));
                throw new IllegalStateException(errorMsg);
            }
            
            logger.info("Attendance recorded successfully: {} for student: {} in session: {} (verified sessionId matches)",
                        savedAttendance.getAttendanceId(), savedAttendance.getStudentUsername(), savedAttendance.getSessionId());
            
            // Log to session-specific log file
                    SessionLoggerUtil.logAttendance(savedAttendance.getSessionId(),
                                                  savedAttendance.getStudentUsername(),
                                                  savedAttendance.getMethod().toString(),
                                                  savedAttendance.getAttendanceTime().toString());
            
                    LoggerUtil.logAttendanceEvent(logger, "ATTENDANCE_RECORDED", 
                        savedAttendance.getStudentUsername(),
                        savedAttendance.getSessionId() != null ? savedAttendance.getSessionId().toString() : null,
                        savedAttendance.getMethod().toString());
            
            // Log to database
            databaseLoggerService.logAttendance("ATTENDANCE_RECORDED", 
                savedAttendance.getStudentUsername(), savedAttendance.getSessionId(), 
                savedAttendance.getMethod().toString());
            
            return savedAttendance;
            
        } catch (IllegalArgumentException e) {
            // Already logged above, just re-throw
            throw e;
        } catch (Exception e) {
            String errorMsg = String.format("Failed to record attendance for student: %s in session: %s", 
                attendance.getStudentUsername(), attendance.getSessionId());
            logger.error(errorMsg, e);
            databaseLoggerService.logError("ATTENDANCE_RECORDING_ERROR", errorMsg, e, 
                attendance.getStudentUsername(), String.format("sessionId=%s,exceptionType=%s", 
                    attendance.getSessionId(), e.getClass().getName()));
            throw e;
        } finally {
            LoggerUtil.clearStudentUsername();
            LoggerUtil.clearKey("sessionId");
        }
    }
    
    /**
     * Get attendance by ID
     */
    @Transactional(readOnly = true)
    public Optional<Attendance> getAttendanceById(Long attendanceId) {
        logger.debug("Retrieving attendance by ID: {}", attendanceId);
        
        try {
            Optional<Attendance> attendance = attendanceRepository.findById(attendanceId);
            if (attendance.isPresent()) {
                logger.debug("Attendance found: {} for student: {} in session: {}", 
                    attendanceId, attendance.get().getStudentUsername(), attendance.get().getSessionId());
                LoggerUtil.setStudentUsername(attendance.get().getStudentUsername());
                LoggerUtil.setSessionId(attendance.get().getSessionId().toString());
                LoggerUtil.logDatabaseOperation(logger, "SELECT", "attendance", attendanceId.toString());
            } else {
                logger.warn("Attendance not found: {}", attendanceId);
            }
            return attendance;
        } finally {
            LoggerUtil.clearStudentUsername();
            LoggerUtil.clearKey("sessionId");
        }
    }
    
    /**
     * Get attendance records by session
     */
    @Transactional(readOnly = true)
    public List<Attendance> getAttendanceBySession(Long sessionId) {
        logger.info("Retrieving attendance records for session: {}", sessionId);
        LoggerUtil.setSessionId(sessionId != null ? sessionId.toString() : null);
        
        try {
            // CRITICAL: Query attendance records by THIS specific sessionId
            // This ensures that when multiple sessions exist for the same slot,
            // each session's attendance records are returned independently
            // Do NOT query by slot_id or join in a way that returns wrong data
            List<Attendance> attendanceList = attendanceRepository.findBySessionId(sessionId);
            
            // Verify all returned records belong to the requested session
            List<Attendance> invalidRecords = attendanceList.stream()
                .filter(attendance -> !Objects.equals(attendance.getSessionId(), sessionId))
                .toList();
            
            if (!invalidRecords.isEmpty()) {
                String errorMsg = String.format("CRITICAL ERROR: Repository returned %d attendance records with wrong sessionId! Expected: %s, Found: %s", 
                    invalidRecords.size(), sessionId, 
                    invalidRecords.stream().map(Attendance::getSessionId).distinct().toList());
                logger.error(errorMsg);
                
                // Log to database
                databaseLoggerService.logError("ATTENDANCE_WRONG_SESSION_ID", errorMsg, null, null, 
                    String.format("requestedSessionId=%s,invalidSessionIds=%s,invalidRecordIds=%s", 
                        sessionId,
                        invalidRecords.stream().map(Attendance::getSessionId).distinct().toList(),
                        invalidRecords.stream().map(Attendance::getAttendanceId).toList()));
                
                // Filter out invalid records
                attendanceList = attendanceList.stream()
                    .filter(attendance -> Objects.equals(attendance.getSessionId(), sessionId))
                    .toList();
                
                logger.warn("Filtered out {} invalid attendance records. Returning {} valid records for session {}", 
                    invalidRecords.size(), attendanceList.size(), sessionId);
            }
            
            logger.info("Retrieved {} attendance records for session: {} (verified all records belong to this session)", 
                attendanceList.size(), sessionId);
            
            // CRITICAL: Always log detailed info for export debugging
            // This helps diagnose if records have wrong sessionIds
            if (attendanceList.size() > 0) {
                logger.info("=== EXPORT DEBUG - SessionId: {}, Record Count: {} ===", sessionId, attendanceList.size());
                attendanceList.forEach(attendance -> {
                    logger.info("  Record {}: Student={}, SessionId={}, Method={}, Time={}", 
                        attendance.getAttendanceId(), 
                        attendance.getStudentUsername(), 
                        attendance.getSessionId(), 
                        attendance.getMethod(), 
                        attendance.getAttendanceTime());
                });
                logger.info("=== END EXPORT DEBUG ===");
                
                // Log to database with detailed record info
                String recordSummary = attendanceList.stream()
                    .limit(10) // Limit to first 10 to avoid huge payloads
                    .map(a -> String.format("id=%s,student=%s,sessionId=%s", 
                        a.getAttendanceId(), a.getStudentUsername(), a.getSessionId()))
                    .collect(java.util.stream.Collectors.joining(";"));
                databaseLoggerService.logAction("INFO", "ATTENDANCE_EXPORT_QUERY", 
                    String.format("Export query for session %s returned %d records", sessionId, attendanceList.size()),
                    null, String.format("sessionId=%s,recordCount=%d,records=%s", sessionId, attendanceList.size(), recordSummary));
            } else {
                logger.info("No attendance records found for session: {}", sessionId);
                databaseLoggerService.logBusinessEvent("ATTENDANCE_EXPORT_EMPTY", 
                    String.format("No attendance records found for session %s", sessionId), null);
            }
            
            LoggerUtil.logDatabaseOperation(logger, "SELECT_BY_SESSION", "attendance", sessionId != null ? sessionId.toString() : "null");
            return attendanceList;
        } catch (Exception e) {
            String errorMsg = String.format("Failed to retrieve attendance records for session: %s", sessionId);
            logger.error(errorMsg, e);
            databaseLoggerService.logError("ATTENDANCE_RETRIEVAL_ERROR", errorMsg, e, null, 
                String.format("sessionId=%s,exceptionType=%s", sessionId, e.getClass().getName()));
            throw e;
        } finally {
            LoggerUtil.clearKey("sessionId");
        }
    }
    
    /**
     * Get attendance records by student
     */
    @Transactional(readOnly = true)
    public List<Attendance> getAttendanceByStudent(String studentUsername) {
        logger.info("Retrieving attendance records for student: {}", studentUsername);
        LoggerUtil.setStudentUsername(studentUsername);
        
        try {
            List<Attendance> attendanceList = attendanceRepository.findByStudentUsername(studentUsername);
            logger.info("Retrieved {} attendance records for student: {}", attendanceList.size(), studentUsername);
            LoggerUtil.logDatabaseOperation(logger, "SELECT_BY_STUDENT", "attendance", studentUsername != null ? studentUsername : "null");
            return attendanceList;
        } catch (Exception e) {
            String errorMsg = String.format("Failed to retrieve attendance records for student: %s", studentUsername);
            logger.error(errorMsg, e);
            databaseLoggerService.logError("ATTENDANCE_RETRIEVAL_ERROR", errorMsg, e, studentUsername, 
                String.format("exceptionType=%s", e.getClass().getName()));
            throw e;
        } finally {
            LoggerUtil.clearStudentUsername();
        }
    }
    
    /**
     * Check if student attended a session
     */
    @Transactional(readOnly = true)
    public boolean hasStudentAttended(Long sessionId, String studentUsername) {
        logger.debug("Checking if student attended session: {} - {}", studentUsername, sessionId);
        LoggerUtil.setStudentUsername(studentUsername);
        LoggerUtil.setSessionId(sessionId != null ? sessionId.toString() : null);
        
        try {
            boolean attended = attendanceRepository.existsBySessionIdAndStudentUsername(sessionId, studentUsername);
            logger.debug("Student {} {} attended session {}", 
                studentUsername, attended ? "has" : "has not", sessionId);
            LoggerUtil.logDatabaseOperation(logger, "CHECK_ATTENDANCE", "attendance", 
                studentUsername + " in " + sessionId);
            return attended;
        } finally {
            LoggerUtil.clearStudentUsername();
            LoggerUtil.clearKey("sessionId");
        }
    }
    
    /**
     * Get attendance records by method
     */
    @Transactional(readOnly = true)
    public List<Attendance> getAttendanceByMethod(Attendance.AttendanceMethod method) {
        logger.info("Retrieving attendance records by method: {}", method);
        
        try {
            List<Attendance> attendanceList = attendanceRepository.findByMethod(method);
            logger.info("Retrieved {} attendance records by method: {}", attendanceList.size(), method);
            LoggerUtil.logDatabaseOperation(logger, "SELECT_BY_METHOD", "attendance", method.toString());
            return attendanceList;
        } catch (Exception e) {
            String errorMsg = String.format("Failed to retrieve attendance records by method: %s", method);
            logger.error(errorMsg, e);
            databaseLoggerService.logError("ATTENDANCE_RETRIEVAL_ERROR", errorMsg, e, null, 
                String.format("method=%s,exceptionType=%s", method, e.getClass().getName()));
            throw e;
        }
    }
    
    /**
     * Get attendance records within date range
     */
    @Transactional(readOnly = true)
    public List<Attendance> getAttendanceBetweenDates(LocalDateTime startDate, LocalDateTime endDate) {
        logger.info("Retrieving attendance records between dates: {} and {}", startDate, endDate);
        
        try {
            List<Attendance> attendanceList = attendanceRepository.findAttendanceBetweenDates(startDate, endDate);
            logger.info("Retrieved {} attendance records between dates", attendanceList.size());
            LoggerUtil.logDatabaseOperation(logger, "SELECT_BETWEEN_DATES", "attendance", 
                startDate + " to " + endDate);
            return attendanceList;
        } catch (Exception e) {
            String errorMsg = "Failed to retrieve attendance records between dates";
            logger.error(errorMsg, e);
            databaseLoggerService.logError("ATTENDANCE_RETRIEVAL_ERROR", errorMsg, e, null, 
                String.format("startDate=%s,endDate=%s,exceptionType=%s", startDate, endDate, e.getClass().getName()));
            throw e;
        }
    }
    
    /**
     * Get attendance statistics for a session
     */
    @Transactional(readOnly = true)
    public AttendanceStats getSessionAttendanceStats(Long sessionId) {
        logger.info("Calculating attendance statistics for session: {}", sessionId);
        LoggerUtil.setSessionId(sessionId != null ? sessionId.toString() : null);
        
        try {
            long totalAttendance = attendanceRepository.countBySessionId(sessionId);
            long qrScanCount = attendanceRepository.countByMethod(Attendance.AttendanceMethod.QR_SCAN);
            long manualCount = attendanceRepository.countByMethod(Attendance.AttendanceMethod.MANUAL);
            long proxyCount = attendanceRepository.countByMethod(Attendance.AttendanceMethod.PROXY);
            
            AttendanceStats stats = new AttendanceStats(totalAttendance, qrScanCount, manualCount, proxyCount);
            logger.info("Session {} attendance stats: Total={}, QR={}, Manual={}, Proxy={}", 
                sessionId, totalAttendance, qrScanCount, manualCount, proxyCount);
            
            return stats;
        } catch (Exception e) {
            String errorMsg = String.format("Failed to calculate attendance statistics for session: %s", sessionId);
            logger.error(errorMsg, e);
            databaseLoggerService.logError("ATTENDANCE_STATS_ERROR", errorMsg, e, null, 
                String.format("sessionId=%s,exceptionType=%s", sessionId, e.getClass().getName()));
            throw e;
        } finally {
            LoggerUtil.clearKey("sessionId");
        }
    }
    
    /**
     * Delete attendance record
     */
    public void deleteAttendance(Long attendanceId) {
        logger.info("Deleting attendance record: {}", attendanceId);
        
        try {
            if (!attendanceRepository.existsById(attendanceId)) {
                String errorMsg = "Attendance record not found: " + attendanceId;
                logger.error("Attendance record not found for deletion: {}", attendanceId);
                databaseLoggerService.logError("ATTENDANCE_DELETE_NOT_FOUND", errorMsg, null, null, 
                    String.format("attendanceId=%s", attendanceId));
                throw new IllegalArgumentException(errorMsg);
            }
            
            attendanceRepository.deleteById(attendanceId);
            logger.info("Attendance record deleted successfully: {}", attendanceId);
            LoggerUtil.logAttendanceEvent(logger, "ATTENDANCE_DELETED", (String) null, (String) null, null);
            databaseLoggerService.logAttendance("ATTENDANCE_DELETED", null, null, null);
            
        } catch (IllegalArgumentException e) {
            // Already logged above, just re-throw
            throw e;
        } catch (Exception e) {
            String errorMsg = String.format("Failed to delete attendance record: %s", attendanceId);
            logger.error(errorMsg, e);
            databaseLoggerService.logError("ATTENDANCE_DELETE_ERROR", errorMsg, e, null, 
                String.format("attendanceId=%s,exceptionType=%s", attendanceId, e.getClass().getName()));
            throw e;
        }
    }
    
    /**
     * Inner class for attendance statistics
     */
    public static class AttendanceStats {
        private final long totalAttendance;
        private final long qrScanCount;
        private final long manualCount;
        private final long proxyCount;
        
        public AttendanceStats(long totalAttendance, long qrScanCount, long manualCount, long proxyCount) {
            this.totalAttendance = totalAttendance;
            this.qrScanCount = qrScanCount;
            this.manualCount = manualCount;
            this.proxyCount = proxyCount;
        }
        
        // Getters
        public long getTotalAttendance() { return totalAttendance; }
        public long getQrScanCount() { return qrScanCount; }
        public long getManualCount() { return manualCount; }
        public long getProxyCount() { return proxyCount; }
    }

    private String normalizeUsername(String username) {
        if (username == null) {
            return null;
        }
        String trimmed = username.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }
}
