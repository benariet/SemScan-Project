package edu.bgu.semscanapi.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO for QR code generation response
 */
public class QRCodeResponse {
    
    private String sessionId;
    private String seminarId;
    private String status;
    private LocalDateTime startTime;
    private Map<String, String> qrContent;
    private Map<String, String> serverInfo;
    private Map<String, Object> metadata;
    
    public QRCodeResponse() {
    }
    
    public QRCodeResponse(String sessionId, String seminarId, String status, LocalDateTime startTime,
                         Map<String, String> qrContent, Map<String, String> serverInfo, 
                         Map<String, Object> metadata) {
        this.sessionId = sessionId;
        this.seminarId = seminarId;
        this.status = status;
        this.startTime = startTime;
        this.qrContent = qrContent;
        this.serverInfo = serverInfo;
        this.metadata = metadata;
    }
    
    // Getters and Setters
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public String getSeminarId() {
        return seminarId;
    }
    
    public void setSeminarId(String seminarId) {
        this.seminarId = seminarId;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public LocalDateTime getStartTime() {
        return startTime;
    }
    
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }
    
    public Map<String, String> getQrContent() {
        return qrContent;
    }
    
    public void setQrContent(Map<String, String> qrContent) {
        this.qrContent = qrContent;
    }
    
    public Map<String, String> getServerInfo() {
        return serverInfo;
    }
    
    public void setServerInfo(Map<String, String> serverInfo) {
        this.serverInfo = serverInfo;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    @Override
    public String toString() {
        return "QRCodeResponse{" +
                "sessionId='" + sessionId + '\'' +
                ", seminarId='" + seminarId + '\'' +
                ", status='" + status + '\'' +
                ", startTime=" + startTime +
                ", qrContent=" + qrContent +
                ", serverInfo=" + serverInfo +
                ", metadata=" + metadata +
                '}';
    }
}
