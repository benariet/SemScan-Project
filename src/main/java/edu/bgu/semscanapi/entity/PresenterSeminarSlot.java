package edu.bgu.semscanapi.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "presenter_seminar_slot")
public class PresenterSeminarSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "presenter_seminar_slot_id")
    private Long presenterSeminarSlotId;

    @Column(name = "presenter_seminar_id", nullable = false)
    private Long presenterSeminarId;

    @Column(name = "weekday", nullable = false)
    private Integer weekday; // 0-6

    @Column(name = "start_hour", nullable = false)
    private Integer startHour; // 0-23

    @Column(name = "end_hour", nullable = false)
    private Integer endHour; // 1-24

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getPresenterSeminarSlotId() { return presenterSeminarSlotId; }
    public void setPresenterSeminarSlotId(Long presenterSeminarSlotId) { this.presenterSeminarSlotId = presenterSeminarSlotId; }
    public Long getPresenterSeminarId() { return presenterSeminarId; }
    public void setPresenterSeminarId(Long presenterSeminarId) { this.presenterSeminarId = presenterSeminarId; }
    public Integer getWeekday() { return weekday; }
    public void setWeekday(Integer weekday) { this.weekday = weekday; }
    public Integer getStartHour() { return startHour; }
    public void setStartHour(Integer startHour) { this.startHour = startHour; }
    public Integer getEndHour() { return endHour; }
    public void setEndHour(Integer endHour) { this.endHour = endHour; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @PrePersist
    public void onCreate() { this.createdAt = LocalDateTime.now(); }
}


