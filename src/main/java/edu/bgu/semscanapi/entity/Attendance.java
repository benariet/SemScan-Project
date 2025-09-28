package edu.bgu.semscanapi.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

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

    public Attendance() {
    }


    // AttendanceMethod enum
    public enum AttendanceMethod {
        QR_SCAN, MANUAL, PROXY
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
                '}';
    }
}
