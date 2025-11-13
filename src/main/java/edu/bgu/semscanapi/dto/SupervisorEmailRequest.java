package edu.bgu.semscanapi.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for sending supervisor notification email
 */
public class SupervisorEmailRequest {

    @NotBlank(message = "Supervisor email is required")
    @Email(message = "Supervisor email must be a valid email address")
    private String supervisorEmail;

    private String supervisorName;

    public String getSupervisorEmail() {
        return supervisorEmail;
    }

    public void setSupervisorEmail(String supervisorEmail) {
        this.supervisorEmail = supervisorEmail;
    }

    public String getSupervisorName() {
        return supervisorName;
    }

    public void setSupervisorName(String supervisorName) {
        this.supervisorName = supervisorName;
    }
}

