package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.entity.PresenterApiKey;
import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.repository.PresenterApiKeyRepository;
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
 * Service class for Authentication business logic
 * Provides comprehensive logging for all operations
 */
@Service
@Transactional
public class AuthenticationService {
    
    private static final Logger logger = LoggerUtil.getLogger(AuthenticationService.class);
    
    @Autowired
    private PresenterApiKeyRepository presenterApiKeyRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    /**
     * Validate API key and return presenter information
     */
    @Transactional(readOnly = true)
    public Optional<User> validateApiKey(String apiKey) {
        logger.debug("Validating API key: {}", apiKey != null ? "***" + apiKey.substring(Math.max(0, apiKey.length() - 4)) : "null");
        
        try {
            if (apiKey == null || apiKey.trim().isEmpty()) {
                logger.warn("API key is null or empty");
                return Optional.empty();
            }
            
            Optional<PresenterApiKey> presenterApiKey = presenterApiKeyRepository.findActiveApiKey(apiKey);
            if (presenterApiKey.isEmpty()) {
                logger.warn("Invalid or inactive API key provided");
                LoggerUtil.logAuthentication(logger, "API_KEY_VALIDATION_FAILED", null, apiKey);
                return Optional.empty();
            }
            
            String presenterId = presenterApiKey.get().getPresenterId();
            Optional<User> presenter = userRepository.findById(presenterId);
            
            if (presenter.isEmpty()) {
                logger.error("Presenter not found for valid API key: {}", presenterId);
                LoggerUtil.logAuthentication(logger, "PRESENTER_NOT_FOUND", presenterId, apiKey);
                return Optional.empty();
            }
            
            if (presenter.get().getRole() != User.UserRole.PRESENTER) {
                logger.error("User is not a presenter: {}", presenterId);
                LoggerUtil.logAuthentication(logger, "INVALID_USER_ROLE", presenterId, apiKey);
                return Optional.empty();
            }
            
            logger.info("API key validation successful for presenter: {}", presenterId);
            LoggerUtil.setUserId(presenterId);
            LoggerUtil.logAuthentication(logger, "API_KEY_VALIDATION_SUCCESS", presenterId, apiKey);
            
            return presenter;
            
        } catch (Exception e) {
            logger.error("Error validating API key", e);
            LoggerUtil.logAuthentication(logger, "API_KEY_VALIDATION_ERROR", null, apiKey);
            return Optional.empty();
        } finally {
            LoggerUtil.clearKey("userId");
        }
    }
    
    /**
     * Generate API key for presenter
     */
    public PresenterApiKey generateApiKey(String presenterId) {
        logger.info("Generating API key for presenter: {}", presenterId);
        LoggerUtil.setUserId(presenterId);
        
        try {
            // Validate presenter exists
            Optional<User> presenter = userRepository.findById(presenterId);
            if (presenter.isEmpty()) {
                logger.error("Presenter not found: {}", presenterId);
                throw new IllegalArgumentException("Presenter not found: " + presenterId);
            }
            
            if (presenter.get().getRole() != User.UserRole.PRESENTER) {
                logger.error("User is not a presenter: {}", presenterId);
                throw new IllegalArgumentException("User is not a presenter: " + presenterId);
            }
            
            // Generate new API key
            String apiKey = presenterId + "-api-key-" + UUID.randomUUID().toString().substring(0, 8);
            
            PresenterApiKey presenterApiKey = new PresenterApiKey();
            presenterApiKey.setApiKeyId("api-key-" + UUID.randomUUID().toString().substring(0, 8));
            presenterApiKey.setPresenterId(presenterId);
            presenterApiKey.setApiKey(apiKey);
            presenterApiKey.setIsActive(true);
            presenterApiKey.setCreatedAt(java.time.LocalDateTime.now());
            
            PresenterApiKey savedApiKey = presenterApiKeyRepository.save(presenterApiKey);
            logger.info("API key generated successfully for presenter: {}", presenterId);
            LoggerUtil.logAuthentication(logger, "API_KEY_GENERATED", presenterId, apiKey);
            
            return savedApiKey;
            
        } catch (Exception e) {
            logger.error("Failed to generate API key for presenter: {}", presenterId, e);
            throw e;
        } finally {
            LoggerUtil.clearKey("userId");
        }
    }
    
    /**
     * Get API keys for presenter
     */
    @Transactional(readOnly = true)
    public List<PresenterApiKey> getApiKeysForPresenter(String presenterId) {
        logger.info("Retrieving API keys for presenter: {}", presenterId);
        LoggerUtil.setUserId(presenterId);
        
        try {
            List<PresenterApiKey> apiKeys = presenterApiKeyRepository.findByPresenterId(presenterId);
            logger.info("Retrieved {} API keys for presenter: {}", apiKeys.size(), presenterId);
            LoggerUtil.logDatabaseOperation(logger, "SELECT_API_KEYS", "presenter_api_keys", presenterId);
            return apiKeys;
        } catch (Exception e) {
            logger.error("Failed to retrieve API keys for presenter: {}", presenterId, e);
            throw e;
        } finally {
            LoggerUtil.clearKey("userId");
        }
    }
    
    /**
     * Get active API keys for presenter
     */
    @Transactional(readOnly = true)
    public List<PresenterApiKey> getActiveApiKeysForPresenter(String presenterId) {
        logger.info("Retrieving active API keys for presenter: {}", presenterId);
        LoggerUtil.setUserId(presenterId);
        
        try {
            List<PresenterApiKey> apiKeys = presenterApiKeyRepository.findActiveApiKeysByPresenter(presenterId);
            logger.info("Retrieved {} active API keys for presenter: {}", apiKeys.size(), presenterId);
            LoggerUtil.logDatabaseOperation(logger, "SELECT_ACTIVE_API_KEYS", "presenter_api_keys", presenterId);
            return apiKeys;
        } catch (Exception e) {
            logger.error("Failed to retrieve active API keys for presenter: {}", presenterId, e);
            throw e;
        } finally {
            LoggerUtil.clearKey("userId");
        }
    }
    
    /**
     * Deactivate API key
     */
    public void deactivateApiKey(String apiKeyId) {
        logger.info("Deactivating API key: {}", apiKeyId);
        
        try {
            Optional<PresenterApiKey> apiKey = presenterApiKeyRepository.findById(apiKeyId);
            if (apiKey.isEmpty()) {
                logger.error("API key not found: {}", apiKeyId);
                throw new IllegalArgumentException("API key not found: " + apiKeyId);
            }
            
            PresenterApiKey presenterApiKey = apiKey.get();
            presenterApiKey.setIsActive(false);
            presenterApiKeyRepository.save(presenterApiKey);
            
            logger.info("API key deactivated successfully: {}", apiKeyId);
            LoggerUtil.logAuthentication(logger, "API_KEY_DEACTIVATED", presenterApiKey.getPresenterId(), presenterApiKey.getApiKey());
            
        } catch (Exception e) {
            logger.error("Failed to deactivate API key: {}", apiKeyId, e);
            throw e;
        }
    }
    
    /**
     * Activate API key
     */
    public void activateApiKey(String apiKeyId) {
        logger.info("Activating API key: {}", apiKeyId);
        
        try {
            Optional<PresenterApiKey> apiKey = presenterApiKeyRepository.findById(apiKeyId);
            if (apiKey.isEmpty()) {
                logger.error("API key not found: {}", apiKeyId);
                throw new IllegalArgumentException("API key not found: " + apiKeyId);
            }
            
            PresenterApiKey presenterApiKey = apiKey.get();
            presenterApiKey.setIsActive(true);
            presenterApiKeyRepository.save(presenterApiKey);
            
            logger.info("API key activated successfully: {}", apiKeyId);
            LoggerUtil.logAuthentication(logger, "API_KEY_ACTIVATED", presenterApiKey.getPresenterId(), presenterApiKey.getApiKey());
            
        } catch (Exception e) {
            logger.error("Failed to activate API key: {}", apiKeyId, e);
            throw e;
        }
    }
    
    /**
     * Delete API key
     */
    public void deleteApiKey(String apiKeyId) {
        logger.info("Deleting API key: {}", apiKeyId);
        
        try {
            if (!presenterApiKeyRepository.existsById(apiKeyId)) {
                logger.error("API key not found for deletion: {}", apiKeyId);
                throw new IllegalArgumentException("API key not found: " + apiKeyId);
            }
            
            presenterApiKeyRepository.deleteById(apiKeyId);
            logger.info("API key deleted successfully: {}", apiKeyId);
            LoggerUtil.logAuthentication(logger, "API_KEY_DELETED", null, null);
            
        } catch (Exception e) {
            logger.error("Failed to delete API key: {}", apiKeyId, e);
            throw e;
        }
    }
    
    /**
     * Get API key statistics for presenter
     */
    @Transactional(readOnly = true)
    public ApiKeyStats getApiKeyStats(String presenterId) {
        logger.info("Calculating API key statistics for presenter: {}", presenterId);
        LoggerUtil.setUserId(presenterId);
        
        try {
            long totalKeys = presenterApiKeyRepository.countByPresenterId(presenterId);
            long activeKeys = presenterApiKeyRepository.countActiveApiKeysByPresenter(presenterId);
            long inactiveKeys = totalKeys - activeKeys;
            
            ApiKeyStats stats = new ApiKeyStats(totalKeys, activeKeys, inactiveKeys);
            logger.info("Presenter {} API key stats: Total={}, Active={}, Inactive={}", 
                presenterId, totalKeys, activeKeys, inactiveKeys);
            
            return stats;
        } catch (Exception e) {
            logger.error("Failed to calculate API key statistics for presenter: {}", presenterId, e);
            throw e;
        } finally {
            LoggerUtil.clearKey("userId");
        }
    }
    
    /**
     * Inner class for API key statistics
     */
    public static class ApiKeyStats {
        private final long totalKeys;
        private final long activeKeys;
        private final long inactiveKeys;
        
        public ApiKeyStats(long totalKeys, long activeKeys, long inactiveKeys) {
            this.totalKeys = totalKeys;
            this.activeKeys = activeKeys;
            this.inactiveKeys = inactiveKeys;
        }
        
        // Getters
        public long getTotalKeys() { return totalKeys; }
        public long getActiveKeys() { return activeKeys; }
        public long getInactiveKeys() { return inactiveKeys; }
    }
    
    /**
     * Get the first available presenter ID for POC
     * In production, this would come from the authenticated user context
     */
    @Transactional(readOnly = true)
    public String getFirstPresenterId() {
        logger.debug("Getting first available presenter ID for POC");
        
        // Get the first presenter from the database
        List<User> presenters = userRepository.findByRole(User.UserRole.PRESENTER);
        
        if (!presenters.isEmpty()) {
            String presenterId = presenters.get(0).getUserId();
            logger.info("Found first presenter for POC: {} ({} {})", 
                       presenterId, presenters.get(0).getFirstName(), presenters.get(0).getLastName());
            return presenterId;
        } else {
            throw new RuntimeException("No presenters found in database - cannot proceed without valid presenter");
        }
    }
}
