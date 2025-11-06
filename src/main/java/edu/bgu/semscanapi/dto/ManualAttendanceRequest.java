package edu.bgu.semscanapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ManualAttendanceRequest {
    
    @NotNull(message = "Session ID is required")
    private Long sessionId;
    
    @NotBlank(message = "Student username is required")
    private String studentUsername;
    
    @NotBlank(message = "Reason is required")
    private String reason;
    
    private String deviceId;

    public ManualAttendanceRequest() {
    }

    public ManualAttendanceRequest(Long sessionId, String studentUsername, String reason, String deviceId) {
        this.sessionId = sessionId;
        this.studentUsername = studentUsername;
        this.reason = reason;
        this.deviceId = deviceId;
    }

    // Getters and Setters
    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getStudentUsername() {
        return studentUsername;
    }

    public void setStudentUsername(String studentUsername) {
        this.studentUsername = studentUsername;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    @Override
    public String toString() {
        return "ManualAttendanceRequest{" +
                "sessionId='" + sessionId + '\'' +
                ", studentUsername='" + studentUsername + '\'' +
                ", reason='" + reason + '\'' +
                ", deviceId='" + deviceId + '\'' +
                '}';
    }
}
