package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.entity.Seminar;
import edu.bgu.semscanapi.service.SeminarService;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * REST Controller for Seminar operations
 * Provides comprehensive logging for all API endpoints
 */
@RestController
@RequestMapping("/api/v1/seminars")
public class SeminarController {
    
    private static final Logger logger = LoggerUtil.getLogger(SeminarController.class);
    
    @Autowired
    private SeminarService seminarService;
    
    /**
     * Create a new seminar
     */
    @PostMapping
    public ResponseEntity<Seminar> createSeminar(@RequestBody Seminar seminar) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Creating seminar - Name: {}, Code: {}", seminar.getSeminarName(), seminar.getSeminarCode());
        LoggerUtil.logApiRequest(logger, "POST", "/api/v1/seminars", seminar.toString());
        
        try {
            Seminar createdSeminar = seminarService.createSeminar(seminar);
            logger.info("Seminar created successfully - ID: {}, Name: {}", 
                createdSeminar.getSeminarId(), createdSeminar.getSeminarName());
            LoggerUtil.logApiResponse(logger, "POST", "/api/v1/seminars", 201, createdSeminar.toString());
            
            return ResponseEntity.status(HttpStatus.CREATED).body(createdSeminar);
            
        } catch (IllegalArgumentException e) {
            logger.error("Invalid seminar data: {}", e.getMessage());
            LoggerUtil.logApiResponse(logger, "POST", "/api/v1/seminars", 400, "Bad Request: " + e.getMessage());
            return ResponseEntity.badRequest().build();
            
        } catch (Exception e) {
            logger.error("Failed to create seminar", e);
            LoggerUtil.logError(logger, "Failed to create seminar", e);
            LoggerUtil.logApiResponse(logger, "POST", "/api/v1/seminars", 500, "Internal Server Error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Get seminar by ID
     */
    @GetMapping("/{seminarId}")
    public ResponseEntity<Seminar> getSeminarById(@PathVariable String seminarId) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Retrieving seminar by ID: {}", seminarId);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/seminars/" + seminarId, null);
        
        try {
            Optional<Seminar> seminar = seminarService.getSeminarById(seminarId);
            
            if (seminar.isPresent()) {
                logger.info("Seminar found - ID: {}, Name: {}", seminarId, seminar.get().getSeminarName());
                LoggerUtil.logApiResponse(logger, "GET", "/api/v1/seminars/" + seminarId, 200, seminar.get().toString());
                return ResponseEntity.ok(seminar.get());
            } else {
                logger.warn("Seminar not found: {}", seminarId);
                LoggerUtil.logApiResponse(logger, "GET", "/api/v1/seminars/" + seminarId, 404, "Not Found");
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            logger.error("Failed to retrieve seminar: {}", seminarId, e);
            LoggerUtil.logError(logger, "Failed to retrieve seminar", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/seminars/" + seminarId, 500, "Internal Server Error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Get seminar by code
     */
    @GetMapping("/code/{seminarCode}")
    public ResponseEntity<Seminar> getSeminarByCode(@PathVariable String seminarCode) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Retrieving seminar by code: {}", seminarCode);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/seminars/code/" + seminarCode, null);
        
        try {
            Optional<Seminar> seminar = seminarService.getSeminarByCode(seminarCode);
            
            if (seminar.isPresent()) {
                logger.info("Seminar found by code - Code: {}, Name: {}", seminarCode, seminar.get().getSeminarName());
                LoggerUtil.logApiResponse(logger, "GET", "/api/v1/seminars/code/" + seminarCode, 200, seminar.get().toString());
                return ResponseEntity.ok(seminar.get());
            } else {
                logger.warn("Seminar not found by code: {}", seminarCode);
                LoggerUtil.logApiResponse(logger, "GET", "/api/v1/seminars/code/" + seminarCode, 404, "Not Found");
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            logger.error("Failed to retrieve seminar by code: {}", seminarCode, e);
            LoggerUtil.logError(logger, "Failed to retrieve seminar by code", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/seminars/code/" + seminarCode, 500, "Internal Server Error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Get all seminars
     */
    @GetMapping
    public ResponseEntity<List<Seminar>> getAllSeminars() {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Retrieving all seminars");
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/seminars", null);
        
        try {
            List<Seminar> seminars = seminarService.getAllSeminars();
            logger.info("Retrieved {} seminars", seminars.size());
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/seminars", 200, "List of " + seminars.size() + " seminars");
            
            return ResponseEntity.ok(seminars);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve all seminars", e);
            LoggerUtil.logError(logger, "Failed to retrieve all seminars", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/seminars", 500, "Internal Server Error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Get seminars by presenter
     */
    @GetMapping("/presenter/{presenterId}")
    public ResponseEntity<List<Seminar>> getSeminarsByPresenter(@PathVariable String presenterId) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Retrieving seminars for presenter: {}", presenterId);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/seminars/presenter/" + presenterId, null);
        
        try {
            List<Seminar> seminars = seminarService.getSeminarsByPresenter(presenterId);
            logger.info("Retrieved {} seminars for presenter: {}", seminars.size(), presenterId);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/seminars/presenter/" + presenterId, 200, 
                "List of " + seminars.size() + " seminars for presenter");
            
            return ResponseEntity.ok(seminars);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve seminars for presenter: {}", presenterId, e);
            LoggerUtil.logError(logger, "Failed to retrieve seminars for presenter", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/seminars/presenter/" + presenterId, 500, "Internal Server Error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Update seminar
     */
    @PutMapping("/{seminarId}")
    public ResponseEntity<Seminar> updateSeminar(@PathVariable String seminarId, @RequestBody Seminar seminar) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Updating seminar: {}", seminarId);
        LoggerUtil.logApiRequest(logger, "PUT", "/api/v1/seminars/" + seminarId, seminar.toString());
        
        try {
            Seminar updatedSeminar = seminarService.updateSeminar(seminarId, seminar);
            logger.info("Seminar updated successfully - ID: {}, Name: {}", 
                seminarId, updatedSeminar.getSeminarName());
            LoggerUtil.logApiResponse(logger, "PUT", "/api/v1/seminars/" + seminarId, 200, updatedSeminar.toString());
            
            return ResponseEntity.ok(updatedSeminar);
            
        } catch (IllegalArgumentException e) {
            logger.error("Invalid seminar update data: {}", e.getMessage());
            LoggerUtil.logApiResponse(logger, "PUT", "/api/v1/seminars/" + seminarId, 400, "Bad Request: " + e.getMessage());
            return ResponseEntity.badRequest().build();
            
        } catch (Exception e) {
            logger.error("Failed to update seminar: {}", seminarId, e);
            LoggerUtil.logError(logger, "Failed to update seminar", e);
            LoggerUtil.logApiResponse(logger, "PUT", "/api/v1/seminars/" + seminarId, 500, "Internal Server Error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Delete seminar
     */
    @DeleteMapping("/{seminarId}")
    public ResponseEntity<Void> deleteSeminar(@PathVariable String seminarId) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Deleting seminar: {}", seminarId);
        LoggerUtil.logApiRequest(logger, "DELETE", "/api/v1/seminars/" + seminarId, null);
        
        try {
            seminarService.deleteSeminar(seminarId);
            logger.info("Seminar deleted successfully: {}", seminarId);
            LoggerUtil.logApiResponse(logger, "DELETE", "/api/v1/seminars/" + seminarId, 204, "No Content");
            
            return ResponseEntity.noContent().build();
            
        } catch (IllegalArgumentException e) {
            logger.error("Seminar not found for deletion: {}", e.getMessage());
            LoggerUtil.logApiResponse(logger, "DELETE", "/api/v1/seminars/" + seminarId, 404, "Not Found");
            return ResponseEntity.notFound().build();
            
        } catch (Exception e) {
            logger.error("Failed to delete seminar: {}", seminarId, e);
            LoggerUtil.logError(logger, "Failed to delete seminar", e);
            LoggerUtil.logApiResponse(logger, "DELETE", "/api/v1/seminars/" + seminarId, 500, "Internal Server Error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Search seminars by name
     */
    @GetMapping("/search")
    public ResponseEntity<List<Seminar>> searchSeminarsByName(@RequestParam String name) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Searching seminars by name: {}", name);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/seminars/search?name=" + name, null);
        
        try {
            List<Seminar> seminars = seminarService.searchSeminarsByName(name);
            logger.info("Found {} seminars matching name: {}", seminars.size(), name);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/seminars/search", 200, 
                "List of " + seminars.size() + " matching seminars");
            
            return ResponseEntity.ok(seminars);
            
        } catch (Exception e) {
            logger.error("Failed to search seminars by name: {}", name, e);
            LoggerUtil.logError(logger, "Failed to search seminars by name", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/seminars/search", 500, "Internal Server Error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            LoggerUtil.clearContext();
        }
    }
}
