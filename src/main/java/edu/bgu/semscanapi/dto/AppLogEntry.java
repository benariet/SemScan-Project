package edu.bgu.semscanapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for individual log entries from mobile applications
 * Represents a single log entry with all relevant information
 */
public class AppLogEntry {
    
    @JsonProperty("timestamp")
    private Long timestamp;
    
    @JsonProperty("level")
    private String level;
    
    @JsonProperty("tag")
    private String tag;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("userId")
    private String userId;
    
    @JsonProperty("userRole")
    private String userRole;
    
    @JsonProperty("deviceInfo")
    private String deviceInfo;
    
    @JsonProperty("appVersion")
    private String appVersion;
    
    @JsonProperty("stackTrace")
    private String stackTrace;
    
    @JsonProperty("exceptionType")
    private String exceptionType;
    
    // Constructors
    public AppLogEntry() {}
    
    public AppLogEntry(Long timestamp, String level, String tag, String message) {
        this.timestamp = timestamp;
        this.level = level;
        this.tag = tag;
        this.message = message;
    }
    
    // Getters and Setters
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
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getUserRole() {
        return userRole;
    }
    
    public void setUserRole(String userRole) {
        this.userRole = userRole;
    }
    
    public String getDeviceInfo() {
        return deviceInfo;
    }
    
    public void setDeviceInfo(String deviceInfo) {
        this.deviceInfo = deviceInfo;
    }
    
    public String getAppVersion() {
        return appVersion;
    }
    
    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }
    
    public String getStackTrace() {
        return stackTrace;
    }
    
    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }
    
    public String getExceptionType() {
        return exceptionType;
    }
    
    public void setExceptionType(String exceptionType) {
        this.exceptionType = exceptionType;
    }
    
    @Override
    public String toString() {
        return "AppLogEntry{" +
                "timestamp=" + timestamp +
                ", level='" + level + '\'' +
                ", tag='" + tag + '\'' +
                ", message='" + message + '\'' +
                ", userId='" + userId + '\'' +
                ", userRole='" + userRole + '\'' +
                ", deviceInfo='" + deviceInfo + '\'' +
                ", appVersion='" + appVersion + '\'' +
                ", stackTrace='" + stackTrace + '\'' +
                ", exceptionType='" + exceptionType + '\'' +
                '}';
    }
}
