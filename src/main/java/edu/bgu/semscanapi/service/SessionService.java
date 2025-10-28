package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.entity.Seminar;
import edu.bgu.semscanapi.entity.Session;
import edu.bgu.semscanapi.repository.SeminarRepository;
import edu.bgu.semscanapi.repository.SessionRepository;
import edu.bgu.semscanapi.util.LoggerUtil;
import edu.bgu.semscanapi.util.SessionLoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service class for Session business logic
 * Provides comprehensive logging for all operations
 */
@Service
@Transactional
public class SessionService {
    
    private static final Logger logger = LoggerUtil.getLogger(SessionService.class);
    
    @Autowired
    private SessionRepository sessionRepository;
    
    @Autowired
    private SeminarRepository seminarRepository;
    
    /**
     * Create a new session
     */
    public Session createSession(Session session) {
        logger.info("Creating new session for seminar: {}", session.getSeminarId());
        LoggerUtil.setSessionId(session.getSessionId() != null ? session.getSessionId().toString() : null);
        LoggerUtil.setSeminarId(session.getSeminarId() != null ? session.getSeminarId().toString() : null);
        
        try {
            // Validate seminar exists
            Optional<Seminar> seminar = seminarRepository.findById(session.getSeminarId());
            if (seminar.isEmpty()) {
                logger.error("Seminar not found: {}", session.getSeminarId());
                throw new IllegalArgumentException("Seminar not found: " + session.getSeminarId());
            }
            
            // Set default status if not provided
            if (session.getStatus() == null) {
                session.setStatus(Session.SessionStatus.OPEN);
                logger.debug("Set default session status: OPEN");
            }
            
            // Validate start time
            if (session.getStartTime() == null) {
                session.setStartTime(LocalDateTime.now());
                logger.debug("Set session start time to current time");
            }
            
            Session savedSession = sessionRepository.save(session);
            logger.info("Session created successfully: {} for seminar: {}", 
                savedSession.getSessionId(), savedSession.getSeminarId());
            
            // Log to session-specific log file
            SessionLoggerUtil.logSessionCreated(
                savedSession.getSessionId(),
                savedSession.getSeminarId(),
                seminar.get().getPresenterId()
            );
            
            LoggerUtil.logSessionEvent(logger, "SESSION_CREATED", 
                savedSession.getSessionId().toString(),
                savedSession.getSeminarId().toString(),
                seminar.get().getPresenterId().toString());
            
            return savedSession;
            
        } catch (Exception e) {
            logger.error("Failed to create session for seminar: {}", session.getSeminarId(), e);
            if (session.getSessionId() != null) {
                SessionLoggerUtil.logSessionError(session.getSessionId(), 
                    "Failed to create session: " + e.getMessage(), e);
            }
            throw e;
        } finally {
            LoggerUtil.clearKey("sessionId");
            LoggerUtil.clearKey("seminarId");
        }
    }
    
    /**
     * Get session by ID
     */
    @Transactional(readOnly = true)
    public Optional<Session> getSessionById(Long sessionId) {
        logger.debug("Retrieving session by ID: {}", sessionId);
        LoggerUtil.setSessionId(sessionId != null ? sessionId.toString() : null);
        
        try {
            Optional<Session> session = sessionRepository.findById(sessionId);
            if (session.isPresent()) {
                logger.debug("Session found: {} for seminar: {}", sessionId, session.get().getSeminarId());
                LoggerUtil.setSeminarId(session.get().getSeminarId().toString());
                LoggerUtil.logDatabaseOperation(logger, "SELECT", "sessions", sessionId != null ? sessionId.toString() : "null");
            } else {
                logger.warn("Session not found: {}", sessionId);
            }
            return session;
        } finally {
            LoggerUtil.clearKey("sessionId");
            LoggerUtil.clearKey("seminarId");
        }
    }
    
    /**
     * Get sessions by seminar
     */
    @Transactional(readOnly = true)
    public List<Session> getSessionsBySeminar(Long seminarId) {
        logger.info("Retrieving sessions for seminar: {}", seminarId);
        LoggerUtil.setSeminarId(seminarId != null ? seminarId.toString() : null);
        
        try {
            List<Session> sessions = sessionRepository.findBySeminarId(seminarId);
            logger.info("Retrieved {} sessions for seminar: {}", sessions.size(), seminarId);
            LoggerUtil.logDatabaseOperation(logger, "SELECT_BY_SEMINAR", "sessions", seminarId != null ? seminarId.toString() : "null");
            return sessions;
        } catch (Exception e) {
            logger.error("Failed to retrieve sessions for seminar: {}", seminarId, e);
            throw e;
        } finally {
            LoggerUtil.clearKey("seminarId");
        }
    }
    
    /**
     * Get open sessions
     */
    @Transactional(readOnly = true)
    public List<Session> getOpenSessions() {
        logger.info("Retrieving all open sessions");
        
        try {
            List<Session> sessions = sessionRepository.findOpenSessions();
            logger.info("Retrieved {} open sessions", sessions.size());
            LoggerUtil.logDatabaseOperation(logger, "SELECT_OPEN", "sessions", "all");
            return sessions;
        } catch (Exception e) {
            logger.error("Failed to retrieve open sessions", e);
            throw e;
        }
    }
    
    /**
     * Get closed sessions
     */
    @Transactional(readOnly = true)
    public List<Session> getClosedSessions() {
        logger.info("Retrieving all closed sessions");
        
        try {
            List<Session> sessions = sessionRepository.findClosedSessions();
            logger.info("Retrieved {} closed sessions", sessions.size());
            LoggerUtil.logDatabaseOperation(logger, "SELECT_CLOSED", "sessions", "all");
            return sessions;
        } catch (Exception e) {
            logger.error("Failed to retrieve closed sessions", e);
            throw e;
        }
    }
    
    /**
     * Update session status
     */
    public Session updateSessionStatus(Long sessionId, Session.SessionStatus status) {
        logger.info("Updating session status: {} to {}", sessionId, status);
        LoggerUtil.setSessionId(sessionId != null ? sessionId.toString() : null);
        
        try {
            Optional<Session> existingSession = sessionRepository.findById(sessionId);
            if (existingSession.isEmpty()) {
                logger.error("Session not found for status update: {}", sessionId);
                throw new IllegalArgumentException("Session not found: " + sessionId);
            }
            
            Session session = existingSession.get();
            Session.SessionStatus oldStatus = session.getStatus();
            session.setStatus(status);
            
            if (status == Session.SessionStatus.CLOSED && session.getEndTime() == null) {
                session.setEndTime(LocalDateTime.now());
                logger.debug("Set session end time to current time");
            }
            
            Session savedSession = sessionRepository.save(session);
            logger.info("Session status updated: {} from {} to {}", sessionId, oldStatus, status);
            
            SessionLoggerUtil.logSessionStatusChange(
                sessionId,
                oldStatus.toString(),
                status.toString()
            );
            
            LoggerUtil.logSessionEvent(logger, "SESSION_STATUS_UPDATED", 
                sessionId.toString(),
                savedSession.getSeminarId().toString(),
                null);
            
            return savedSession;
        } catch (Exception e) {
            logger.error("Failed to update session status: {}", sessionId, e);
            throw e;
        } finally {
            LoggerUtil.clearKey("sessionId");
        }
    }
    
    /**
     * Close session
     */
    public Session closeSession(Long sessionId) {
        logger.info("Closing session: {}", sessionId);
        return updateSessionStatus(sessionId, Session.SessionStatus.CLOSED);
    }
    
    /**
     * Open session
     */
    public Session openSession(Long sessionId) {
        logger.info("Opening session: {}", sessionId);
        return updateSessionStatus(sessionId, Session.SessionStatus.OPEN);
    }
    
    /**
     * Delete session
     */
    public void deleteSession(Long sessionId) {
        logger.info("Deleting session: {}", sessionId);
        LoggerUtil.setSessionId(sessionId != null ? sessionId.toString() : null);
        
        try {
            if (!sessionRepository.existsById(sessionId)) {
                logger.error("Session not found for deletion: {}", sessionId);
                throw new IllegalArgumentException("Session not found: " + sessionId);
            }
            
            sessionRepository.deleteById(sessionId);
            logger.info("Session deleted successfully: {}", sessionId);
            LoggerUtil.logSessionEvent(logger, "SESSION_DELETED", sessionId.toString(), null, null);
        } catch (Exception e) {
            logger.error("Failed to delete session: {}", sessionId, e);
            throw e;
        } finally {
            LoggerUtil.clearKey("sessionId");
        }
    }
    
    /**
     * Get sessions within date range
     */
    @Transactional(readOnly = true)
    public List<Session> getSessionsBetweenDates(LocalDateTime startDate, LocalDateTime endDate) {
        logger.info("Retrieving sessions between dates: {} and {}", startDate, endDate);
        
        try {
            List<Session> sessions = sessionRepository.findSessionsBetweenDates(startDate, endDate);
            logger.info("Retrieved {} sessions between dates", sessions.size());
            LoggerUtil.logDatabaseOperation(logger, "SELECT_BETWEEN_DATES", "sessions", 
                startDate + " to " + endDate);
            return sessions;
        } catch (Exception e) {
            logger.error("Failed to retrieve sessions between dates", e);
            throw e;
        }
    }
    
    /**
     * Get active sessions for a seminar
     */
    @Transactional(readOnly = true)
    public List<Session> getActiveSessionsBySeminar(Long seminarId) {
        logger.info("Retrieving active sessions for seminar: {}", seminarId);
        LoggerUtil.setSeminarId(seminarId != null ? seminarId.toString() : null);
        
        try {
            List<Session> sessions = sessionRepository.findActiveSessionsBySeminar(seminarId);
            logger.info("Retrieved {} active sessions for seminar: {}", sessions.size(), seminarId);
            LoggerUtil.logDatabaseOperation(logger, "SELECT_ACTIVE_BY_SEMINAR", "sessions", seminarId != null ? seminarId.toString() : "null");
            return sessions;
        } catch (Exception e) {
            logger.error("Failed to retrieve active sessions for seminar: {}", seminarId, e);
            throw e;
        } finally {
            LoggerUtil.clearKey("seminarId");
        }
    }
}
