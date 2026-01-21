package edu.bgu.semscanapi.dto;

public class PresenterSlotRegistrationResponse {

    private boolean ok; // Changed from 'success' to 'ok' for mobile compatibility
    private String message;
    private String code;
    private Boolean hasSupervisorEmail; // Indicates if supervisor email is available to send
    // New fields for mobile compatibility
    private Long registrationId;
    private String approvalToken;
    private String approvalStatus; // "PENDING_APPROVAL", "APPROVED", "DECLINED", "EXPIRED"

    public PresenterSlotRegistrationResponse() {
    }

    public PresenterSlotRegistrationResponse(boolean ok, String message) {
        this.ok = ok;
        this.message = message;
    }

    public PresenterSlotRegistrationResponse(boolean ok, String message, String code) {
        this.ok = ok;
        this.message = message;
        this.code = code;
    }

    public PresenterSlotRegistrationResponse(boolean ok, String message, String code, Boolean hasSupervisorEmail) {
        this.ok = ok;
        this.message = message;
        this.code = code;
        this.hasSupervisorEmail = hasSupervisorEmail;
    }

    public PresenterSlotRegistrationResponse(boolean ok, String message, String code, Long registrationId, String approvalToken, String approvalStatus) {
        this.ok = ok;
        this.message = message;
        this.code = code;
        this.registrationId = registrationId;
        this.approvalToken = approvalToken;
        this.approvalStatus = approvalStatus;
    }

    // Backward compatibility - keep isSuccess() for existing code
    public boolean isSuccess() {
        return ok;
    }

    public void setSuccess(boolean success) {
        this.ok = success;
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

    public Long getRegistrationId() {
        return registrationId;
    }

    public void setRegistrationId(Long registrationId) {
        this.registrationId = registrationId;
    }

    public String getApprovalToken() {
        return approvalToken;
    }

    public void setApprovalToken(String approvalToken) {
        this.approvalToken = approvalToken;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }
}


