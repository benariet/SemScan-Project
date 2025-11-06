package edu.bgu.semscanapi.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "seminar_slot")
public class SeminarSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "slot_id")
    private Long slotId;

    @Column(name = "semester_label")
    private String semesterLabel;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "building")
    private String building;

    @Column(name = "room")
    private String room;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SlotStatus status = SlotStatus.FREE;

    @Column(name = "attendance_opened_at")
    private LocalDateTime attendanceOpenedAt;

    @Column(name = "attendance_closes_at")
    private LocalDateTime attendanceClosesAt;

    @Column(name = "attendance_opened_by")
    private String attendanceOpenedBy;

    @Column(name = "legacy_seminar_id")
    private Long legacySeminarId;

    @Column(name = "legacy_session_id")
    private Long legacySessionId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getSlotId() {
        return slotId;
    }

    public void setSlotId(Long slotId) {
        this.slotId = slotId;
    }

    public String getSemesterLabel() {
        return semesterLabel;
    }

    public void setSemesterLabel(String semesterLabel) {
        this.semesterLabel = semesterLabel;
    }

    public LocalDate getSlotDate() {
        return slotDate;
    }

    public void setSlotDate(LocalDate slotDate) {
        this.slotDate = slotDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public String getBuilding() {
        return building;
    }

    public void setBuilding(String building) {
        this.building = building;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public SlotStatus getStatus() {
        return status;
    }

    public void setStatus(SlotStatus status) {
        this.status = status;
    }

    public LocalDateTime getAttendanceOpenedAt() {
        return attendanceOpenedAt;
    }

    public void setAttendanceOpenedAt(LocalDateTime attendanceOpenedAt) {
        this.attendanceOpenedAt = attendanceOpenedAt;
    }

    public LocalDateTime getAttendanceClosesAt() {
        return attendanceClosesAt;
    }

    public void setAttendanceClosesAt(LocalDateTime attendanceClosesAt) {
        this.attendanceClosesAt = attendanceClosesAt;
    }

    public String getAttendanceOpenedBy() {
        return attendanceOpenedBy;
    }

    public void setAttendanceOpenedBy(String attendanceOpenedBy) {
        this.attendanceOpenedBy = attendanceOpenedBy;
    }

    public Long getLegacySeminarId() {
        return legacySeminarId;
    }

    public void setLegacySeminarId(Long legacySeminarId) {
        this.legacySeminarId = legacySeminarId;
    }

    public Long getLegacySessionId() {
        return legacySessionId;
    }

    public void setLegacySessionId(Long legacySessionId) {
        this.legacySessionId = legacySessionId;
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

    public enum SlotStatus {
        FREE,
        SEMI,
        FULL
    }
}


