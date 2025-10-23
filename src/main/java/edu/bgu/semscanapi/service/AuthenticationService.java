package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.repository.UserRepository;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service class for Authentication business logic
 * Provides comprehensive logging for all operations
 */
@Service
@Transactional
public class AuthenticationService {
    
    private static final Logger logger = LoggerUtil.getLogger(AuthenticationService.class);
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private IdMappingService idMappingService;
    
    /**
     * Find user by ID
     */
    @Transactional(readOnly = true)
    public Optional<User> findUserById(String userId) {
        logger.debug("Finding user by ID: {}", userId);
        
        try {
            // Convert String ID to Long ID using IdMappingService
            Optional<Long> numericId = idMappingService.getUserIdFromStringId(userId);
            if (numericId.isEmpty()) {
                logger.warn("User not found with string ID: {}", userId);
                return Optional.empty();
            }
            
            Optional<User> user = userRepository.findById(numericId.get());
            if (user.isPresent()) {
                logger.info("User found: {}", userId);
            } else {
                logger.warn("User not found: {}", userId);
            }
            return user;
        } catch (Exception e) {
            logger.error("Error finding user: {}", userId, e);
            return Optional.empty();
        }
    }
    
    /**
     * Find user by email
     */
    @Transactional(readOnly = true)
    public Optional<User> findUserByEmail(String email) {
        logger.debug("Finding user by email: {}", email);
        
        try {
            Optional<User> user = userRepository.findByEmail(email);
            if (user.isPresent()) {
                logger.info("User found by email: {}", email);
            } else {
                logger.warn("User not found by email: {}", email);
            }
            return user;
        } catch (Exception e) {
            logger.error("Error finding user by email: {}", email, e);
            return Optional.empty();
        }
    }
    
    /**
     * Find presenters
     */
    @Transactional(readOnly = true)
    public List<User> findPresenters() {
        logger.debug("Finding all presenters");
        
        try {
            List<User> presenters = userRepository.findPresenters();
            logger.info("Found {} presenters", presenters.size());
            return presenters;
        } catch (Exception e) {
            logger.error("Error finding presenters", e);
            return List.of();
        }
    }
    
    /**
     * Find students
     */
    @Transactional(readOnly = true)
    public List<User> findStudents() {
        logger.debug("Finding all students");
        
        try {
            List<User> students = userRepository.findStudents();
            logger.info("Found {} students", students.size());
            return students;
        } catch (Exception e) {
            logger.error("Error finding students", e);
            return List.of();
        }
    }
    
    /**
     * Check if user exists
     */
    @Transactional(readOnly = true)
    public boolean userExists(String userId) {
        logger.debug("Checking if user exists: {}", userId);
        
        try {
            // Convert String ID to Long ID using IdMappingService
            Optional<Long> numericId = idMappingService.getUserIdFromStringId(userId);
            if (numericId.isEmpty()) {
                logger.debug("User not found with string ID: {}", userId);
                return false;
            }
            
            boolean exists = userRepository.existsById(numericId.get());
            logger.debug("User exists: {} = {}", userId, exists);
            return exists;
        } catch (Exception e) {
            logger.error("Error checking if user exists: {}", userId, e);
            return false;
        }
    }
    
    /**
     * Check if email exists
     */
    @Transactional(readOnly = true)
    public boolean emailExists(String email) {
        logger.debug("Checking if email exists: {}", email);
        
        try {
            boolean exists = userRepository.existsByEmail(email);
            logger.debug("Email exists: {} = {}", email, exists);
            return exists;
        } catch (Exception e) {
            logger.error("Error checking if email exists: {}", email, e);
            return false;
        }
    }
}