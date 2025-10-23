package edu.bgu.semscanapi.repository;

import edu.bgu.semscanapi.entity.Session;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Session entity operations
 * Provides data access methods with comprehensive logging
 */
@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {
    
    Logger logger = LoggerUtil.getLogger(SessionRepository.class);
    
    /**
     * Find sessions by seminar ID
     */
    List<Session> findBySeminarId(String seminarId);
    
    /**
     * Find session by session ID (string ID)
     */
    Optional<Session> findBySessionId(String sessionId);
    
    /**
     * Find sessions by status
     */
    List<Session> findByStatus(Session.SessionStatus status);
    
    /**
     * Find open sessions
     */
    @Query("SELECT s FROM Session s WHERE s.status = 'OPEN'")
    List<Session> findOpenSessions();
    
    /**
     * Find closed sessions
     */
    @Query("SELECT s FROM Session s WHERE s.status = 'CLOSED'")
    List<Session> findClosedSessions();
    
    /**
     * Find sessions by seminar ID and status
     */
    List<Session> findBySeminarIdAndStatus(String seminarId, Session.SessionStatus status);
    
    /**
     * Find sessions within date range
     */
    @Query("SELECT s FROM Session s WHERE s.startTime BETWEEN :startDate AND :endDate")
    List<Session> findSessionsBetweenDates(@Param("startDate") LocalDateTime startDate, 
                                         @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find sessions by seminar ID within date range
     */
    @Query("SELECT s FROM Session s WHERE s.seminarId = :seminarId AND s.startTime BETWEEN :startDate AND :endDate")
    List<Session> findSessionsBySeminarBetweenDates(@Param("seminarId") String seminarId,
                                                   @Param("startDate") LocalDateTime startDate, 
                                                   @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find active sessions for a seminar
     */
    @Query("SELECT s FROM Session s WHERE s.seminarId = :seminarId AND s.status = 'OPEN'")
    List<Session> findActiveSessionsBySeminar(@Param("seminarId") String seminarId);
    
    /**
     * Count sessions by seminar
     */
    long countBySeminarId(String seminarId);
    
    /**
     * Count sessions by status
     */
    long countByStatus(Session.SessionStatus status);
    
    /**
     * Find sessions starting after a specific time
     */
    @Query("SELECT s FROM Session s WHERE s.startTime > :startTime")
    List<Session> findSessionsStartingAfter(@Param("startTime") LocalDateTime startTime);
}
