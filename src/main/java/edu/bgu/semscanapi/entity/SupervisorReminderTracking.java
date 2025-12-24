package edu.bgu.semscanapi.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity to track daily supervisor reminders.
 * Prevents sending duplicate reminders on the same day.
 */
@Entity
@Table(name = "supervisor_reminder_tracking",
       uniqueConstraints = @UniqueConstraint(columnNames = {"registration_id", "reminder_date"}))
public class SupervisorReminderTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "registration_id", nullable = false)
    private Long registrationId;

    @Column(name = "supervisor_email", nullable = false)
    private String supervisorEmail;

    @Column(name = "reminder_date", nullable = false)
    private LocalDate reminderDate;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @PrePersist
    public void prePersist() {
        if (sentAt == null) {
            sentAt = LocalDateTime.now();
        }
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRegistrationId() {
        return registrationId;
    }

    public void setRegistrationId(Long registrationId) {
        this.registrationId = registrationId;
    }

    public String getSupervisorEmail() {
        return supervisorEmail;
    }

    public void setSupervisorEmail(String supervisorEmail) {
        this.supervisorEmail = supervisorEmail;
    }

    public LocalDate getReminderDate() {
        return reminderDate;
    }

    public void setReminderDate(LocalDate reminderDate) {
        this.reminderDate = reminderDate;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }
}
