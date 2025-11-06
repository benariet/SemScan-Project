package edu.bgu.semscanapi.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "seminar_slot_registration")
public class SeminarSlotRegistration {

    @EmbeddedId
    private SeminarSlotRegistrationId id;

    @Enumerated(EnumType.STRING)
    @Column(name = "degree")
    private User.Degree degree;

    @Column(name = "topic")
    private String topic;

    @Column(name = "supervisor_name")
    private String supervisorName;

    @Column(name = "supervisor_email")
    private String supervisorEmail;

    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    @PrePersist
    public void onCreate() {
        this.registeredAt = LocalDateTime.now();
    }

    public SeminarSlotRegistrationId getId() {
        return id;
    }

    public void setId(SeminarSlotRegistrationId id) {
        this.id = id;
    }

    public User.Degree getDegree() {
        return degree;
    }

    public void setDegree(User.Degree degree) {
        this.degree = degree;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getSupervisorName() {
        return supervisorName;
    }

    public void setSupervisorName(String supervisorName) {
        this.supervisorName = supervisorName;
    }

    public String getSupervisorEmail() {
        return supervisorEmail;
    }

    public void setSupervisorEmail(String supervisorEmail) {
        this.supervisorEmail = supervisorEmail;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(LocalDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }

    public Long getSlotId() {
        return id != null ? id.getSlotId() : null;
    }

    public String getPresenterUsername() {
        return id != null ? id.getPresenterUsername() : null;
    }
}


