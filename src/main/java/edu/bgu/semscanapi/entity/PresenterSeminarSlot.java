package edu.bgu.semscanapi.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "presenter_seminar_slot")
public class PresenterSeminarSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "presenter_seminar_slot_id", length = 30, unique = true)
    private String presenterSeminarSlotId;

    @Column(name = "presenter_seminar_id", nullable = false)
    private String presenterSeminarId;

    @Column(name = "weekday", nullable = false)
    private Integer weekday; // 0-6

    @Column(name = "start_hour", nullable = false)
    private Integer startHour; // 0-23

    @Column(name = "end_hour", nullable = false)
    private Integer endHour; // 1-24

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPresenterSeminarSlotId() { return presenterSeminarSlotId; }
    public void setPresenterSeminarSlotId(String presenterSeminarSlotId) { this.presenterSeminarSlotId = presenterSeminarSlotId; }
    public String getPresenterSeminarId() { return presenterSeminarId; }
    public void setPresenterSeminarId(String presenterSeminarId) { this.presenterSeminarId = presenterSeminarId; }
    public Integer getWeekday() { return weekday; }
    public void setWeekday(Integer weekday) { this.weekday = weekday; }
    public Integer getStartHour() { return startHour; }
    public void setStartHour(Integer startHour) { this.startHour = startHour; }
    public Integer getEndHour() { return endHour; }
    public void setEndHour(Integer endHour) { this.endHour = endHour; }
}


