package edu.bgu.semscanapi.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO used by mobile clients to push updated user profile details (first/last name, email).
 */
public class UserProfileUpdateRequest {

    @NotBlank(message = "bguUsername is required")
    private String bguUsername;

    @Email(message = "email must be a valid address")
    @jakarta.validation.constraints.NotBlank(message = "email is required")
    private String email;

    @jakarta.validation.constraints.NotBlank(message = "firstName is required")
    private String firstName;

    @jakarta.validation.constraints.NotBlank(message = "lastName is required")
    private String lastName;

    @NotNull(message = "degree is required")
    private edu.bgu.semscanapi.entity.User.Degree degree;

    @NotNull(message = "participationPreference is required")
    private ParticipationPreference participationPreference;

    public String getBguUsername() {
        return bguUsername;
    }

    public void setBguUsername(String bguUsername) {
        this.bguUsername = bguUsername;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public edu.bgu.semscanapi.entity.User.Degree getDegree() {
        return degree;
    }

    public void setDegree(edu.bgu.semscanapi.entity.User.Degree degree) {
        this.degree = degree;
    }

    public ParticipationPreference getParticipationPreference() {
        return participationPreference;
    }

    public void setParticipationPreference(ParticipationPreference participationPreference) {
        this.participationPreference = participationPreference;
    }

    public enum ParticipationPreference {
        PARTICIPANT_ONLY,
        PRESENTER_ONLY,
        BOTH
    }
}


