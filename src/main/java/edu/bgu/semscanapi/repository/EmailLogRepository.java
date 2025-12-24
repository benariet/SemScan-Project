package edu.bgu.semscanapi.repository;

import edu.bgu.semscanapi.entity.EmailLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {

    /**
     * Find all logs for a specific email address
     */
    List<EmailLog> findByToEmailOrderByCreatedAtDesc(String toEmail);

    /**
     * Find all logs for a specific registration
     */
    List<EmailLog> findByRegistrationIdOrderByCreatedAtDesc(Long registrationId);

    /**
     * Find logs by status with pagination (for admin dashboard)
     */
    Page<EmailLog> findByStatusOrderByCreatedAtDesc(EmailLog.Status status, Pageable pageable);

    /**
     * Find recent logs (for admin dashboard)
     */
    Page<EmailLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Count by status (for dashboard stats)
     */
    long countByStatus(EmailLog.Status status);

    /**
     * Count by status and date range
     */
    @Query("SELECT COUNT(e) FROM EmailLog e WHERE e.status = :status AND e.createdAt >= :start AND e.createdAt <= :end")
    long countByStatusAndDateRange(@Param("status") EmailLog.Status status,
                                   @Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end);

    /**
     * Find failed emails in date range
     */
    @Query("SELECT e FROM EmailLog e WHERE e.status = 'FAILED' AND e.createdAt >= :start ORDER BY e.createdAt DESC")
    List<EmailLog> findRecentFailures(@Param("start") LocalDateTime start);

    /**
     * Get email statistics grouped by type
     */
    @Query("SELECT e.emailType, e.status, COUNT(e) FROM EmailLog e WHERE e.createdAt >= :start GROUP BY e.emailType, e.status")
    List<Object[]> getEmailStatsByType(@Param("start") LocalDateTime start);
}
