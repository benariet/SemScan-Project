package edu.bgu.semscanapi.dto;

import java.time.LocalDateTime;
import java.util.List;

public class SeminarTileDto {
    public String id;
    public String presenterId;
    public String seminarName;
    public List<SeminarSlotDto> slots;
    public LocalDateTime createdAt;
}


