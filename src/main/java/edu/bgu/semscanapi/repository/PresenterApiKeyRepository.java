package edu.bgu.semscanapi.repository;

import edu.bgu.semscanapi.entity.PresenterApiKey;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for PresenterApiKey entity operations
 * Provides data access methods with comprehensive logging
 */
@Repository
public interface PresenterApiKeyRepository extends JpaRepository<PresenterApiKey, String> {
    
    Logger logger = LoggerUtil.getLogger(PresenterApiKeyRepository.class);
    
    /**
     * Find API key by key value
     */
    Optional<PresenterApiKey> findByApiKey(String apiKey);
    
    /**
     * Find API keys by presenter ID
     */
    List<PresenterApiKey> findByPresenterId(String presenterId);
    
    /**
     * Find active API keys by presenter ID
     */
    @Query("SELECT p FROM PresenterApiKey p WHERE p.presenterId = :presenterId AND p.isActive = true")
    List<PresenterApiKey> findActiveApiKeysByPresenter(@Param("presenterId") String presenterId);
    
    /**
     * Find active API key by key value
     */
    @Query("SELECT p FROM PresenterApiKey p WHERE p.apiKey = :apiKey AND p.isActive = true")
    Optional<PresenterApiKey> findActiveApiKey(@Param("apiKey") String apiKey);
    
    /**
     * Check if API key exists and is active
     */
    @Query("SELECT COUNT(p) > 0 FROM PresenterApiKey p WHERE p.apiKey = :apiKey AND p.isActive = true")
    boolean existsByApiKeyAndIsActive(@Param("apiKey") String apiKey);
    
    /**
     * Find all active API keys
     */
    @Query("SELECT p FROM PresenterApiKey p WHERE p.isActive = true")
    List<PresenterApiKey> findAllActiveApiKeys();
    
    /**
     * Count active API keys by presenter
     */
    @Query("SELECT COUNT(p) FROM PresenterApiKey p WHERE p.presenterId = :presenterId AND p.isActive = true")
    long countActiveApiKeysByPresenter(@Param("presenterId") String presenterId);
    
    /**
     * Find API keys created after a specific date
     */
    @Query("SELECT p FROM PresenterApiKey p WHERE p.createdAt > :createdAfter")
    List<PresenterApiKey> findApiKeysCreatedAfter(@Param("createdAfter") java.time.LocalDateTime createdAfter);
    
    /**
     * Count API keys by presenter ID
     */
    long countByPresenterId(String presenterId);
}
