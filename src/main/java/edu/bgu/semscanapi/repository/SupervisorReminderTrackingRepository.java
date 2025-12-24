package edu.bgu.semscanapi.repository;

import edu.bgu.semscanapi.entity.SupervisorReminderTracking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface SupervisorReminderTrackingRepository extends JpaRepository<SupervisorReminderTracking, Long> {

    /**
     * Check if reminder was already sent today for this registration
     */
    boolean existsByRegistrationIdAndReminderDate(Long registrationId, LocalDate reminderDate);

    /**
     * Find reminder tracking record
     */
    Optional<SupervisorReminderTracking> findByRegistrationIdAndReminderDate(Long registrationId, LocalDate reminderDate);

    /**
     * Count reminders sent for a registration
     */
    long countByRegistrationId(Long registrationId);
}
