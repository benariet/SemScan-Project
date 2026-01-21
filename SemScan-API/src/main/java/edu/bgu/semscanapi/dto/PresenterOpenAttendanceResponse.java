package edu.bgu.semscanapi.dto;

public class PresenterOpenAttendanceResponse {

    private boolean success;
    private String message;
    private String code;
    private String qrUrl;
    private String closesAt;
    private String openedAt;
    private Long sessionId;
    private String qrPayload;

    public PresenterOpenAttendanceResponse() {
    }

    public PresenterOpenAttendanceResponse(boolean success,
                                            String message,
                                            String code,
                                            String qrUrl,
                                            String openedAt,
                                            String closesAt,
                                            Long sessionId,
                                            String qrPayload) {
        this.success = success;
        this.message = message;
        this.code = code;
        this.qrUrl = qrUrl;
        this.openedAt = openedAt;
        this.closesAt = closesAt;
        this.sessionId = sessionId;
        this.qrPayload = qrPayload;
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

    public String getQrUrl() {
        return qrUrl;
    }

    public void setQrUrl(String qrUrl) {
        this.qrUrl = qrUrl;
    }

    public String getClosesAt() {
        return closesAt;
    }

    public void setClosesAt(String closesAt) {
        this.closesAt = closesAt;
    }

    public String getOpenedAt() {
        return openedAt;
    }

    public void setOpenedAt(String openedAt) {
        this.openedAt = openedAt;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getQrPayload() {
        return qrPayload;
    }

    public void setQrPayload(String qrPayload) {
        this.qrPayload = qrPayload;
    }
}


