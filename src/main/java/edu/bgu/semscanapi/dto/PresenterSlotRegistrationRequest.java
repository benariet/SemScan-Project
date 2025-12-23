package edu.bgu.semscanapi.dto;

import jakarta.validation.constraints.Email;
public class PresenterSlotRegistrationRequest {

    private String topic;

    private String seminarAbstract;

    private String supervisorName;

    @Email(message = "supervisorEmail must be a valid email")
    private String supervisorEmail;

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getSeminarAbstract() {
        return seminarAbstract;
    }

    public void setSeminarAbstract(String seminarAbstract) {
        this.seminarAbstract = seminarAbstract;
    }

    public String getSupervisorName() {
        return supervisorName;
    }

    public void setSupervisorName(String supervisorName) {
        this.supervisorName = supervisorName;
    }

    public String getSupervisorEmail() {
        return supervisorEmail;
    }

    public void setSupervisorEmail(String supervisorEmail) {
        this.supervisorEmail = supervisorEmail;
    }
}


