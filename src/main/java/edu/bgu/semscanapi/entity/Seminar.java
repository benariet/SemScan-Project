package edu.bgu.semscanapi.entity;

import jakarta.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "seminars")
public class Seminar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "seminar_id", length = 30, unique = true)
    private String seminarId;

    @Column(name = "seminar_name")
    private String seminarName;

    @Column(name = "seminar_code", unique = true)
    private String seminarCode;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "presenter_id")
    private String presenterId;

    public Seminar() {
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSeminarId() {
        return seminarId;
    }

    public void setSeminarId(String seminarId) {
        this.seminarId = seminarId;
    }

    public String getSeminarName() {
        return seminarName;
    }

    public void setSeminarName(String seminarName) {
        this.seminarName = seminarName;
    }

    public String getSeminarCode() {
        return seminarCode;
    }

    public void setSeminarCode(String seminarCode) {
        this.seminarCode = seminarCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPresenterId() {
        return presenterId;
    }

    public void setPresenterId(String presenterId) {
        this.presenterId = presenterId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Seminar seminar = (Seminar) o;
        return Objects.equals(id, seminar.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Seminar{" +
                "id=" + id +
                ", seminarId='" + seminarId + '\'' +
                ", seminarName='" + seminarName + '\'' +
                ", seminarCode='" + seminarCode + '\'' +
                ", description='" + description + '\'' +
                ", presenterId=" + presenterId +
                '}';
    }
}
