package edu.bgu.semscanapi.repository;

import edu.bgu.semscanapi.entity.AppLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AppLogRepository extends JpaRepository<AppLog, Long> {

    List<AppLog> findByLevel(String level);

    List<AppLog> findByTag(String tag);

    @Query("SELECT l FROM AppLog l WHERE l.logTimestamp BETWEEN :start AND :end ORDER BY l.logTimestamp DESC")
    List<AppLog> findByLogTimeRange(@Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end);

    @Query("SELECT l FROM AppLog l WHERE l.level = 'ERROR' ORDER BY l.logTimestamp DESC")
    List<AppLog> findErrorLogs();

    List<AppLog> findByLevelAndTag(String level, String tag);

    List<AppLog> findByUserRole(AppLog.UserRole userRole);

    @Query("SELECT l.level, COUNT(l) FROM AppLog l GROUP BY l.level")
    List<Object[]> countLogsByLevel();

    @Query("SELECT l.tag, COUNT(l) FROM AppLog l GROUP BY l.tag ORDER BY COUNT(l) DESC")
    List<Object[]> countLogsByTag();

    @Query(value = "SELECT * FROM app_logs ORDER BY log_timestamp DESC LIMIT :limit", nativeQuery = true)
    List<AppLog> findRecentLogs(@Param("limit") int limit);

    List<AppLog> findByLogTimestampAfter(LocalDateTime dateTime);

    List<AppLog> findByLogTimestampBetween(LocalDateTime startDate, LocalDateTime endDate);

    @Query("SELECT l FROM AppLog l WHERE l.stackTrace IS NOT NULL AND l.stackTrace <> ''")
    List<AppLog> findLogsWithExceptions();

    @Query("SELECT COUNT(l) FROM AppLog l")
    long countTotalLogs();

    long countByLevel(String level);

    @Query("DELETE FROM AppLog l WHERE l.logTimestamp < :cutoff")
    int deleteOldLogs(@Param("cutoff") LocalDateTime cutoffDate);

    List<AppLog> findByUserUsername(String userUsername);

    List<AppLog> findByUserUsernameAndLevel(String userUsername, String level);
}
