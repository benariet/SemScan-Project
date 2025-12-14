package edu.bgu.semscanapi.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "waiting_list")
public class WaitingListEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "waiting_list_id")
    private Long waitingListId;

    @Column(name = "slot_id", nullable = false)
    private Long slotId;

    @Column(name = "presenter_username", nullable = false)
    private String presenterUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "degree", nullable = false)
    private User.Degree degree;

    @Column(name = "topic")
    private String topic;

    @Column(name = "supervisor_name")
    private String supervisorName;

    @Column(name = "supervisor_email")
    private String supervisorEmail;

    @Column(name = "position", nullable = false)
    private Integer position;

    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;

    @PrePersist
    public void onCreate() {
        if (this.addedAt == null) {
            this.addedAt = LocalDateTime.now();
        }
    }

    public Long getWaitingListId() {
        return waitingListId;
    }

    public void setWaitingListId(Long waitingListId) {
        this.waitingListId = waitingListId;
    }

    public Long getSlotId() {
        return slotId;
    }

    public void setSlotId(Long slotId) {
        this.slotId = slotId;
    }

    public String getPresenterUsername() {
        return presenterUsername;
    }

    public void setPresenterUsername(String presenterUsername) {
        this.presenterUsername = presenterUsername;
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

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    public LocalDateTime getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(LocalDateTime addedAt) {
        this.addedAt = addedAt;
    }
}

