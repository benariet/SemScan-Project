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
import java.util.UUID;

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
    private UserRepository userRepository;
    
    /**
     * Record attendance for a student in a session
     */
    public Attendance recordAttendance(Attendance attendance) {
        logger.info("Recording attendance for student: {} in session: {}", 
            attendance.getStudentId(), attendance.getSessionId());
        LoggerUtil.setStudentId(attendance.getStudentId());
        LoggerUtil.setSessionId(attendance.getSessionId());
        
        try {
            // Validate session exists and is open
            Optional<Session> session = sessionRepository.findById(attendance.getSessionId());
            if (session.isEmpty()) {
                logger.error("Session not found: {}", attendance.getSessionId());
                throw new IllegalArgumentException("Session not found: " + attendance.getSessionId());
            }
            
            if (session.get().getStatus() != Session.SessionStatus.OPEN) {
                logger.error("Session is not open: {} (status: {})", 
                    attendance.getSessionId(), session.get().getStatus());
                throw new IllegalArgumentException("Session is not open: " + attendance.getSessionId());
            }
            
            // Validate student exists
            Optional<User> student = userRepository.findById(attendance.getStudentId());
            if (student.isEmpty()) {
                logger.error("Student not found: {}", attendance.getStudentId());
                throw new IllegalArgumentException("Student not found: " + attendance.getStudentId());
            }
            
            if (student.get().getRole() != User.UserRole.STUDENT) {
                logger.error("User is not a student: {}", attendance.getStudentId());
                throw new IllegalArgumentException("User is not a student: " + attendance.getStudentId());
            }
            
            // Check if already attended
            if (attendanceRepository.existsBySessionIdAndStudentId(
                attendance.getSessionId(), attendance.getStudentId())) {
                logger.warn("Student already attended session: {} - {}", 
                    attendance.getStudentId(), attendance.getSessionId());
                throw new IllegalArgumentException("Student already attended this session");
            }
            
            // Generate ID if not provided
            if (attendance.getAttendanceId() == null || attendance.getAttendanceId().isEmpty()) {
                attendance.setAttendanceId("attendance-" + UUID.randomUUID().toString().substring(0, 8));
                logger.debug("Generated attendance ID: {}", attendance.getAttendanceId());
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
            
            Attendance savedAttendance = attendanceRepository.save(attendance);
            logger.info("Attendance recorded successfully: {} for student: {} in session: {}", 
                savedAttendance.getAttendanceId(), savedAttendance.getStudentId(), savedAttendance.getSessionId());
            
            // Log to session-specific log file
            SessionLoggerUtil.logAttendance(savedAttendance.getSessionId(), 
                                          savedAttendance.getStudentId(), 
                                          savedAttendance.getMethod().toString(),
                                          savedAttendance.getAttendanceTime().toString());
            
            LoggerUtil.logAttendanceEvent(logger, "ATTENDANCE_RECORDED", 
                savedAttendance.getStudentId(), savedAttendance.getSessionId(), 
                savedAttendance.getMethod().toString());
            
            return savedAttendance;
            
        } catch (Exception e) {
            logger.error("Failed to record attendance for student: {} in session: {}", 
                attendance.getStudentId(), attendance.getSessionId(), e);
            throw e;
        } finally {
            LoggerUtil.clearKey("studentId");
            LoggerUtil.clearKey("sessionId");
        }
    }
    
    /**
     * Get attendance by ID
     */
    @Transactional(readOnly = true)
    public Optional<Attendance> getAttendanceById(String attendanceId) {
        logger.debug("Retrieving attendance by ID: {}", attendanceId);
        
        try {
            Optional<Attendance> attendance = attendanceRepository.findById(attendanceId);
            if (attendance.isPresent()) {
                logger.debug("Attendance found: {} for student: {} in session: {}", 
                    attendanceId, attendance.get().getStudentId(), attendance.get().getSessionId());
                LoggerUtil.setStudentId(attendance.get().getStudentId());
                LoggerUtil.setSessionId(attendance.get().getSessionId());
                LoggerUtil.logDatabaseOperation(logger, "SELECT", "attendance", attendanceId);
            } else {
                logger.warn("Attendance not found: {}", attendanceId);
            }
            return attendance;
        } finally {
            LoggerUtil.clearKey("studentId");
            LoggerUtil.clearKey("sessionId");
        }
    }
    
    /**
     * Get attendance records by session
     */
    @Transactional(readOnly = true)
    public List<Attendance> getAttendanceBySession(String sessionId) {
        logger.info("Retrieving attendance records for session: {}", sessionId);
        LoggerUtil.setSessionId(sessionId);
        
        try {
            List<Attendance> attendanceList = attendanceRepository.findBySessionId(sessionId);
            logger.info("Retrieved {} attendance records for session: {}", attendanceList.size(), sessionId);
            LoggerUtil.logDatabaseOperation(logger, "SELECT_BY_SESSION", "attendance", sessionId);
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
    public List<Attendance> getAttendanceByStudent(String studentId) {
        logger.info("Retrieving attendance records for student: {}", studentId);
        LoggerUtil.setStudentId(studentId);
        
        try {
            List<Attendance> attendanceList = attendanceRepository.findByStudentId(studentId);
            logger.info("Retrieved {} attendance records for student: {}", attendanceList.size(), studentId);
            LoggerUtil.logDatabaseOperation(logger, "SELECT_BY_STUDENT", "attendance", studentId);
            return attendanceList;
        } catch (Exception e) {
            logger.error("Failed to retrieve attendance records for student: {}", studentId, e);
            throw e;
        } finally {
            LoggerUtil.clearKey("studentId");
        }
    }
    
    /**
     * Check if student attended a session
     */
    @Transactional(readOnly = true)
    public boolean hasStudentAttended(String sessionId, String studentId) {
        logger.debug("Checking if student attended session: {} - {}", studentId, sessionId);
        LoggerUtil.setStudentId(studentId);
        LoggerUtil.setSessionId(sessionId);
        
        try {
            boolean attended = attendanceRepository.existsBySessionIdAndStudentId(sessionId, studentId);
            logger.debug("Student {} {} attended session {}", 
                studentId, attended ? "has" : "has not", sessionId);
            LoggerUtil.logDatabaseOperation(logger, "CHECK_ATTENDANCE", "attendance", 
                studentId + " in " + sessionId);
            return attended;
        } finally {
            LoggerUtil.clearKey("studentId");
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
    public AttendanceStats getSessionAttendanceStats(String sessionId) {
        logger.info("Calculating attendance statistics for session: {}", sessionId);
        LoggerUtil.setSessionId(sessionId);
        
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
    public void deleteAttendance(String attendanceId) {
        logger.info("Deleting attendance record: {}", attendanceId);
        
        try {
            if (!attendanceRepository.existsById(attendanceId)) {
                logger.error("Attendance record not found for deletion: {}", attendanceId);
                throw new IllegalArgumentException("Attendance record not found: " + attendanceId);
            }
            
            attendanceRepository.deleteById(attendanceId);
            logger.info("Attendance record deleted successfully: {}", attendanceId);
            LoggerUtil.logAttendanceEvent(logger, "ATTENDANCE_DELETED", null, null, null);
            
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
}
