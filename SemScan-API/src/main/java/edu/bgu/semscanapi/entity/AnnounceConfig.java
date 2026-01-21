package edu.bgu.semscanapi.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "announce_config")
public class AnnounceConfig {

    @Id
    @Column(name = "id")
    private Integer id = 1; // Singleton - always 1

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = false;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "title", length = 100)
    private String title = "";

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "is_blocking", nullable = false)
    private Boolean isBlocking = false;

    @Column(name = "start_at")
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Boolean getIsBlocking() {
        return isBlocking;
    }

    public void setIsBlocking(Boolean isBlocking) {
        this.isBlocking = isBlocking;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public void setStartAt(LocalDateTime startAt) {
        this.startAt = startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public void setEndAt(LocalDateTime endAt) {
        this.endAt = endAt;
    }

    /**
     * Check if announcement is currently active based on is_active flag and schedule.
     * Returns true if:
     * - is_active is true AND
     * - Current time is within start_at and end_at (if they are set)
     */
    public boolean isCurrentlyActive() {
        if (!Boolean.TRUE.equals(isActive)) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();

        // Check start_at constraint
        if (startAt != null && now.isBefore(startAt)) {
            return false;
        }

        // Check end_at constraint
        if (endAt != null && now.isAfter(endAt)) {
            return false;
        }

        return true;
    }
}
