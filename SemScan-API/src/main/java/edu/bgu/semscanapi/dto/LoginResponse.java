package edu.bgu.semscanapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response payload returned after a mobile login attempt.
 */
public class LoginResponse {

    @JsonProperty("ok")
    private boolean ok;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("bguUsername")
    private String bguUsername;
    
    @JsonProperty("email")
    private String email;
    
    @JsonProperty("firstTime")
    private boolean isFirstTime;
    
    @JsonProperty("presenter")
    private boolean isPresenter;
    
    @JsonProperty("participant")
    private boolean isParticipant;

    public static LoginResponse success(String bguUsername,
                                        String email,
                                        boolean isFirstTime,
                                        boolean isPresenter,
                                        boolean isParticipant) {
        LoginResponse response = new LoginResponse();
        response.ok = true;
        response.message = "Login successful";
        response.bguUsername = bguUsername;
        response.email = email;
        response.isFirstTime = isFirstTime;
        response.isPresenter = isPresenter;
        response.isParticipant = isParticipant;
        return response;
    }

    public static LoginResponse failure(String message) {
        LoginResponse response = new LoginResponse();
        response.ok = false;
        response.message = message;
        // Ensure all fields are initialized to prevent null pointer exceptions in mobile app
        response.bguUsername = null;
        response.email = null;
        response.isFirstTime = false;
        response.isPresenter = false;
        response.isParticipant = false;
        return response;
    }

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
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

    public boolean isFirstTime() {
        return isFirstTime;
    }

    public void setFirstTime(boolean firstTime) {
        isFirstTime = firstTime;
    }

    public boolean isPresenter() {
        return isPresenter;
    }

    public void setPresenter(boolean presenter) {
        isPresenter = presenter;
    }

    public boolean isParticipant() {
        return isParticipant;
    }

    public void setParticipant(boolean participant) {
        isParticipant = participant;
    }
}


