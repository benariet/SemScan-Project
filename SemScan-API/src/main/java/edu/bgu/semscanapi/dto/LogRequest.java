package edu.bgu.semscanapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * DTO for receiving log requests from mobile applications
 * Contains a list of log entries to be processed
 */
public class LogRequest {
    
    @JsonProperty("logs")
    private List<AppLogEntry> logs;
    
    // Constructors
    public LogRequest() {}
    
    public LogRequest(List<AppLogEntry> logs) {
        this.logs = logs;
    }
    
    // Getters and Setters
    public List<AppLogEntry> getLogs() {
        return logs;
    }
    
    public void setLogs(List<AppLogEntry> logs) {
        this.logs = logs;
    }
    
    @Override
    public String toString() {
        return "LogRequest{" +
                "logs=" + (logs != null ? logs.size() : 0) + " entries" +
                '}';
    }
}
