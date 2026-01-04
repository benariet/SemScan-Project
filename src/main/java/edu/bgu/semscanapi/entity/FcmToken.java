package edu.bgu.semscanapi.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * FCM Token entity for push notifications
 * Stores one token per user (latest device wins)
 */
@Entity
@Table(name = "fcm_tokens")
public class FcmToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "bgu_username", nullable = false, unique = true)
    private String bguUsername;

    @Column(name = "fcm_token", nullable = false)
    private String fcmToken;

    @Column(name = "device_info")
    private String deviceInfo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_notification_title")
    private String lastNotificationTitle;

    @Column(name = "last_notification_body", columnDefinition = "TEXT")
    private String lastNotificationBody;

    @Column(name = "last_notification_sent_at")
    private LocalDateTime lastNotificationSentAt;

    public FcmToken() {
    }

    public FcmToken(String bguUsername, String fcmToken, String deviceInfo) {
        this.bguUsername = bguUsername;
        this.fcmToken = fcmToken;
        this.deviceInfo = deviceInfo;
    }

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBguUsername() {
        return bguUsername;
    }

    public void setBguUsername(String bguUsername) {
        this.bguUsername = bguUsername;
    }

    public String getFcmToken() {
        return fcmToken;
    }

    public void setFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public String getDeviceInfo() {
        return deviceInfo;
    }

    public void setDeviceInfo(String deviceInfo) {
        this.deviceInfo = deviceInfo;
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

    public String getLastNotificationTitle() {
        return lastNotificationTitle;
    }

    public void setLastNotificationTitle(String lastNotificationTitle) {
        this.lastNotificationTitle = lastNotificationTitle;
    }

    public String getLastNotificationBody() {
        return lastNotificationBody;
    }

    public void setLastNotificationBody(String lastNotificationBody) {
        this.lastNotificationBody = lastNotificationBody;
    }

    public LocalDateTime getLastNotificationSentAt() {
        return lastNotificationSentAt;
    }

    public void setLastNotificationSentAt(LocalDateTime lastNotificationSentAt) {
        this.lastNotificationSentAt = lastNotificationSentAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FcmToken fcmToken = (FcmToken) o;
        return Objects.equals(id, fcmToken.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "FcmToken{" +
                "id=" + id +
                ", bguUsername='" + bguUsername + '\'' +
                ", deviceInfo='" + deviceInfo + '\'' +
                '}';
    }
}
