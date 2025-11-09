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
import java.util.Optional;
import java.util.Locale;

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
            // Validate session exists and is open
            Optional<Session> session = sessionRepository.findById(attendance.getSessionId());
            if (session.isEmpty()) {
                logger.error("Session not found: {}", attendance.getSessionId());
                throw new IllegalArgumentException("Session not found: " + attendance.getSessionId());
            }
            
            Session sessionEntity = session.get();
            logger.debug("Session found: {} - Status: {}, StartTime: {}, EndTime: {}", 
                sessionEntity.getSessionId(), sessionEntity.getStatus(), 
                sessionEntity.getStartTime(), sessionEntity.getEndTime());
            
            if (sessionEntity.getStatus() != Session.SessionStatus.OPEN) {
                logger.error("Session is not open: {} (status: {})", 
                    attendance.getSessionId(), sessionEntity.getStatus());
                throw new IllegalArgumentException("Session is not open: " + attendance.getSessionId() + " (status: " + sessionEntity.getStatus() + ")");
            }
            
            // Validate student exists (use case-insensitive lookup)
            Optional<User> student = userRepository.findByBguUsernameIgnoreCase(attendance.getStudentUsername());
            if (student.isEmpty()) {
                logger.error("Student not found: {}", attendance.getStudentUsername());
                throw new IllegalArgumentException("Student not found: " + attendance.getStudentUsername());
            }
            
            logger.debug("Student found: {} - isParticipant: {}", 
                student.get().getBguUsername(), student.get().getIsParticipant());
            
            if (!Boolean.TRUE.equals(student.get().getIsParticipant())) {
                logger.error("User is not marked as participant: {}", attendance.getStudentUsername());
                throw new IllegalArgumentException("User is not a participant: " + attendance.getStudentUsername());
            }
            
            // Check if already attended (use case-insensitive check)
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
                    logger.warn("Student already attended session: {} - {} (Attendance ID: {}, Username in DB: {})", 
                        normalizedUsername, attendance.getSessionId(), 
                        existingAttendance.get().getAttendanceId(),
                        existingAttendance.get().getStudentUsername());
                    throw new IllegalArgumentException("Student already attended this session");
                }
            }
            
            // Set attendance time if not provided
            if (attendance.getAttendanceTime() == null) {
                attendance.setAttendanceTime(LocalDateTime.now());
                logger.debug("Set attendance time to current time");
            }
            
            // Set default method if not provided
            if (attendance.getMethod() == null) {
                attendance.setMethod(Attendance.AttendanceMethod.QR_SCAN);
                logger.debug("Set default attendance method: QR_SCAN");
            }
            
            // Keep string IDs for API consistency - database will handle conversion
            // The database triggers will generate the string IDs automatically
            
            Attendance savedAttendance = attendanceRepository.save(attendance);
                    logger.info("Attendance recorded successfully: {} for student: {} in session: {}",
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
            
        } catch (Exception e) {
            logger.error("Failed to record attendance for student: {} in session: {}", 
                attendance.getStudentUsername(), attendance.getSessionId(), e);
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
            List<Attendance> attendanceList = attendanceRepository.findBySessionId(sessionId);
            logger.info("Retrieved {} attendance records for session: {}", attendanceList.size(), sessionId);
            LoggerUtil.logDatabaseOperation(logger, "SELECT_BY_SESSION", "attendance", sessionId != null ? sessionId.toString() : "null");
            return attendanceList;
        } catch (Exception e) {
            logger.error("Failed to retrieve attendance records for session: {}", sessionId, e);
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
            logger.error("Failed to retrieve attendance records for student: {}", studentUsername, e);
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
            logger.error("Failed to retrieve attendance records by method: {}", method, e);
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
            logger.error("Failed to retrieve attendance records between dates", e);
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
            logger.error("Failed to calculate attendance statistics for session: {}", sessionId, e);
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
                logger.error("Attendance record not found for deletion: {}", attendanceId);
                throw new IllegalArgumentException("Attendance record not found: " + attendanceId);
            }
            
            attendanceRepository.deleteById(attendanceId);
            logger.info("Attendance record deleted successfully: {}", attendanceId);
            LoggerUtil.logAttendanceEvent(logger, "ATTENDANCE_DELETED", (String) null, (String) null, null);
            
        } catch (Exception e) {
            logger.error("Failed to delete attendance record: {}", attendanceId, e);
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
