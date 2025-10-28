package edu.bgu.semscanapi.dto;

import java.time.LocalDateTime;
import java.util.List;

public class SeminarTileDto {
    public Long id;
    public Long presenterId;
    public Long seminarId;
    public String instanceName;
    public String instanceDescription;
    public List<SeminarSlotDto> slots;
    public LocalDateTime createdAt;
}


