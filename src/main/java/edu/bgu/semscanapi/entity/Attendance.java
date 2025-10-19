package edu.bgu.semscanapi.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonRawValue;

@Entity
@Table(name = "attendance")
public class Attendance {

    @Id
    @Column(name = "attendance_id", length = 36)
    private String attendanceId;

    @Column(name = "session_id", length = 36)
    private String sessionId;

    @Column(name = "student_id", length = 36)
    private String studentId;

    @Column(name = "attendance_time")
    private LocalDateTime attendanceTime;


    @Enumerated(EnumType.STRING)
    @Column(name = "method")
    private AttendanceMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_status")
    private RequestStatus requestStatus;

    @Column(name = "manual_reason", length = 255)
    private String manualReason;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @Column(name = "approved_by", length = 36)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "device_id", length = 255)
    private String deviceId;

    @Column(name = "auto_flags", columnDefinition = "JSON")
    @JsonRawValue
    private String autoFlags;

    public Attendance() {
    }

    // AttendanceMethod enum
    public enum AttendanceMethod {
        QR_SCAN, MANUAL, MANUAL_REQUEST, PROXY
    }

    // RequestStatus enum
    public enum RequestStatus {
        CONFIRMED, PENDING_APPROVAL, REJECTED
    }

    // Getters and Setters
    public String getAttendanceId() {
        return attendanceId;
    }

    public void setAttendanceId(String attendanceId) {
        this.attendanceId = attendanceId;
    }

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

    public LocalDateTime getAttendanceTime() {
        return attendanceTime;
    }

    public void setAttendanceTime(LocalDateTime attendanceTime) {
        this.attendanceTime = attendanceTime;
    }


    public AttendanceMethod getMethod() {
        return method;
    }

    public void setMethod(AttendanceMethod method) {
        this.method = method;
    }

    public RequestStatus getRequestStatus() {
        return requestStatus;
    }

    public void setRequestStatus(RequestStatus requestStatus) {
        this.requestStatus = requestStatus;
    }

    public String getManualReason() {
        return manualReason;
    }

    public void setManualReason(String manualReason) {
        this.manualReason = manualReason;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getAutoFlags() {
        return autoFlags;
    }

    public void setAutoFlags(String autoFlags) {
        this.autoFlags = autoFlags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Attendance that = (Attendance) o;
        return Objects.equals(attendanceId, that.attendanceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attendanceId);
    }

    @Override
    public String toString() {
        return "Attendance{" +
                "attendanceId='" + attendanceId + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", studentId='" + studentId + '\'' +
                ", attendanceTime=" + attendanceTime +
                ", method=" + method +
                ", requestStatus=" + requestStatus +
                ", manualReason='" + manualReason + '\'' +
                ", requestedAt=" + requestedAt +
                ", approvedBy='" + approvedBy + '\'' +
                ", approvedAt=" + approvedAt +
                ", deviceId='" + deviceId + '\'' +
                ", autoFlags='" + autoFlags + '\'' +
                '}';
    }
}
