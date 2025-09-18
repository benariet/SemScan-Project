package edu.bgu.semscanapi.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "absence_requests")
public class AbsenceRequest {

    @Id
    @Column(name = "request_id", length = 36)
    private String requestId;

    @Column(name = "student_id", length = 36, nullable = false)
    private String studentId;

    @Column(name = "course_id", length = 36, nullable = false)
    private String courseId;

    @Column(name = "session_id", length = 36)
    private String sessionId; // Optional - can be null for general absence

    @NotBlank(message = "Reason is required")
    @Column(name = "reason", length = 255, nullable = false)
    private String reason;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private RequestStatus status;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    public AbsenceRequest() {
    }

    // RequestStatus enum
    public enum RequestStatus {
        PENDING, APPROVED, REJECTED
    }

    // Getters and Setters
    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getCourseId() {
        return courseId;
    }

    public void setCourseId(String courseId) {
        this.courseId = courseId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public void setStatus(RequestStatus status) {
        this.status = status;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbsenceRequest that = (AbsenceRequest) o;
        return Objects.equals(requestId, that.requestId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId);
    }

    @Override
    public String toString() {
        return "AbsenceRequest{" +
                "requestId='" + requestId + '\'' +
                ", studentId='" + studentId + '\'' +
                ", courseId='" + courseId + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", reason='" + reason + '\'' +
                ", status=" + status +
                ", submittedAt=" + submittedAt +
                '}';
    }
}
