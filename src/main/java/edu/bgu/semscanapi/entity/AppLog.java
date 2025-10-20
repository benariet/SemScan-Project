package edu.bgu.semscanapi.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity class for app logs from mobile applications
 * Stores log entries from Android/iOS apps for analytics and debugging
 */
@Entity
@Table(name = "app_logs")
public class AppLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "timestamp", nullable = false)
    private Long timestamp;
    
    @Column(name = "level", nullable = false, length = 10)
    private String level;
    
    @Column(name = "tag", nullable = false, length = 50)
    private String tag;
    
    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;
    
    @Column(name = "user_id", length = 50)
    private String userId;
    
    @Column(name = "user_role", length = 20)
    private String userRole;
    
    @Column(name = "device_info", columnDefinition = "TEXT")
    private String deviceInfo;
    
    @Column(name = "app_version", length = 20)
    private String appVersion;
    
    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;
    
    @Column(name = "exception_type", length = 100)
    private String exceptionType;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // Constructors
    public AppLog() {
        this.createdAt = LocalDateTime.now();
    }
    
    public AppLog(Long timestamp, String level, String tag, String message) {
        this();
        this.timestamp = timestamp;
        this.level = level;
        this.tag = tag;
        this.message = message;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    @JsonProperty("timestamp")
    public Long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getLevel() {
        return level;
    }
    
    public void setLevel(String level) {
        this.level = level;
    }
    
    public String getTag() {
        return tag;
    }
    
    public void setTag(String tag) {
        this.tag = tag;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    @JsonProperty("userId")
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    @JsonProperty("userRole")
    public String getUserRole() {
        return userRole;
    }
    
    public void setUserRole(String userRole) {
        this.userRole = userRole;
    }
    
    @JsonProperty("deviceInfo")
    public String getDeviceInfo() {
        return deviceInfo;
    }
    
    public void setDeviceInfo(String deviceInfo) {
        this.deviceInfo = deviceInfo;
    }
    
    @JsonProperty("appVersion")
    public String getAppVersion() {
        return appVersion;
    }
    
    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }
    
    @JsonProperty("stackTrace")
    public String getStackTrace() {
        return stackTrace;
    }
    
    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }
    
    @JsonProperty("exceptionType")
    public String getExceptionType() {
        return exceptionType;
    }
    
    public void setExceptionType(String exceptionType) {
        this.exceptionType = exceptionType;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    @Override
    public String toString() {
        return "AppLog{" +
                "id=" + id +
                ", timestamp=" + timestamp +
                ", level='" + level + '\'' +
                ", tag='" + tag + '\'' +
                ", message='" + message + '\'' +
                ", userId='" + userId + '\'' +
                ", userRole='" + userRole + '\'' +
                ", deviceInfo='" + deviceInfo + '\'' +
                ", appVersion='" + appVersion + '\'' +
                ", stackTrace='" + stackTrace + '\'' +
                ", exceptionType='" + exceptionType + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
