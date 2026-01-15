package edu.bgu.semscanapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO for adding a user to the waiting list
 */
public class WaitingListRequest {

    @NotBlank(message = "Presenter username is required")
    @JsonProperty("username") // Map JSON "username" to Java "presenterUsername" for mobile compatibility
    private String presenterUsername;

    private String topic;

    private String supervisorName;

    @Email(message = "Supervisor email must be a valid email address")
    private String supervisorEmail;

    public String getPresenterUsername() {
        return presenterUsername;
    }

    public void setPresenterUsername(String presenterUsername) {
        this.presenterUsername = presenterUsername;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
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

