package edu.bgu.semscanapi.dto;

/**
 * Generic response returned after updating user profile details.
 */
public class UserProfileResponse {

    private boolean success;
    private String message;
    private String bguUsername;
    private String email;
    private String firstName;
    private String lastName;
    private String degree;
    private UserProfileUpdateRequest.ParticipationPreference participationPreference;

    public static UserProfileResponse success(String bguUsername, String message) {
        UserProfileResponse response = new UserProfileResponse();
        response.success = true;
        response.bguUsername = bguUsername;
        response.message = message;
        return response;
    }

    public static UserProfileResponse success(String bguUsername,
                                              String message,
                                              String email,
                                              String firstName,
                                              String lastName,
                                              String degree,
                                              UserProfileUpdateRequest.ParticipationPreference participationPreference) {
        UserProfileResponse response = success(bguUsername, message);
        response.setEmail(email);
        response.setFirstName(firstName);
        response.setLastName(lastName);
        response.setDegree(degree);
        response.setParticipationPreference(participationPreference);
        return response;
    }

    public static UserProfileResponse failure(String message) {
        UserProfileResponse response = new UserProfileResponse();
        response.success = false;
        response.message = message;
        return response;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

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

    public String getDegree() {
        return degree;
    }

    public void setDegree(String degree) {
        this.degree = degree;
    }

    public UserProfileUpdateRequest.ParticipationPreference getParticipationPreference() {
        return participationPreference;
    }

    public void setParticipationPreference(UserProfileUpdateRequest.ParticipationPreference participationPreference) {
        this.participationPreference = participationPreference;
    }
}


