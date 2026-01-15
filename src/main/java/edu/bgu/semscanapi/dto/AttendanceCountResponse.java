package edu.bgu.semscanapi.dto;

public class AttendanceCountResponse {
    
    private long totalCount;
    
    public AttendanceCountResponse() {
    }
    
    public AttendanceCountResponse(long totalCount) {
        this.totalCount = totalCount;
    }
    
    public long getTotalCount() {
        return totalCount;
    }
    
    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }
}
