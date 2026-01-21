package edu.bgu.semscanapi.dto;

import java.time.LocalDate;
import java.util.List;

public class AttendanceDatesResponse {
    
    private List<LocalDate> dates;
    
    public AttendanceDatesResponse() {
    }
    
    public AttendanceDatesResponse(List<LocalDate> dates) {
        this.dates = dates != null ? dates : new java.util.ArrayList<>();
    }
    
    public List<LocalDate> getDates() {
        return dates != null ? dates : new java.util.ArrayList<>();
    }
    
    public void setDates(List<LocalDate> dates) {
        this.dates = dates != null ? dates : new java.util.ArrayList<>();
    }
}
