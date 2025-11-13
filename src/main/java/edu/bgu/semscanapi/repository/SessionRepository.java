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

@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {

    Logger logger = LoggerUtil.getLogger(SessionRepository.class);

    List<Session> findBySeminarId(Long seminarId);

    List<Session> findByStatus(Session.SessionStatus status);

    @Query("SELECT s FROM Session s WHERE s.status = 'OPEN' ORDER BY s.startTime DESC, s.createdAt DESC")
    List<Session> findOpenSessions();

    @Query("SELECT s FROM Session s WHERE s.status = 'CLOSED'")
    List<Session> findClosedSessions();

    List<Session> findBySeminarIdAndStatus(Long seminarId, Session.SessionStatus status);

    @Query("SELECT s FROM Session s WHERE s.startTime BETWEEN :start AND :end")
    List<Session> findSessionsBetweenDates(@Param("start") LocalDateTime start,
                                           @Param("end") LocalDateTime end);

    @Query("SELECT s FROM Session s WHERE s.seminarId = :seminarId AND s.startTime BETWEEN :start AND :end")
    List<Session> findSessionsBySeminarBetweenDates(@Param("seminarId") Long seminarId,
                                                    @Param("start") LocalDateTime start,
                                                    @Param("end") LocalDateTime end);

    @Query("SELECT s FROM Session s WHERE s.seminarId = :seminarId AND s.status = 'OPEN'")
    List<Session> findActiveSessionsBySeminar(@Param("seminarId") Long seminarId);

    long countBySeminarId(Long seminarId);

    long countByStatus(Session.SessionStatus status);

    @Query("SELECT s FROM Session s WHERE s.startTime > :startTime")
    List<Session> findSessionsStartingAfter(@Param("startTime") LocalDateTime startTime);
}
