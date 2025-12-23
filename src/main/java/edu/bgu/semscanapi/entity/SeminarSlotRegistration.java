package edu.bgu.semscanapi.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "slot_registration")
public class SeminarSlotRegistration {

    @EmbeddedId
    private SeminarSlotRegistrationId id;

    @Enumerated(EnumType.STRING)
    @Column(name = "degree")
    private User.Degree degree;

    @Column(name = "topic")
    private String topic;

    @Column(name = "seminar_abstract", columnDefinition = "TEXT")
    private String seminarAbstract;

    @Column(name = "supervisor_name")
    private String supervisorName;

    @Column(name = "supervisor_email")
    private String supervisorEmail;

    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false)
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    @Column(name = "approval_token", unique = true)
    private String approvalToken;

    @Column(name = "approval_token_expires_at")
    private LocalDateTime approvalTokenExpiresAt;

    @Column(name = "supervisor_approved_at")
    private LocalDateTime supervisorApprovedAt;

    @Column(name = "supervisor_declined_at")
    private LocalDateTime supervisorDeclinedAt;

    @Column(name = "supervisor_declined_reason", columnDefinition = "TEXT")
    private String supervisorDeclinedReason;

    @PrePersist
    public void onCreate() {
        if (this.registeredAt == null) {
            this.registeredAt = LocalDateTime.now();
        }
        if (this.approvalStatus == null) {
            this.approvalStatus = ApprovalStatus.PENDING;
        }
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

    public String getSeminarAbstract() {
        return seminarAbstract;
    }

    public void setSeminarAbstract(String seminarAbstract) {
        this.seminarAbstract = seminarAbstract;
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

    public ApprovalStatus getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(ApprovalStatus approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public String getApprovalToken() {
        return approvalToken;
    }

    public void setApprovalToken(String approvalToken) {
        this.approvalToken = approvalToken;
    }

    public LocalDateTime getApprovalTokenExpiresAt() {
        return approvalTokenExpiresAt;
    }

    public void setApprovalTokenExpiresAt(LocalDateTime approvalTokenExpiresAt) {
        this.approvalTokenExpiresAt = approvalTokenExpiresAt;
    }

    public LocalDateTime getSupervisorApprovedAt() {
        return supervisorApprovedAt;
    }

    public void setSupervisorApprovedAt(LocalDateTime supervisorApprovedAt) {
        this.supervisorApprovedAt = supervisorApprovedAt;
    }

    public LocalDateTime getSupervisorDeclinedAt() {
        return supervisorDeclinedAt;
    }

    public void setSupervisorDeclinedAt(LocalDateTime supervisorDeclinedAt) {
        this.supervisorDeclinedAt = supervisorDeclinedAt;
    }

    public String getSupervisorDeclinedReason() {
        return supervisorDeclinedReason;
    }

    public void setSupervisorDeclinedReason(String supervisorDeclinedReason) {
        this.supervisorDeclinedReason = supervisorDeclinedReason;
    }
}


