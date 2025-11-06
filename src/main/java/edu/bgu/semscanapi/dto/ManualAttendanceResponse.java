package edu.bgu.semscanapi.dto;

import java.time.LocalDateTime;

public class ManualAttendanceResponse {
    
    private Long attendanceId;
    private Long sessionId;
    private String studentUsername;
    private String studentName;
    private String reason;
    private String requestStatus;
    private LocalDateTime requestedAt;
    private String autoFlags;
    private String message;

    public ManualAttendanceResponse() {
    }

    public ManualAttendanceResponse(Long attendanceId, Long sessionId, String studentUsername, 
                                  String studentName, String reason, String requestStatus, 
                                  LocalDateTime requestedAt, String autoFlags, String message) {
        this.attendanceId = attendanceId;
        this.sessionId = sessionId;
        this.studentUsername = studentUsername;
        this.studentName = studentName;
        this.reason = reason;
        this.requestStatus = requestStatus;
        this.requestedAt = requestedAt;
        this.autoFlags = autoFlags;
        this.message = message;
    }

    // Getters and Setters
    public Long getAttendanceId() {
        return attendanceId;
    }

    public void setAttendanceId(Long attendanceId) {
        this.attendanceId = attendanceId;
    }

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

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getRequestStatus() {
        return requestStatus;
    }

    public void setRequestStatus(String requestStatus) {
        this.requestStatus = requestStatus;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public String getAutoFlags() {
        return autoFlags;
    }

    public void setAutoFlags(String autoFlags) {
        this.autoFlags = autoFlags;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "ManualAttendanceResponse{" +
                "attendanceId=" + attendanceId +
                ", sessionId=" + sessionId +
                ", studentUsername='" + studentUsername + '\'' +
                ", studentName='" + studentName + '\'' +
                ", reason='" + reason + '\'' +
                ", requestStatus='" + requestStatus + '\'' +
                ", requestedAt=" + requestedAt +
                ", autoFlags='" + autoFlags + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}
