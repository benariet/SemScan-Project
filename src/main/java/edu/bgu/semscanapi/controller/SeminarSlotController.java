package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.dto.PresenterHomeResponse;
import edu.bgu.semscanapi.service.PresenterHomeService;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/slots")
@CrossOrigin(origins = "*")
public class SeminarSlotController {

    private static final Logger logger = LoggerUtil.getLogger(SeminarSlotController.class);

    private final PresenterHomeService presenterHomeService;

    public SeminarSlotController(PresenterHomeService presenterHomeService) {
        this.presenterHomeService = presenterHomeService;
    }

    @GetMapping
    public ResponseEntity<List<PresenterHomeResponse.SlotCard>> listAllSlots() {
        LoggerUtil.generateAndSetCorrelationId();
        String endpoint = "/api/v1/slots";
        LoggerUtil.logApiRequest(logger, "GET", endpoint, null);

        try {
            List<PresenterHomeResponse.SlotCard> slots = presenterHomeService.getAllSlots();
            LoggerUtil.logApiResponse(logger, "GET", endpoint, 200, "Slot catalog size=" + slots.size());
            return ResponseEntity.ok(slots);
        } catch (Exception ex) {
            LoggerUtil.logError(logger, "Failed to retrieve slot catalog", ex);
            LoggerUtil.logApiResponse(logger, "GET", endpoint, 500, "Internal Server Error");
            throw ex;
        } finally {
            LoggerUtil.clearContext();
        }
    }
}


