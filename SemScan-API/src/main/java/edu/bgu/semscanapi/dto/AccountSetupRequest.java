package edu.bgu.semscanapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO for completing account setup (setting supervisor information)
 * Used when a user logs in for the first time
 */
public class AccountSetupRequest {

    @NotBlank(message = "Supervisor name is required")
    @JsonProperty("supervisorName")
    private String supervisorName;

    @NotBlank(message = "Supervisor email is required")
    @Email(message = "Supervisor email must be a valid email address")
    @JsonProperty("supervisorEmail")
    private String supervisorEmail;

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
