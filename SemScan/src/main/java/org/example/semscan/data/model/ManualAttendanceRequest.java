package org.example.semscan.data.model;

public class ManualAttendanceRequest {
    private Long sessionId;
    private String studentUsername;
    private String reason;
    private String deviceId;

    public ManualAttendanceRequest() {}

    public ManualAttendanceRequest(Long sessionId, String studentUsername, String reason, String deviceId) {
        this.sessionId = sessionId;
        this.studentUsername = studentUsername;
        this.reason = reason;
        this.deviceId = deviceId;
    }

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public String getStudentUsername() { return studentUsername; }
    public void setStudentUsername(String studentUsername) { this.studentUsername = studentUsername; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
}
