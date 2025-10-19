package edu.bgu.semscanapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ManualAttendanceRequest {
    
    @NotBlank(message = "Session ID is required")
    private String sessionId;
    
    @NotBlank(message = "Student ID is required")
    private String studentId;
    
    @NotBlank(message = "Reason is required")
    private String reason;
    
    private String deviceId;

    public ManualAttendanceRequest() {
    }

    public ManualAttendanceRequest(String sessionId, String studentId, String reason, String deviceId) {
        this.sessionId = sessionId;
        this.studentId = studentId;
        this.reason = reason;
        this.deviceId = deviceId;
    }

    // Getters and Setters
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
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
                ", studentId='" + studentId + '\'' +
                ", reason='" + reason + '\'' +
                ", deviceId='" + deviceId + '\'' +
                '}';
    }
}
