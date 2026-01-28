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
    private String nationalIdNumber;
    private String seminarAbstract;
    private String presentationTopic;
    private String supervisorName;
    private String supervisorEmail;

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

    public static UserProfileResponse success(String bguUsername,
                                              String message,
                                              String email,
                                              String firstName,
                                              String lastName,
                                              String degree,
                                              UserProfileUpdateRequest.ParticipationPreference participationPreference,
                                              String nationalIdNumber) {
        UserProfileResponse response = success(bguUsername, message, email, firstName, lastName, degree, participationPreference);
        response.setNationalIdNumber(nationalIdNumber);
        return response;
    }

    public static UserProfileResponse success(String bguUsername,
                                              String message,
                                              String email,
                                              String firstName,
                                              String lastName,
                                              String degree,
                                              UserProfileUpdateRequest.ParticipationPreference participationPreference,
                                              String nationalIdNumber,
                                              String seminarAbstract) {
        UserProfileResponse response = success(bguUsername, message, email, firstName, lastName, degree, participationPreference, nationalIdNumber);
        response.setSeminarAbstract(seminarAbstract);
        return response;
    }

    public static UserProfileResponse success(String bguUsername,
                                              String message,
                                              String email,
                                              String firstName,
                                              String lastName,
                                              String degree,
                                              UserProfileUpdateRequest.ParticipationPreference participationPreference,
                                              String nationalIdNumber,
                                              String seminarAbstract,
                                              String presentationTopic,
                                              String supervisorName,
                                              String supervisorEmail) {
        UserProfileResponse response = success(bguUsername, message, email, firstName, lastName, degree, participationPreference, nationalIdNumber, seminarAbstract);
        response.setPresentationTopic(presentationTopic);
        response.setSupervisorName(supervisorName);
        response.setSupervisorEmail(supervisorEmail);
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

    public String getNationalIdNumber() {
        return nationalIdNumber;
    }

    public void setNationalIdNumber(String nationalIdNumber) {
        this.nationalIdNumber = nationalIdNumber;
    }

    public String getSeminarAbstract() {
        return seminarAbstract;
    }

    public void setSeminarAbstract(String seminarAbstract) {
        this.seminarAbstract = seminarAbstract;
    }

    public String getPresentationTopic() {
        return presentationTopic;
    }

    public void setPresentationTopic(String presentationTopic) {
        this.presentationTopic = presentationTopic;
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


