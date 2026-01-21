package org.example.semscan.data.model;

import com.google.gson.annotations.SerializedName;

public class Seminar {
    @SerializedName(value = "seminar_id", alternate = {"seminarId", "id"})
    private Long seminarId;
    
    @SerializedName(value = "seminar_name", alternate = {"seminarName", "name"})
    private String seminarName;
    
    @SerializedName(value = "seminar_code", alternate = {"seminarCode", "code"})
    private String seminarCode;
    
    private String description;
    
    @SerializedName(value = "presenter_id", alternate = {"presenterId"})
    private Long presenterId;
    
    public Seminar() {}
    
    public Seminar(Long seminarId, String seminarName, String seminarCode, String description, Long presenterId) {
        this.seminarId = seminarId;
        this.seminarName = seminarName;
        this.seminarCode = seminarCode;
        this.description = description;
        this.presenterId = presenterId;
    }
    
    // Getters and Setters
    public Long getSeminarId() {
        return seminarId;
    }
    
    public void setSeminarId(Long seminarId) {
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
    
    public Long getPresenterId() {
        return presenterId;
    }
    
    public void setPresenterId(Long presenterId) {
        this.presenterId = presenterId;
    }
    
    @Override
    public String toString() {
        return "Seminar{" +
                "seminarId='" + seminarId + '\'' +
                ", seminarName='" + seminarName + '\'' +
                ", seminarCode='" + seminarCode + '\'' +
                ", description='" + description + '\'' +
                ", presenterId='" + presenterId + '\'' +
                '}';
    }
}
