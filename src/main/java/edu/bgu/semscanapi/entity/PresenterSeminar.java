package edu.bgu.semscanapi.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "presenter_seminar")
public class PresenterSeminar {

    @Id
    @Column(name = "presenter_seminar_id", length = 36)
    private String presenterSeminarId;

    @Column(name = "presenter_id", length = 36, nullable = false)
    private String presenterId;

    @Column(name = "seminar_name")
    private String seminarName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public String getPresenterSeminarId() { return presenterSeminarId; }
    public void setPresenterSeminarId(String presenterSeminarId) { this.presenterSeminarId = presenterSeminarId; }
    public String getPresenterId() { return presenterId; }
    public void setPresenterId(String presenterId) { this.presenterId = presenterId; }
    public String getSeminarName() { return seminarName; }
    public void setSeminarName(String seminarName) { this.seminarName = seminarName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}


