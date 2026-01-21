package edu.bgu.semscanapi.dto;

/**
 * DTO for log processing responses
 * Contains the result of processing log entries
 */
public class LogResponse {
    
    private boolean success;
    private String message;
    private int processedCount;
    
    // Constructors
    public LogResponse() {}
    
    public LogResponse(boolean success, String message, int processedCount) {
        this.success = success;
        this.message = message;
        this.processedCount = processedCount;
    }
    
    // Static factory methods for common responses
    public static LogResponse success(int processedCount) {
        return new LogResponse(true, "Logs processed successfully", processedCount);
    }
    
    public static LogResponse error(String message) {
        return new LogResponse(false, message, 0);
    }
    
    // Getters and Setters
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
    
    public int getProcessedCount() {
        return processedCount;
    }
    
    public void setProcessedCount(int processedCount) {
        this.processedCount = processedCount;
    }
    
    @Override
    public String toString() {
        return "LogResponse{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", processedCount=" + processedCount +
                '}';
    }
}
