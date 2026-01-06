package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.dto.SessionDTO;
import edu.bgu.semscanapi.entity.Seminar;
import edu.bgu.semscanapi.entity.Session;
import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.repository.SeminarRepository;
import edu.bgu.semscanapi.repository.SessionRepository;
import edu.bgu.semscanapi.repository.UserRepository;
import edu.bgu.semscanapi.util.LoggerUtil;
import edu.bgu.semscanapi.util.SessionLoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
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
    private static final ZoneId ISRAEL_TIMEZONE = ZoneId.of("Asia/Jerusalem");
    
    /**
     * Get current time in Israel timezone to match session times
     */
    private LocalDateTime nowIsrael() {
        return ZonedDateTime.now(ISRAEL_TIMEZONE).toLocalDateTime();
    }
    
    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SeminarRepository seminarRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DatabaseLoggerService databaseLoggerService;

    @Autowired(required = false)
    private AppConfigService appConfigService;

    /**
     * Create a new session
     */
    public Session createSession(Session session) {
        logger.info("Creating new session for seminar: {}", session.getSeminarId());
        LoggerUtil.setSessionId(session.getSessionId() != null ? session.getSessionId().toString() : null);
        LoggerUtil.setSeminarId(session.getSeminarId() != null ? session.getSeminarId().toString() : null);
        
        try {
            // Verify the seminar referenced by session exists in database before creating session
            Optional<Seminar> seminar = seminarRepository.findById(session.getSeminarId());
            if (seminar.isEmpty()) {
                String errorMsg = "Seminar not found: " + session.getSeminarId();
                logger.error(errorMsg);
                databaseLoggerService.logError("SESSION_SEMINAR_NOT_FOUND", errorMsg, null, null, 
                    String.format("seminarId=%s", session.getSeminarId()));
                throw new IllegalArgumentException(errorMsg);
            }
            
            // Default session status to OPEN if not specified in request
            if (session.getStatus() == null) {
                session.setStatus(Session.SessionStatus.OPEN);
                logger.debug("Set default session status: OPEN");
            }
            
            // Require start time: sessions must have a start time to determine attendance window
            if (session.getStartTime() == null) {
                // CRITICAL: Use Israel timezone to match slot times
                session.setStartTime(nowIsrael());
                logger.debug("Set session start time to current time (Israel timezone)");
            }
            
            Session savedSession = sessionRepository.save(session);
            logger.info("Session created successfully: {} for seminar: {}", 
                savedSession.getSessionId(), savedSession.getSeminarId());
            
            String presenterUsername = seminar.get().getPresenterUsername();
            
            SessionLoggerUtil.logSessionCreated(
                savedSession.getSessionId(),
                savedSession.getSeminarId(),
                presenterUsername
            );
            
            LoggerUtil.logSessionEvent(logger, "SESSION_CREATED", 
                savedSession.getSessionId().toString(),
                savedSession.getSeminarId().toString(),
                presenterUsername);
            
            databaseLoggerService.logSessionEvent("SESSION_CREATED", 
                savedSession.getSessionId(), savedSession.getSeminarId(), presenterUsername);
            
            return savedSession;
            
        } catch (IllegalArgumentException e) {
            // Already logged above, just re-throw
            throw e;
        } catch (Exception e) {
            String errorMsg = String.format("Failed to create session for seminar: %s", session.getSeminarId());
            logger.error(errorMsg, e);
            databaseLoggerService.logError("SESSION_CREATION_ERROR", errorMsg, e, null, 
                String.format("seminarId=%s,exceptionType=%s", session.getSeminarId(), e.getClass().getName()));
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
            String errorMsg = String.format("Failed to retrieve sessions for seminar: %s", seminarId);
            logger.error(errorMsg, e);
            databaseLoggerService.logError("SESSION_RETRIEVAL_ERROR", errorMsg, e, null, 
                String.format("seminarId=%s,exceptionType=%s", seminarId, e.getClass().getName()));
            throw e;
        } finally {
            LoggerUtil.clearKey("seminarId");
        }
    }
    
    /**
     * Get open sessions
     * Returns ALL open sessions ordered by most recent first (startTime DESC, createdAt DESC)
     * This ensures participants can see newly opened sessions even when multiple presenters
     * use the same time slot.
     *
     * CRITICAL: Also auto-closes any expired sessions before returning.
     * A session is expired if it has been open longer than presenter_close_session_duration_minutes.
     */
    @Transactional
    public List<Session> getOpenSessions() {
        logger.info("Retrieving all open sessions");

        try {
            List<Session> sessions = sessionRepository.findOpenSessions();

            // CRITICAL: Double-check that all returned sessions are actually OPEN
            // This is a safety measure in case the database query has issues
            List<Session> openSessions = sessions.stream()
                .filter(session -> session.getStatus() == Session.SessionStatus.OPEN)
                .toList();

            if (openSessions.size() != sessions.size()) {
                logger.warn("Query returned {} sessions but only {} are actually OPEN. Filtering out CLOSED sessions.",
                    sessions.size(), openSessions.size());
                sessions.stream()
                    .filter(session -> session.getStatus() != Session.SessionStatus.OPEN)
                    .forEach(session -> logger.warn("Filtered out CLOSED session: ID={}, Status={}",
                        session.getSessionId(), session.getStatus()));
            }

            // CRITICAL: Auto-close expired sessions before returning
            // This prevents orphan sessions from appearing in the manual attendance list
            LocalDateTime now = nowIsrael();
            Integer sessionCloseDurationMinutes = appConfigService != null
                    ? appConfigService.getIntegerConfig("presenter_close_session_duration_minutes", 15)
                    : 15;

            List<Session> stillValidSessions = new ArrayList<>();
            for (Session session : openSessions) {
                if (session.getStartTime() != null) {
                    LocalDateTime sessionExpiresAt = session.getStartTime().plusMinutes(sessionCloseDurationMinutes);
                    if (now.isAfter(sessionExpiresAt)) {
                        // Session has expired - auto-close it
                        logger.info("Auto-closing expired session: ID={}, started at {}, expired at {}",
                            session.getSessionId(), session.getStartTime(), sessionExpiresAt);
                        session.setStatus(Session.SessionStatus.CLOSED);
                        session.setEndTime(sessionExpiresAt);
                        sessionRepository.save(session);
                        databaseLoggerService.logAction("INFO", "SESSION_AUTO_CLOSED_EXPIRED",
                            String.format("Session %d auto-closed (was open since %s, expired at %s)",
                                session.getSessionId(), session.getStartTime(), sessionExpiresAt),
                            null, String.format("sessionId=%d,startTime=%s,expiredAt=%s",
                                session.getSessionId(), session.getStartTime(), sessionExpiresAt));
                    } else {
                        stillValidSessions.add(session);
                    }
                } else {
                    // Session has no start time - include it but log warning
                    logger.warn("Session {} has no start time, cannot check expiry", session.getSessionId());
                    stillValidSessions.add(session);
                }
            }

            logger.info("Retrieved {} valid open sessions (auto-closed {} expired sessions)",
                stillValidSessions.size(), openSessions.size() - stillValidSessions.size());
            if (logger.isDebugEnabled()) {
                stillValidSessions.forEach(session -> logger.debug("Open session: ID={}, SeminarID={}, Status={}, StartTime={}, CreatedAt={}",
                    session.getSessionId(), session.getSeminarId(), session.getStatus(),
                    session.getStartTime(), session.getCreatedAt()));
            }
            LoggerUtil.logDatabaseOperation(logger, "SELECT_OPEN", "sessions", "all");
            return stillValidSessions;
        } catch (Exception e) {
            String errorMsg = "Failed to retrieve open sessions";
            logger.error(errorMsg, e);
            databaseLoggerService.logError("SESSION_RETRIEVAL_ERROR", errorMsg, e, null,
                String.format("exceptionType=%s", e.getClass().getName()));
            throw e;
        }
    }

    /**
     * Get open sessions with enriched presenter name and topic.
     * This method fetches open sessions and enriches each with:
     * - presenterName: Full name from User entity (via Seminar.presenterUsername)
     * - topic: From Seminar.description
     * - startTimeEpoch: Start time as epoch milliseconds
     */
    @Transactional
    public List<SessionDTO> getOpenSessionsEnriched() {
        logger.info("Retrieving enriched open sessions");

        List<Session> openSessions = getOpenSessions();
        List<SessionDTO> enrichedSessions = new ArrayList<>();

        for (Session session : openSessions) {
            String presenterName = "Unknown Presenter";
            String presenterUsername = null;
            String topic = null;

            // Look up Seminar to get presenterUsername and topic
            if (session.getSeminarId() != null) {
                Optional<Seminar> seminarOpt = seminarRepository.findById(session.getSeminarId());
                if (seminarOpt.isPresent()) {
                    Seminar seminar = seminarOpt.get();
                    presenterUsername = seminar.getPresenterUsername();
                    topic = seminar.getDescription();

                    // Use seminar name as topic fallback if description is empty
                    if ((topic == null || topic.isEmpty()) && seminar.getSeminarName() != null) {
                        topic = seminar.getSeminarName();
                    }

                    // Look up User to get full name
                    if (presenterUsername != null) {
                        Optional<User> userOpt = userRepository.findByBguUsername(presenterUsername);
                        if (userOpt.isPresent()) {
                            User user = userOpt.get();
                            presenterName = user.getFirstName() + " " + user.getLastName();
                        } else {
                            // Fallback to username if user not found
                            presenterName = presenterUsername;
                        }
                    }
                }
            }

            SessionDTO dto = SessionDTO.fromSession(session, presenterName, presenterUsername, topic);
            enrichedSessions.add(dto);
        }

        logger.info("Retrieved {} enriched open sessions", enrichedSessions.size());
        return enrichedSessions;
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
            String errorMsg = "Failed to retrieve closed sessions";
            logger.error(errorMsg, e);
            databaseLoggerService.logError("SESSION_RETRIEVAL_ERROR", errorMsg, e, null, 
                String.format("exceptionType=%s", e.getClass().getName()));
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
                String errorMsg = "Session not found: " + sessionId;
                logger.error("Session not found for status update: {}", sessionId);
                databaseLoggerService.logError("SESSION_NOT_FOUND", errorMsg, null, null, 
                    String.format("sessionId=%s", sessionId));
                throw new IllegalArgumentException(errorMsg);
            }
            
            Session session = existingSession.get();
            Session.SessionStatus oldStatus = session.getStatus();
            session.setStatus(status);
            
            if (status == Session.SessionStatus.CLOSED && session.getEndTime() == null) {
                // CRITICAL: Use Israel timezone to match session times
                session.setEndTime(nowIsrael());
                logger.debug("Set session end time to current time (Israel timezone)");
            }
            
            Session savedSession = sessionRepository.save(session);
            logger.info("Session status updated: {} from {} to {}", sessionId, oldStatus, status);
            
            SessionLoggerUtil.logSessionStatusChange(
                sessionId,
                oldStatus.toString(),
                status.toString()
            );
            
            LoggerUtil.logSessionEvent(logger, "SESSION_STATUS_UPDATED", 
                sessionId.toString(), existingSession.get().getSeminarId() != null ? existingSession.get().getSeminarId().toString() : null,
                null);
            
            // Log status update to database
            databaseLoggerService.logSessionEvent("SESSION_STATUS_UPDATED", sessionId, 
                existingSession.get().getSeminarId(), null);
            
            return savedSession;
        } catch (IllegalArgumentException e) {
            // Already logged above, just re-throw
            throw e;
        } catch (Exception e) {
            String errorMsg = String.format("Failed to update session status: %s", sessionId);
            logger.error(errorMsg, e);
            databaseLoggerService.logError("SESSION_UPDATE_ERROR", errorMsg, e, null, 
                String.format("sessionId=%s,exceptionType=%s", sessionId, e.getClass().getName()));
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
                String errorMsg = "Session not found: " + sessionId;
                logger.error("Session not found for deletion: {}", sessionId);
                databaseLoggerService.logError("SESSION_DELETE_NOT_FOUND", errorMsg, null, null, 
                    String.format("sessionId=%s", sessionId));
                throw new IllegalArgumentException(errorMsg);
            }
            
            sessionRepository.deleteById(sessionId);
            logger.info("Session deleted successfully: {}", sessionId);
            LoggerUtil.logSessionEvent(logger, "SESSION_DELETED", sessionId.toString(), null, null);
            databaseLoggerService.logSessionEvent("SESSION_DELETED", sessionId, null, null);
        } catch (IllegalArgumentException e) {
            // Already logged above, just re-throw
            throw e;
        } catch (Exception e) {
            String errorMsg = String.format("Failed to delete session: %s", sessionId);
            logger.error(errorMsg, e);
            databaseLoggerService.logError("SESSION_DELETE_ERROR", errorMsg, e, null, 
                String.format("sessionId=%s,exceptionType=%s", sessionId, e.getClass().getName()));
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
            String errorMsg = "Failed to retrieve sessions between dates";
            logger.error(errorMsg, e);
            databaseLoggerService.logError("SESSION_RETRIEVAL_ERROR", errorMsg, e, null, 
                String.format("startDate=%s,endDate=%s,exceptionType=%s", startDate, endDate, e.getClass().getName()));
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
            String errorMsg = String.format("Failed to retrieve active sessions for seminar: %s", seminarId);
            logger.error(errorMsg, e);
            databaseLoggerService.logError("SESSION_RETRIEVAL_ERROR", errorMsg, e, null, 
                String.format("seminarId=%s,exceptionType=%s", seminarId, e.getClass().getName()));
            throw e;
        } finally {
            LoggerUtil.clearKey("seminarId");
        }
    }
}
