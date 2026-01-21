package org.example.semscan.data.model;

import java.util.List;

/**
 * Response model for student session endpoints
 * Contains sessions data with metadata
 */
public class StudentSessionResponse {
    private List<Session> sessions;
    private int totalCount;
    private String message;
    private String correlationId;
    
    public StudentSessionResponse() {}
    
    public StudentSessionResponse(List<Session> sessions, int totalCount, String message, String correlationId) {
        this.sessions = sessions;
        this.totalCount = totalCount;
        this.message = message;
        this.correlationId = correlationId;
    }
    
    public List<Session> getSessions() {
        return sessions;
    }
    
    public void setSessions(List<Session> sessions) {
        this.sessions = sessions;
    }
    
    public int getTotalCount() {
        return totalCount;
    }
    
    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getCorrelationId() {
        return correlationId;
    }
    
    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }
    
    @Override
    public String toString() {
        return "StudentSessionResponse{" +
                "sessions=" + sessions +
                ", totalCount=" + totalCount +
                ", message='" + message + '\'' +
                ", correlationId='" + correlationId + '\'' +
                '}';
    }
}
