package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.repository.UserRepository;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
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
    
    /**
     * Find user by username
     */
    @Transactional(readOnly = true)
    public Optional<User> findUserByUsername(String username) {
        String normalized = normalizeUsername(username);
        logger.debug("Finding user by username: {}", normalized);

        try {
            Optional<User> user = userRepository.findByBguUsername(normalized);
            if (user.isPresent()) {
                logger.info("User found by username: {}", normalized);
                return user;
            }

            Optional<User> caseInsensitive = userRepository.findByBguUsernameIgnoreCase(normalized);
            caseInsensitive.ifPresent(value -> logger.info("User found by case-insensitive username match: {}", normalized));
            if (caseInsensitive.isPresent()) {
                return caseInsensitive;
            }

            logger.warn("User not found by username: {}", normalized);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Error finding user by username: {}", normalized, e);
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
            List<User> presenters = userRepository.findByIsPresenterTrue();
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
            List<User> students = userRepository.findByIsParticipantTrue();
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
    public boolean userExists(String username) {
        String normalized = normalizeUsername(username);
        logger.debug("Checking if user exists by username: {}", normalized);

        try {
            boolean exists = userRepository.existsByBguUsernameIgnoreCase(normalized);
            logger.debug("User exists: {} = {}", normalized, exists);
            return exists;
        } catch (Exception e) {
            logger.error("Error checking if user exists by username: {}", normalized, e);
            return false;
        }
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
    
    /**
     * Check if email exists
     */
    @Transactional(readOnly = true)
    public boolean emailExists(String email) {
        logger.debug("Checking if email exists: {}", email);
        
        try {
            boolean exists = userRepository.existsByEmailIgnoreCase(email);
            logger.debug("Email exists: {} = {}", email, exists);
            return exists;
        } catch (Exception e) {
            logger.error("Error checking if email exists: {}", email, e);
            return false;
        }
    }
}