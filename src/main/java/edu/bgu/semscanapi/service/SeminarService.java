package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.entity.Seminar;
import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.repository.SeminarRepository;
import edu.bgu.semscanapi.repository.UserRepository;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service class for Seminar business logic
 * Provides comprehensive logging for all operations
 */
@Service
@Transactional
public class SeminarService {
    
    private static final Logger logger = LoggerUtil.getLogger(SeminarService.class);
    
    @Autowired
    private SeminarRepository seminarRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    /**
     * Create a new seminar
     */
    public Seminar createSeminar(Seminar seminar) {
        logger.info("Creating new seminar: {}", seminar.getSeminarName());
        LoggerUtil.setSeminarId(seminar.getSeminarId());
        
        try {
            // Validate presenter exists
            Optional<User> presenter = userRepository.findById(seminar.getPresenterId());
            if (presenter.isEmpty()) {
                logger.error("Presenter not found: {}", seminar.getPresenterId());
                throw new IllegalArgumentException("Presenter not found: " + seminar.getPresenterId());
            }
            
            if (presenter.get().getRole() != User.UserRole.PRESENTER) {
                logger.error("User is not a presenter: {}", seminar.getPresenterId());
                throw new IllegalArgumentException("User is not a presenter: " + seminar.getPresenterId());
            }
            
            // Check if seminar code already exists
            if (seminarRepository.existsBySeminarCode(seminar.getSeminarCode())) {
                logger.error("Seminar code already exists: {}", seminar.getSeminarCode());
                throw new IllegalArgumentException("Seminar code already exists: " + seminar.getSeminarCode());
            }
            
            // Generate ID if not provided
            if (seminar.getSeminarId() == null || seminar.getSeminarId().isEmpty()) {
                seminar.setSeminarId("seminar-" + UUID.randomUUID().toString().substring(0, 8));
                logger.debug("Generated seminar ID: {}", seminar.getSeminarId());
            }
            
            Seminar savedSeminar = seminarRepository.save(seminar);
            logger.info("Seminar created successfully: {} with ID: {}", savedSeminar.getSeminarName(), savedSeminar.getSeminarId());
            LoggerUtil.logBusinessEvent(logger, "SEMINAR_CREATED", 
                "Seminar: " + savedSeminar.getSeminarName() + ", Presenter: " + savedSeminar.getPresenterId());
            
            return savedSeminar;
            
        } catch (Exception e) {
            logger.error("Failed to create seminar: {}", seminar.getSeminarName(), e);
            throw e;
        } finally {
            LoggerUtil.clearKey("seminarId");
        }
    }
    
    /**
     * Get seminar by ID
     */
    @Transactional(readOnly = true)
    public Optional<Seminar> getSeminarById(String seminarId) {
        logger.debug("Retrieving seminar by ID: {}", seminarId);
        LoggerUtil.setSeminarId(seminarId);
        
        try {
            Optional<Seminar> seminar = seminarRepository.findById(seminarId);
            if (seminar.isPresent()) {
                logger.debug("Seminar found: {}", seminar.get().getSeminarName());
                LoggerUtil.logDatabaseOperation(logger, "SELECT", "seminars", seminarId);
            } else {
                logger.warn("Seminar not found: {}", seminarId);
            }
            return seminar;
        } finally {
            LoggerUtil.clearKey("seminarId");
        }
    }
    
    /**
     * Get seminar by code
     */
    @Transactional(readOnly = true)
    public Optional<Seminar> getSeminarByCode(String seminarCode) {
        logger.debug("Retrieving seminar by code: {}", seminarCode);
        
        try {
            Optional<Seminar> seminar = seminarRepository.findBySeminarCode(seminarCode);
            if (seminar.isPresent()) {
                logger.debug("Seminar found by code: {} - {}", seminarCode, seminar.get().getSeminarName());
                LoggerUtil.setSeminarId(seminar.get().getSeminarId());
                LoggerUtil.logDatabaseOperation(logger, "SELECT", "seminars", seminar.get().getSeminarId());
            } else {
                logger.warn("Seminar not found by code: {}", seminarCode);
            }
            return seminar;
        } finally {
            LoggerUtil.clearKey("seminarId");
        }
    }
    
    /**
     * Get all seminars
     */
    @Transactional(readOnly = true)
    public List<Seminar> getAllSeminars() {
        logger.info("Retrieving all seminars");
        
        try {
            List<Seminar> seminars = seminarRepository.findAll();
            logger.info("Retrieved {} seminars", seminars.size());
            LoggerUtil.logDatabaseOperation(logger, "SELECT_ALL", "seminars", "all");
            return seminars;
        } catch (Exception e) {
            logger.error("Failed to retrieve all seminars", e);
            throw e;
        }
    }
    
    /**
     * Get seminars by presenter
     */
    @Transactional(readOnly = true)
    public List<Seminar> getSeminarsByPresenter(String presenterId) {
        logger.info("Retrieving seminars for presenter: {}", presenterId);
        LoggerUtil.setUserId(presenterId);
        
        try {
            List<Seminar> seminars = seminarRepository.findByPresenterId(presenterId);
            logger.info("Retrieved {} seminars for presenter: {}", seminars.size(), presenterId);
            LoggerUtil.logDatabaseOperation(logger, "SELECT_BY_PRESENTER", "seminars", presenterId);
            return seminars;
        } catch (Exception e) {
            logger.error("Failed to retrieve seminars for presenter: {}", presenterId, e);
            throw e;
        } finally {
            LoggerUtil.clearKey("userId");
        }
    }
    
    /**
     * Update seminar
     */
    public Seminar updateSeminar(String seminarId, Seminar updatedSeminar) {
        logger.info("Updating seminar: {}", seminarId);
        LoggerUtil.setSeminarId(seminarId);
        
        try {
            Optional<Seminar> existingSeminar = seminarRepository.findById(seminarId);
            if (existingSeminar.isEmpty()) {
                logger.error("Seminar not found for update: {}", seminarId);
                throw new IllegalArgumentException("Seminar not found: " + seminarId);
            }
            
            Seminar seminar = existingSeminar.get();
            
            // Update fields
            if (updatedSeminar.getSeminarName() != null) {
                seminar.setSeminarName(updatedSeminar.getSeminarName());
            }
            if (updatedSeminar.getDescription() != null) {
                seminar.setDescription(updatedSeminar.getDescription());
            }
            if (updatedSeminar.getPresenterId() != null) {
                // Validate new presenter
                Optional<User> presenter = userRepository.findById(updatedSeminar.getPresenterId());
                if (presenter.isEmpty() || presenter.get().getRole() != User.UserRole.PRESENTER) {
                    logger.error("Invalid presenter for seminar update: {}", updatedSeminar.getPresenterId());
                    throw new IllegalArgumentException("Invalid presenter: " + updatedSeminar.getPresenterId());
                }
                seminar.setPresenterId(updatedSeminar.getPresenterId());
            }
            
            Seminar savedSeminar = seminarRepository.save(seminar);
            logger.info("Seminar updated successfully: {}", savedSeminar.getSeminarName());
            LoggerUtil.logBusinessEvent(logger, "SEMINAR_UPDATED", 
                "Seminar: " + savedSeminar.getSeminarName() + ", ID: " + seminarId);
            
            return savedSeminar;
            
        } catch (Exception e) {
            logger.error("Failed to update seminar: {}", seminarId, e);
            throw e;
        } finally {
            LoggerUtil.clearKey("seminarId");
        }
    }
    
    /**
     * Delete seminar
     */
    public void deleteSeminar(String seminarId) {
        logger.info("Deleting seminar: {}", seminarId);
        LoggerUtil.setSeminarId(seminarId);
        
        try {
            if (!seminarRepository.existsById(seminarId)) {
                logger.error("Seminar not found for deletion: {}", seminarId);
                throw new IllegalArgumentException("Seminar not found: " + seminarId);
            }
            
            seminarRepository.deleteById(seminarId);
            logger.info("Seminar deleted successfully: {}", seminarId);
            LoggerUtil.logBusinessEvent(logger, "SEMINAR_DELETED", "Seminar ID: " + seminarId);
            
        } catch (Exception e) {
            logger.error("Failed to delete seminar: {}", seminarId, e);
            throw e;
        } finally {
            LoggerUtil.clearKey("seminarId");
        }
    }
    
    /**
     * Search seminars by name
     */
    @Transactional(readOnly = true)
    public List<Seminar> searchSeminarsByName(String name) {
        logger.info("Searching seminars by name: {}", name);
        
        try {
            List<Seminar> seminars = seminarRepository.findByNameContainingIgnoreCase(name);
            logger.info("Found {} seminars matching name: {}", seminars.size(), name);
            LoggerUtil.logDatabaseOperation(logger, "SEARCH_BY_NAME", "seminars", name);
            return seminars;
        } catch (Exception e) {
            logger.error("Failed to search seminars by name: {}", name, e);
            throw e;
        }
    }
}
