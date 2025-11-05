package edu.bgu.semscanapi.dto;

/**
 * Response payload returned after a mobile login attempt.
 */
public class LoginResponse {

    private boolean ok;
    private String message;
    private Long userId;
    private String bguUsername;
    private String email;
    private boolean isFirstTime;
    private boolean isPresenter;
    private boolean isParticipant;

    public static LoginResponse success(Long userId,
                                        String bguUsername,
                                        String email,
                                        boolean isFirstTime,
                                        boolean isPresenter,
                                        boolean isParticipant) {
        LoginResponse response = new LoginResponse();
        response.ok = true;
        response.message = "Login successful";
        response.userId = userId;
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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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


