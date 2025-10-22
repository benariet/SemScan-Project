package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.dto.SeminarSlotDto;
import edu.bgu.semscanapi.dto.SeminarTileDto;
import edu.bgu.semscanapi.entity.PresenterSeminar;
import edu.bgu.semscanapi.entity.PresenterSeminarSlot;
import edu.bgu.semscanapi.repository.PresenterSeminarRepository;
import edu.bgu.semscanapi.repository.PresenterSeminarSlotRepository;
import edu.bgu.semscanapi.service.AuthenticationService;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/presenters/{presenterId}/seminars")
@CrossOrigin(origins = "*")
public class PresenterSeminarController {

    private static final Logger logger = LoggerUtil.getLogger(PresenterSeminarController.class);

    @Autowired private AuthenticationService auth;
    @Autowired private PresenterSeminarRepository seminars;
    @Autowired private PresenterSeminarSlotRepository slots;

    @GetMapping
    public ResponseEntity<Object> list(
                                       @PathVariable String presenterId) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("GET /api/v1/presenters/{}/seminars - Correlation ID: {}", presenterId, correlationId);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/presenters/"+presenterId+"/seminars", null);
        try {
            // No authentication required for POC
            logger.info("Processing seminars list for presenter {} - Correlation ID: {}", presenterId, correlationId);
        var list = seminars.findByPresenterIdOrderByCreatedAtDesc(presenterId).stream()
                .map(this::toTile)
                    .collect(Collectors.toList());
            logger.info("Seminar tiles: {} for presenter {} - Correlation ID: {}", list.size(), presenterId, correlationId);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/presenters/"+presenterId+"/seminars", 200, "Count: "+list.size());
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            logger.error("Error listing seminars for presenter {} - Correlation ID: {}", presenterId, correlationId, e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/presenters/"+presenterId+"/seminars", 500, "Internal Server Error");
            throw e;
        } finally {
            LoggerUtil.clearContext();
        }
    }

    @PostMapping
    public ResponseEntity<Object> create(
                                         @PathVariable String presenterId,
                                         @RequestBody CreateRequest body) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("POST /api/v1/presenters/{}/seminars - Correlation ID: {}", presenterId, correlationId);
        LoggerUtil.logApiRequest(logger, "POST", "/api/v1/presenters/"+presenterId+"/seminars", body != null ? body.toString() : null);
        // No authentication required for POC
        logger.info("Processing seminar create for presenter {} - Correlation ID: {}", presenterId, correlationId);
        // No subject validation (subject removed)
        if (body == null) {
            LoggerUtil.logApiResponse(logger, "POST", "/api/v1/presenters/"+presenterId+"/seminars", 400, "Body required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Request body is required"));
        }
        // Validate slots
        if (body.slots == null || body.slots.isEmpty()) {
            LoggerUtil.logApiResponse(logger, "POST", "/api/v1/presenters/"+presenterId+"/seminars", 400, "No slots provided");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "At least one slot is required"));
        }
        Set<String> dedup = new HashSet<>();
        int i = 0;
        for (SeminarSlotDto s : body.slots) {
            i++;
            logger.debug("Validating slot #{}: w={} {}-{} - Correlation ID: {}", i, s.weekday, s.startHour, s.endHour, correlationId);
            if (s.weekday < 0 || s.weekday > 6) return bad("weekday must be 0-6", presenterId, correlationId, "POST");
            if (s.startHour < 0 || s.startHour > 23) return bad("startHour must be 0-23", presenterId, correlationId, "POST");
            if (s.endHour < 1 || s.endHour > 24) return bad("endHour must be 1-24", presenterId, correlationId, "POST");
            if (s.startHour >= s.endHour) return bad("startHour < endHour required", presenterId, correlationId, "POST");
            String key = s.weekday+":"+s.startHour+":"+s.endHour;
            if (!dedup.add(key)) return bad("duplicate slot", presenterId, correlationId, "POST");
        }
        logger.info("Validated {} slots - Correlation ID: {}", body.slots.size(), correlationId);
        // Persist
        String psId = java.util.UUID.randomUUID().toString();
        PresenterSeminar ps = new PresenterSeminar();
        ps.setPresenterSeminarId(psId);
        ps.setPresenterId(presenterId);
        ps.setSeminarName(body.seminarName);
        seminars.save(ps);
        logger.debug("PresenterSeminar saved: {} - Correlation ID: {}", psId, correlationId);
        for (SeminarSlotDto s : body.slots) {
            PresenterSeminarSlot slot = new PresenterSeminarSlot();
            slot.setPresenterSeminarSlotId(java.util.UUID.randomUUID().toString());
            slot.setPresenterSeminarId(psId);
            slot.setWeekday(s.weekday);
            slot.setStartHour(s.startHour);
            slot.setEndHour(s.endHour);
            slots.save(slot);
            logger.debug("Slot saved: w={} {}-{} - Correlation ID: {}", s.weekday, s.startHour, s.endHour, correlationId);
        }
        var tile = toTile(ps);
        LoggerUtil.logApiResponse(logger, "POST", "/api/v1/presenters/"+presenterId+"/seminars", 201, "Created");
        logger.info("Seminar created for presenter {} - Correlation ID: {}", presenterId, correlationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(tile);
    }

    @DeleteMapping("/{seminarId}")
    public ResponseEntity<Object> delete(
                                         @PathVariable String presenterId,
                                         @PathVariable("seminarId") String psId) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("DELETE /api/v1/presenters/{}/seminars/{} - Correlation ID: {}", presenterId, psId, correlationId);
        LoggerUtil.logApiRequest(logger, "DELETE", "/api/v1/presenters/"+presenterId+"/seminars/"+psId, null);
        // No authentication required for POC
        logger.info("Processing seminar delete for presenter {} - Correlation ID: {}", presenterId, correlationId);
        if (!seminars.existsById(psId)) {
            LoggerUtil.logApiResponse(logger, "DELETE", "/api/v1/presenters/"+presenterId+"/seminars/"+psId, 404, "Not Found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Seminar tile not found"));
        }
        // remove slots then tile
        slots.findByPresenterSeminarIdOrderByWeekdayAscStartHourAsc(psId)
                .forEach(s -> slots.deleteById(s.getPresenterSeminarSlotId()));
        seminars.deleteById(psId);
        LoggerUtil.logApiResponse(logger, "DELETE", "/api/v1/presenters/"+presenterId+"/seminars/"+psId, 204, "Deleted");
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<Object> bad(String msg, String presenterId, String correlationId, String method) {
        LoggerUtil.logApiResponse(logger, method, "/api/v1/presenters/"+presenterId+"/seminars", 400, msg);
        logger.warn("Validation failed: {} - Correlation ID: {}", msg, correlationId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", msg));
    }

    private SeminarTileDto toTile(PresenterSeminar ps) {
        SeminarTileDto dto = new SeminarTileDto();
        dto.id = ps.getPresenterSeminarId();
        dto.presenterId = ps.getPresenterId();
        dto.createdAt = ps.getCreatedAt();
        dto.seminarName = ps.getSeminarName();
        dto.slots = slots.findByPresenterSeminarIdOrderByWeekdayAscStartHourAsc(ps.getPresenterSeminarId())
                .stream().map(s -> {
                    SeminarSlotDto sd = new SeminarSlotDto();
                    sd.weekday = s.getWeekday();
                    sd.startHour = s.getStartHour();
                    sd.endHour = s.getEndHour();
                    return sd;
                }).collect(Collectors.toList());
        return dto;
    }

    public static class CreateRequest {
        public String seminarName; // optional free label
        public List<SeminarSlotDto> slots;
        @Override public String toString() { return "CreateRequest{"+"seminarName='"+seminarName+'\''+", slots="+(slots==null?0:slots.size())+'}'; }
    }
}


