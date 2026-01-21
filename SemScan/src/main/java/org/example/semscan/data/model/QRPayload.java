package org.example.semscan.data.model;

import com.google.gson.annotations.SerializedName;

public class QRPayload {
    @SerializedName("sessionId")
    private Long sessionId;
    
    public QRPayload() {}
    
    public QRPayload(Long sessionId) {
        this.sessionId = sessionId;
    }
    
    public Long getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }
    
    @Override
    public String toString() {
        return "QRPayload{" +
                "sessionId='" + sessionId + '\'' +
                '}';
    }
}
