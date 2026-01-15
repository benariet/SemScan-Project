package edu.bgu.semscanapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO for registering FCM token from mobile app
 */
public class FcmTokenRequest {

    @NotBlank(message = "FCM token is required")
    @JsonProperty("fcmToken")
    private String fcmToken;

    @JsonProperty("deviceInfo")
    private String deviceInfo;

    public FcmTokenRequest() {}

    public FcmTokenRequest(String fcmToken, String deviceInfo) {
        this.fcmToken = fcmToken;
        this.deviceInfo = deviceInfo;
    }

    public String getFcmToken() {
        return fcmToken;
    }

    public void setFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public String getDeviceInfo() {
        return deviceInfo;
    }

    public void setDeviceInfo(String deviceInfo) {
        this.deviceInfo = deviceInfo;
    }

    @Override
    public String toString() {
        String tokenPreview = "null";
        if (fcmToken != null && !fcmToken.isEmpty()) {
            tokenPreview = fcmToken.substring(0, Math.min(10, fcmToken.length())) + "...";
        }
        return "FcmTokenRequest{" +
                "fcmToken='" + tokenPreview + '\'' +
                ", deviceInfo='" + deviceInfo + '\'' +
                '}';
    }
}
