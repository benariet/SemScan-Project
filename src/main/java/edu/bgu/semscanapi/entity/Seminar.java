package edu.bgu.semscanapi.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "seminars")
public class Seminar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seminar_id")
    private Long seminarId;

    @Column(name = "seminar_name")
    private String seminarName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "presenter_username", nullable = false)
    private String presenterUsername;

    @Column(name = "max_enrollment_capacity")
    private Integer maxEnrollmentCapacity;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Seminar() {
    }

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getSeminarId() {
        return seminarId;
    }

    public void setSeminarId(Long seminarId) {
        this.seminarId = seminarId;
    }

    public String getSeminarName() {
        return seminarName;
    }

    public void setSeminarName(String seminarName) {
        this.seminarName = seminarName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPresenterUsername() {
        return presenterUsername;
    }

    public void setPresenterUsername(String presenterUsername) {
        this.presenterUsername = presenterUsername;
    }

    public Integer getMaxEnrollmentCapacity() {
        return maxEnrollmentCapacity;
    }

    public void setMaxEnrollmentCapacity(Integer maxEnrollmentCapacity) {
        this.maxEnrollmentCapacity = maxEnrollmentCapacity;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Seminar seminar = (Seminar) o;
        return Objects.equals(seminarId, seminar.seminarId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(seminarId);
    }

    @Override
    public String toString() {
        return "Seminar{" +
                "seminarId=" + seminarId +
                ", seminarName='" + seminarName + '\'' +
                ", description='" + description + '\'' +
                ", presenterUsername='" + presenterUsername + '\'' +
                ", maxEnrollmentCapacity=" + maxEnrollmentCapacity +
                '}';
    }
}
