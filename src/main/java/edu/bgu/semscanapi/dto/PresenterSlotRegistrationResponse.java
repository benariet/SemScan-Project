package edu.bgu.semscanapi.dto;

public class PresenterSlotRegistrationResponse {

    private boolean success;
    private String message;
    private String code;
    private Boolean hasSupervisorEmail; // Indicates if supervisor email is available to send

    public PresenterSlotRegistrationResponse() {
    }

    public PresenterSlotRegistrationResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public PresenterSlotRegistrationResponse(boolean success, String message, String code) {
        this.success = success;
        this.message = message;
        this.code = code;
    }

    public PresenterSlotRegistrationResponse(boolean success, String message, String code, Boolean hasSupervisorEmail) {
        this.success = success;
        this.message = message;
        this.code = code;
        this.hasSupervisorEmail = hasSupervisorEmail;
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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Boolean getHasSupervisorEmail() {
        return hasSupervisorEmail;
    }

    public void setHasSupervisorEmail(Boolean hasSupervisorEmail) {
        this.hasSupervisorEmail = hasSupervisorEmail;
    }
}


