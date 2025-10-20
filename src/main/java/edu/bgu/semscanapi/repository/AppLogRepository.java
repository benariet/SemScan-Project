package edu.bgu.semscanapi.repository;

import edu.bgu.semscanapi.entity.AppLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository interface for AppLog entity
 * Provides database operations for mobile app logs
 */
@Repository
public interface AppLogRepository extends JpaRepository<AppLog, Long> {
    
    // Find logs by level
    List<AppLog> findByLevel(String level);
    
    // Find logs by tag
    List<AppLog> findByTag(String tag);
    
    // Find logs by user ID
    List<AppLog> findByUserId(String userId);
    
    // Find logs by user role
    List<AppLog> findByUserRole(String userRole);
    
    // Find logs by date range
    @Query("SELECT l FROM AppLog l WHERE l.timestamp BETWEEN :startTime AND :endTime ORDER BY l.timestamp DESC")
    List<AppLog> findByTimestampRange(@Param("startTime") Long startTime, @Param("endTime") Long endTime);
    
    // Find error logs
    @Query("SELECT l FROM AppLog l WHERE l.level = 'ERROR' ORDER BY l.timestamp DESC")
    List<AppLog> findErrorLogs();
    
    // Find logs by level and tag
    List<AppLog> findByLevelAndTag(String level, String tag);
    
    // Find logs by user ID and level
    List<AppLog> findByUserIdAndLevel(String userId, String level);
    
    // Count logs by level
    @Query("SELECT l.level, COUNT(l) FROM AppLog l GROUP BY l.level")
    List<Object[]> countLogsByLevel();
    
    // Count logs by tag
    @Query("SELECT l.tag, COUNT(l) FROM AppLog l GROUP BY l.tag ORDER BY COUNT(l) DESC")
    List<Object[]> countLogsByTag();
    
    // Find recent logs
    @Query(value = "SELECT * FROM app_logs ORDER BY timestamp DESC LIMIT :limit", nativeQuery = true)
    List<AppLog> findRecentLogs(@Param("limit") int limit);
    
    // Find logs by app version
    List<AppLog> findByAppVersion(String appVersion);
    
    // Find logs by device info containing text
    @Query("SELECT l FROM AppLog l WHERE l.deviceInfo LIKE %:deviceInfo%")
    List<AppLog> findByDeviceInfoContaining(@Param("deviceInfo") String deviceInfo);
    
    // Find logs with exceptions
    @Query("SELECT l FROM AppLog l WHERE l.exceptionType IS NOT NULL ORDER BY l.timestamp DESC")
    List<AppLog> findLogsWithExceptions();
    
    // Count total logs
    @Query("SELECT COUNT(l) FROM AppLog l")
    long countTotalLogs();
    
    // Count logs by level
    @Query("SELECT COUNT(l) FROM AppLog l WHERE l.level = :level")
    long countByLevel(@Param("level") String level);
    
    // Find logs created after a specific time
    List<AppLog> findByCreatedAtAfter(LocalDateTime dateTime);
    
    // Find logs created between dates
    List<AppLog> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    // Delete old logs (for cleanup)
    @Query("DELETE FROM AppLog l WHERE l.createdAt < :cutoffDate")
    int deleteOldLogs(@Param("cutoffDate") LocalDateTime cutoffDate);
}
