package edu.bgu.semscanapi.repository;

import edu.bgu.semscanapi.entity.EmailQueue;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EmailQueueRepository extends JpaRepository<EmailQueue, Long> {

    /**
     * Find all pending emails ready to be sent (scheduled time has passed)
     */
    @Query("SELECT e FROM EmailQueue e WHERE e.status = 'PENDING' AND e.scheduledAt <= :now ORDER BY e.scheduledAt ASC")
    List<EmailQueue> findPendingEmailsReadyToSend(@Param("now") LocalDateTime now);

    /**
     * Find pending emails ready to send with limit (for batch processing)
     * Use Pageable for limit: PageRequest.of(0, limit)
     */
    @Query("SELECT e FROM EmailQueue e WHERE e.status = 'PENDING' AND e.scheduledAt <= :now ORDER BY e.scheduledAt ASC")
    List<EmailQueue> findPendingEmailsReadyToSendWithLimit(@Param("now") LocalDateTime now, Pageable pageable);

    /**
     * Find all emails for a specific registration
     */
    List<EmailQueue> findByRegistrationIdOrderByCreatedAtDesc(Long registrationId);

    /**
     * Find all failed emails that can be retried
     */
    @Query("SELECT e FROM EmailQueue e WHERE e.status = 'FAILED' AND e.retryCount < e.maxRetries")
    List<EmailQueue> findRetryableFailedEmails();

    /**
     * Count pending emails
     */
    long countByStatus(EmailQueue.Status status);

    /**
     * Find emails by type and status
     */
    List<EmailQueue> findByEmailTypeAndStatus(EmailQueue.EmailType emailType, EmailQueue.Status status);

    /**
     * Cancel all pending emails for a registration (e.g., when registration is cancelled)
     */
    @Modifying
    @Query("UPDATE EmailQueue e SET e.status = 'CANCELLED' WHERE e.registrationId = :registrationId AND e.status = 'PENDING'")
    int cancelPendingEmailsForRegistration(@Param("registrationId") Long registrationId);

    /**
     * Find stuck processing emails (processing for more than 5 minutes)
     */
    @Query("SELECT e FROM EmailQueue e WHERE e.status = 'PROCESSING' AND e.lastAttemptAt < :cutoff")
    List<EmailQueue> findStuckProcessingEmails(@Param("cutoff") LocalDateTime cutoff);

    /**
     * Reset stuck processing emails back to pending
     */
    @Modifying
    @Query("UPDATE EmailQueue e SET e.status = 'PENDING' WHERE e.status = 'PROCESSING' AND e.lastAttemptAt < :cutoff")
    int resetStuckProcessingEmails(@Param("cutoff") LocalDateTime cutoff);
}
