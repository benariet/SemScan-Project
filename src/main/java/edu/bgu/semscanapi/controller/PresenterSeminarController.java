package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.dto.SeminarSlotDto;
import edu.bgu.semscanapi.dto.SeminarTileDto;
import edu.bgu.semscanapi.entity.PresenterSeminar;
import edu.bgu.semscanapi.entity.PresenterSeminarSlot;
import edu.bgu.semscanapi.entity.Seminar;
import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.repository.PresenterSeminarRepository;
import edu.bgu.semscanapi.repository.PresenterSeminarSlotRepository;
import edu.bgu.semscanapi.repository.SeminarRepository;
import edu.bgu.semscanapi.repository.UserRepository;
import edu.bgu.semscanapi.service.DatabaseLoggerService;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/presenters/{presenterId}/seminars")
@CrossOrigin(origins = "*")
public class PresenterSeminarController {

    private static final Logger logger = LoggerUtil.getLogger(PresenterSeminarController.class);

    @Autowired private PresenterSeminarRepository seminars;
    @Autowired private PresenterSeminarSlotRepository slots;
    @Autowired private SeminarRepository seminarRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DatabaseLoggerService databaseLoggerService;

    @GetMapping
    public ResponseEntity<Object> list(
                                       @PathVariable Long presenterId) {
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
                                         @PathVariable Long presenterId,
                                         @RequestBody CreateRequest body) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("POST /api/v1/presenters/{}/seminars - Correlation ID: {}", presenterId, correlationId);
        LoggerUtil.logApiRequest(logger, "POST", "/api/v1/presenters/"+presenterId+"/seminars", body != null ? body.toString() : null);
        // No authentication required for POC
        logger.info("Processing seminar create for presenter {} - Correlation ID: {}", presenterId, correlationId);
        if (body == null) {
            LoggerUtil.logApiResponse(logger, "POST", "/api/v1/presenters/"+presenterId+"/seminars", 400, "Body required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Request body is required"));
        }
        if (body.seminarId == null && (body.seminarName == null || body.seminarName.isBlank())) {
            LoggerUtil.logApiResponse(logger, "POST", "/api/v1/presenters/"+presenterId+"/seminars", 400, "Seminar name required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Seminar name is required when seminarId is not provided"));
        }
        if (body.slots == null || body.slots.isEmpty()) {
            LoggerUtil.logApiResponse(logger, "POST", "/api/v1/presenters/"+presenterId+"/seminars", 400, "No slots provided");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "At least one slot is required"));
        }
        if (body.maxEnrollmentCapacity != null && body.maxEnrollmentCapacity < 0) {
            return bad("maxEnrollmentCapacity must be >= 0", presenterId, correlationId, "POST");
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
        PresenterSeminar ps = new PresenterSeminar();
        ps.setPresenterId(presenterId);

        PresenterCreateResult result;
        try {
            result = createOrAttachSeminar(correlationId, presenterId, body);
        } catch (IllegalArgumentException ex) {
            LoggerUtil.logApiResponse(logger, "POST", "/api/v1/presenters/"+presenterId+"/seminars", 400, ex.getMessage());
            
            String payload = String.format("correlationId=%s", LoggerUtil.getCurrentCorrelationId());
            databaseLoggerService.logError("PRESENTER_SEMINAR_VALIDATION_ERROR", ex.getMessage(), ex, presenterId, payload);
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            logger.error("Failed to create presenter seminar tile", ex);
            LoggerUtil.logApiResponse(logger, "POST", "/api/v1/presenters/"+presenterId+"/seminars", 500, "Internal Server Error");
            
            String payload = String.format("correlationId=%s", LoggerUtil.getCurrentCorrelationId());
            databaseLoggerService.logError("PRESENTER_SEMINAR_CREATION_ERROR", "Failed to create presenter seminar", ex, presenterId, payload);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create presenter seminar"));
        }

        ps.setSeminarId(result.seminarId());
        ps.setInstanceName(body.instanceName != null && !body.instanceName.isBlank()
                ? body.instanceName
                : result.seminarName());
        String description = body.instanceDescription != null && !body.instanceDescription.isBlank()
                ? body.instanceDescription
                : result.description();
        ps.setInstanceDescription(description);

        seminars.save(ps);
        Long psId = ps.getPresenterSeminarId(); // Get the auto-generated ID
        logger.debug("PresenterSeminar saved: {} - Correlation ID: {}", psId, correlationId);
        for (SeminarSlotDto s : body.slots) {
            PresenterSeminarSlot slot = new PresenterSeminarSlot();
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
                                         @PathVariable Long presenterId,
                                         @PathVariable("seminarId") Long psId) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("DELETE /api/v1/presenters/{}/seminars/{} - Correlation ID: {}", presenterId, psId, correlationId);
        LoggerUtil.logApiRequest(logger, "DELETE", "/api/v1/presenters/"+presenterId+"/seminars/"+psId, null);
        // No authentication required for POC
        logger.info("Processing seminar delete for presenter {} - Correlation ID: {}", presenterId, correlationId);
        Optional<PresenterSeminar> seminar = seminars.findByPresenterSeminarId(psId);
        if (seminar.isEmpty()) {
            LoggerUtil.logApiResponse(logger, "DELETE", "/api/v1/presenters/"+presenterId+"/seminars/"+psId, 404, "Not Found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Seminar tile not found"));
        }
        // remove slots then tile
        slots.findByPresenterSeminarIdOrderByWeekdayAscStartHourAsc(psId)
                .forEach(s -> slots.delete(s));
        seminars.delete(seminar.get());
        LoggerUtil.logApiResponse(logger, "DELETE", "/api/v1/presenters/"+presenterId+"/seminars/"+psId, 204, "Deleted");
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<Object> bad(String msg, Long presenterId, String correlationId, String method) {
        LoggerUtil.logApiResponse(logger, method, "/api/v1/presenters/"+presenterId+"/seminars", 400, msg);
        logger.warn("Validation failed: {} - Correlation ID: {}", msg, correlationId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", msg));
    }

    private SeminarTileDto toTile(PresenterSeminar ps) {
        SeminarTileDto dto = new SeminarTileDto();
        dto.id = ps.getPresenterSeminarId();
        dto.presenterId = ps.getPresenterId();
        dto.seminarId = ps.getSeminarId();
        dto.createdAt = ps.getCreatedAt();
        dto.instanceName = ps.getInstanceName();
        dto.instanceDescription = ps.getInstanceDescription();
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
        public Long seminarId;
        public String seminarName;
        public String seminarDescription;
        public Integer maxEnrollmentCapacity;
        public String instanceName;
        public String instanceDescription;
        public String tileDescription;
        public List<SeminarSlotDto> slots;

        @Override public String toString() {
            return "CreateRequest{"+
                    "seminarId="+seminarId+
                    ", seminarName='"+seminarName+'\''+
                    ", maxEnrollmentCapacity="+maxEnrollmentCapacity+
                    ", instanceName='"+instanceName+'\''+
                    ", slots="+(slots==null?0:slots.size())+
                    '}'; }
    }

    private PresenterCreateResult createOrAttachSeminar(String correlationId, Long presenterId, CreateRequest body) {
        if (body.seminarId != null) {
            Optional<Seminar> seminarOpt = seminarRepository.findById(body.seminarId);
            if (seminarOpt.isEmpty()) {
                throw new IllegalArgumentException("Seminar not found: " + body.seminarId);
            }
            Seminar seminar = seminarOpt.get();
            if (!Objects.equals(seminar.getPresenterId(), presenterId)) {
                throw new IllegalArgumentException("Seminar " + body.seminarId + " does not belong to presenter " + presenterId);
            }
            return new PresenterCreateResult(seminar.getSeminarId(), seminar.getSeminarName(), seminar.getDescription());
        }

        // create new seminar
        User presenter = userRepository.findById(presenterId)
                .orElseThrow(() -> new IllegalArgumentException("Presenter not found: " + presenterId));
        if (!Boolean.TRUE.equals(presenter.getIsPresenter())) {
            throw new IllegalArgumentException("User " + presenterId + " is not a presenter");
        }

        Seminar seminar = new Seminar();
        seminar.setSeminarName(body.seminarName);
        seminar.setDescription(body.seminarDescription);
        seminar.setPresenterId(presenterId);
        
        // Set capacity based on presenter's degree: PhD = 1, MSc = 2
        if (presenter.getDegree() != null) {
            int capacity = presenter.getDegree() == User.Degree.PhD ? 1 : 2;
            seminar.setMaxEnrollmentCapacity(capacity);
            logger.info("Setting max enrollment capacity to {} based on presenter degree {} - Correlation ID: {}", 
                capacity, presenter.getDegree(), correlationId);
        } else {
            // If degree is not set, use the provided value or default
            seminar.setMaxEnrollmentCapacity(body.maxEnrollmentCapacity);
            if (body.maxEnrollmentCapacity == null) {
                logger.warn("Presenter {} has no degree set and no capacity provided - Correlation ID: {}", 
                    presenterId, correlationId);
            }
        }

        Seminar saved = seminarRepository.save(seminar);
        logger.info("Created new seminar {} for presenter {} - Correlation ID: {}", saved.getSeminarId(), presenterId, correlationId);
        return new PresenterCreateResult(saved.getSeminarId(), saved.getSeminarName(), saved.getDescription());
    }

    private record PresenterCreateResult(Long seminarId, String seminarName, String description) {}
}


