package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.dto.AccountSetupRequest;
import edu.bgu.semscanapi.dto.LoginRequest;
import edu.bgu.semscanapi.dto.LoginResponse;
import edu.bgu.semscanapi.entity.Supervisor;
import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.repository.SupervisorRepository;
import edu.bgu.semscanapi.repository.UserRepository;
import edu.bgu.semscanapi.service.BguAuthSoapService;
import edu.bgu.semscanapi.service.DatabaseLoggerService;
import edu.bgu.semscanapi.util.LoggerUtil;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Authentication controller delegates login attempts to the BGU SOAP service
 * and manages local user provisioning for the SemScan mobile application.
 */
@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private static final Logger logger = LoggerUtil.getLogger(AuthController.class);

    private final BguAuthSoapService bguAuthSoapService;
    private final UserRepository userRepository;
    
    @Autowired(required = false)
    private SupervisorRepository supervisorRepository;
    
    @Autowired(required = false)
    private DatabaseLoggerService databaseLoggerService;

    public AuthController(BguAuthSoapService bguAuthSoapService, UserRepository userRepository) {
        this.bguAuthSoapService = bguAuthSoapService;
        this.userRepository = userRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoggerUtil.generateAndSetCorrelationId();
        String endpoint = "/api/v1/auth/login";

        String sanitizedRequest = String.format("{\"username\":\"%s\"}", request != null ? request.getUsername() : "null");
        LoggerUtil.logApiRequest(logger, "POST", endpoint, sanitizedRequest);

        try {
            boolean authenticated = bguAuthSoapService.validateUser(request.getUsername(), request.getPassword());

            if (!authenticated) {
                logger.warn("BGU authentication failed for user: {}", request.getUsername());
                LoginResponse response = LoginResponse.failure("Invalid username or password");
                LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.UNAUTHORIZED.value(), response.getMessage());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            logger.debug("BGU authentication succeeded for username: {}", request.getUsername());

            User user = ensureLocalUser(request.getUsername());
            logger.debug("Resolved local user: id={}, bguUsername={}, email={}, isPresenter={}, isParticipant={}, supervisor={}",
                    user.getId(), user.getBguUsername(), user.getEmail(), user.getIsPresenter(), user.getIsParticipant(),
                    user.getSupervisor() != null ? user.getSupervisor().getEmail() : "null");

            LoggerUtil.setBguUsername(user.getBguUsername());

            // Check if this is a first-time user (no supervisor linked)
            boolean isFirstTime = user.getSupervisor() == null;

            LoginResponse response = LoginResponse.success(
                    user.getBguUsername(),
                    user.getEmail(),
                    isFirstTime,
                    Boolean.TRUE.equals(user.getIsPresenter()),
                    Boolean.TRUE.equals(user.getIsParticipant()));

            LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.OK.value(), response.getMessage());
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            LoggerUtil.logError(logger, "Unexpected error during BGU authentication", ex);
            LoginResponse response = LoginResponse.failure("Authentication service unavailable");
            LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            LoggerUtil.clearContext();
        }
    }

    private User ensureLocalUser(String username) {
        String normalizedUsername = username != null ? username.trim() : "";
        if (normalizedUsername.isEmpty()) {
            throw new IllegalArgumentException("Username must not be empty");
        }

        String canonicalUsername = normalizedUsername.toLowerCase(Locale.ROOT);
        String baseUsername = canonicalUsername.contains("@")
                ? canonicalUsername.substring(0, canonicalUsername.indexOf('@'))
                : canonicalUsername;

        logger.debug("ensureLocalUser - normalized={}, canonical={}, base={} for original username={}"
                +", checking repository...",
                normalizedUsername, canonicalUsername, baseUsername, username);

        Optional<User> existingUser = userRepository.findByBguUsername(canonicalUsername);
        if (existingUser.isEmpty() && !canonicalUsername.equals(baseUsername)) {
            existingUser = userRepository.findByBguUsername(baseUsername);
        }
        if (existingUser.isEmpty() && canonicalUsername.contains("@")) {
            existingUser = userRepository.findByEmail(canonicalUsername);
        }

        if (existingUser.isPresent()) {
            User user = existingUser.get();
            boolean changed = false;

            if (!baseUsername.equals(user.getBguUsername())) {
                // Prevent username conflicts: verify baseUsername isn't already assigned to a different user
                Optional<User> userWithBguUsername = userRepository.findByBguUsername(baseUsername);
                if (userWithBguUsername.isPresent() && !userWithBguUsername.get().getId().equals(user.getId())) {
                    // Username collision detected: another user already has this bguUsername - skip update to prevent data corruption
                    logger.warn("Cannot update bguUsername to {} for user {} (id={}) - username already exists for user {} (id={}). Keeping existing bguUsername.",
                            baseUsername, user.getEmail(), user.getId(),
                            userWithBguUsername.get().getEmail(), userWithBguUsername.get().getId());
                } else {
                    // Safe to update bguUsername
                    logger.debug("Updating bguUsername for user {} from {} to {}", user.getId(), user.getBguUsername(), baseUsername);
                    user.setBguUsername(baseUsername);
                    changed = true;
                }
            }
            if (user.getEmail() == null || user.getEmail().isBlank()) {
                String derivedEmail = deriveEmailFromUsername(normalizedUsername);
                // Check if derived email already exists for another user
                if (derivedEmail != null) {
                    Optional<User> userWithEmail = userRepository.findByEmail(derivedEmail);
                    if (userWithEmail.isPresent() && !userWithEmail.get().getId().equals(user.getId())) {
                        logger.warn("Cannot set email to {} for user {} (id={}) - email already exists for user {} (id={}). Keeping null email.",
                                derivedEmail, user.getBguUsername(), user.getId(),
                                userWithEmail.get().getBguUsername(), userWithEmail.get().getId());
                    } else {
                        logger.debug("Setting missing email for user {} to {}", user.getId(), derivedEmail);
                        user.setEmail(derivedEmail);
                        changed = true;
                    }
                }
            }

            if (changed) {
                user = userRepository.save(user);
                logger.debug("Persisted updates for user {}", user.getId());
            } else {
                logger.debug("No changes detected for user {}", user.getId());
            }
            return user;
        }

        // Before creating new user: check if derived email (username@bgu.ac.il) already exists to prevent duplicates
        String derivedEmail = deriveEmailFromUsername(normalizedUsername);
        if (derivedEmail != null) {
            Optional<User> existingUserByEmail = userRepository.findByEmail(derivedEmail);
            if (existingUserByEmail.isPresent()) {
                User userWithEmail = existingUserByEmail.get();
                logger.info("User with email {} already exists (id={}, bguUsername={}). Checking if bguUsername update is needed.",
                        derivedEmail, userWithEmail.getId(), userWithEmail.getBguUsername());
                
                // Only update bguUsername if it's different AND the new bguUsername doesn't already exist
                if (!baseUsername.equals(userWithEmail.getBguUsername())) {
                    // Prevent username conflicts: check if baseUsername is already assigned to a different user
                    Optional<User> userWithBguUsername = userRepository.findByBguUsername(baseUsername);
                    if (userWithBguUsername.isPresent() && !userWithBguUsername.get().getId().equals(userWithEmail.getId())) {
                        // Username collision: another user already has this bguUsername - return existing user without update
                        logger.warn("Cannot update bguUsername to {} for user {} (id={}) - username already exists for user {} (id={}). Returning existing user.",
                                baseUsername, userWithEmail.getEmail(), userWithEmail.getId(),
                                userWithBguUsername.get().getEmail(), userWithBguUsername.get().getId());
                        return userWithEmail;
                    }
                    
                    // No conflict: update existing user's bguUsername to normalized baseUsername
                    userWithEmail.setBguUsername(baseUsername);
                    userWithEmail = userRepository.save(userWithEmail);
                    logger.debug("Updated bguUsername for existing user {} to {}", userWithEmail.getId(), baseUsername);
                }
                return userWithEmail;
            }
        }

        // Final safety check before user creation: verify bguUsername doesn't already exist (prevents duplicate users)
        Optional<User> checkBguUsername = userRepository.findByBguUsername(baseUsername);
        if (checkBguUsername.isPresent()) {
            logger.warn("User with bguUsername {} already exists (id={}, email={}). Returning existing user instead of creating duplicate.",
                    baseUsername, checkBguUsername.get().getId(), checkBguUsername.get().getEmail());
            return checkBguUsername.get();
        }

        User newUser = new User();
        newUser.setBguUsername(baseUsername);
        newUser.setFirstName("User");
        newUser.setLastName(baseUsername);
        newUser.setEmail(derivedEmail);
        // Set default permissions: new users can be both presenter and participant (can be restricted later if needed)
        newUser.setIsPresenter(true);
        newUser.setIsParticipant(true);

        User savedUser = userRepository.save(newUser);
        logger.info("Provisioned new local user for username={}, stored as base={} (id={})", username, baseUsername, savedUser.getId());
        return savedUser;
    }

    private String deriveEmailFromUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }

        String trimmed = username.trim();
        if (trimmed.contains("@")) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        return trimmed.toLowerCase(Locale.ROOT) + "@bgu.ac.il";
    }

    /**
     * Complete account setup by linking supervisor to user
     * POST /api/v1/auth/setup
     */
    @PostMapping("/setup")
    @Transactional
    public ResponseEntity<Map<String, Object>> setupAccount(
            @Valid @RequestBody AccountSetupRequest request) {
        LoggerUtil.generateAndSetCorrelationId();
        String endpoint = "/api/v1/auth/setup";

        LoggerUtil.logApiRequest(logger, "POST", endpoint, 
                String.format("{\"supervisorName\":\"%s\",\"supervisorEmail\":\"%s\"}", 
                        request.getSupervisorName(), request.getSupervisorEmail()));

        try {
            // Get current user from context (should be set by authentication filter or passed as parameter)
            // For now, we'll need to get it from the request - but typically this would come from security context
            // Since we don't have authentication filter yet, we'll need to pass username in request or use a different approach
            
            // TODO: Get username from security context when authentication is implemented
            // For now, we'll need to add username to AccountSetupRequest or use a different endpoint structure
            
            logger.error("Account setup endpoint called but username not available in request. " +
                    "Need to implement security context or add username to request.");
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Username not provided", "message", 
                            "Account setup requires authentication. Please include username in request or implement security context."));
            
        } catch (Exception ex) {
            LoggerUtil.logError(logger, "Unexpected error during account setup", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Account setup failed", "message", ex.getMessage()));
        } finally {
            LoggerUtil.clearContext();
        }
    }

    /**
     * Complete account setup by linking supervisor to user
     * POST /api/v1/auth/setup/{username}
     */
    @PostMapping("/setup/{username}")
    @Transactional
    public ResponseEntity<Map<String, Object>> setupAccountForUser(
            @PathVariable String username,
            @Valid @RequestBody AccountSetupRequest request) {
        LoggerUtil.generateAndSetCorrelationId();
        String endpoint = "/api/v1/auth/setup/" + username;

        LoggerUtil.logApiRequest(logger, "POST", endpoint, 
                String.format("{\"supervisorName\":\"%s\",\"supervisorEmail\":\"%s\"}", 
                        request.getSupervisorName(), request.getSupervisorEmail()));

        try {
            LoggerUtil.setBguUsername(username);

            // Normalize username
            String normalizedUsername = username != null ? username.trim().toLowerCase(Locale.ROOT) : "";
            if (normalizedUsername.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Invalid username", "message", "Username cannot be empty"));
            }

            // Remove @domain if present
            String baseUsername = normalizedUsername.contains("@")
                    ? normalizedUsername.substring(0, normalizedUsername.indexOf('@'))
                    : normalizedUsername;

            // Find user
            Optional<User> userOpt = userRepository.findByBguUsername(baseUsername);
            if (userOpt.isEmpty()) {
                logger.warn("Account setup failed: user not found: {}", baseUsername);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "User not found", "message", 
                                "User with username " + baseUsername + " does not exist. Please login first."));
            }

            User user = userOpt.get();

            // Check if supervisor is already set
            if (user.getSupervisor() != null) {
                logger.info("Account setup: user {} already has supervisor linked: {}", 
                        baseUsername, user.getSupervisor().getEmail());
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Account already set up",
                        "supervisorName", user.getSupervisor().getName(),
                        "supervisorEmail", user.getSupervisor().getEmail()
                ));
            }

            // Find or create supervisor
            Supervisor supervisor;
            Optional<Supervisor> existingSupervisor = supervisorRepository != null 
                    ? supervisorRepository.findByEmail(request.getSupervisorEmail().trim().toLowerCase(Locale.ROOT))
                    : Optional.empty();

            if (existingSupervisor.isPresent()) {
                supervisor = existingSupervisor.get();
                logger.info("Using existing supervisor: id={}, name={}, email={}", 
                        supervisor.getId(), supervisor.getName(), supervisor.getEmail());
            } else {
                // Create new supervisor
                supervisor = new Supervisor();
                supervisor.setName(request.getSupervisorName().trim());
                supervisor.setEmail(request.getSupervisorEmail().trim().toLowerCase(Locale.ROOT));
                
                if (supervisorRepository != null) {
                    supervisor = supervisorRepository.save(supervisor);
                    logger.info("Created new supervisor: id={}, name={}, email={}", 
                            supervisor.getId(), supervisor.getName(), supervisor.getEmail());
                } else {
                    logger.error("SupervisorRepository is null - cannot save supervisor");
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("error", "Service unavailable", "message", 
                                    "Supervisor repository not configured"));
                }
            }

            // Link supervisor to user
            user.setSupervisor(supervisor);
            user = userRepository.save(user);

            logger.info("Account setup completed for user {}: linked to supervisor {} ({})", 
                    baseUsername, supervisor.getName(), supervisor.getEmail());

            if (databaseLoggerService != null) {
                try {
                    databaseLoggerService.logAction("INFO", "ACCOUNT_SETUP_COMPLETED",
                            String.format("User %s linked to supervisor %s (%s)", 
                                    baseUsername, supervisor.getName(), supervisor.getEmail()),
                            baseUsername, String.format("supervisorId=%d,supervisorEmail=%s", 
                                    supervisor.getId(), supervisor.getEmail()));
                } catch (Exception logEx) {
                    logger.warn("Failed to log account setup to database", logEx);
                }
            }

            LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.OK.value(), "Account setup completed");
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Account setup completed successfully",
                    "supervisorName", supervisor.getName(),
                    "supervisorEmail", supervisor.getEmail()
            ));

        } catch (Exception ex) {
            LoggerUtil.logError(logger, "Unexpected error during account setup", ex);
            if (databaseLoggerService != null) {
                try {
                    databaseLoggerService.logError("ACCOUNT_SETUP_FAILED",
                            String.format("Account setup failed for user %s: %s", username, ex.getMessage()),
                            ex, username, String.format("supervisorEmail=%s", request.getSupervisorEmail()));
                } catch (Exception logEx) {
                    // Ignore logging errors
                }
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Account setup failed", "message", ex.getMessage()));
        } finally {
            LoggerUtil.clearContext();
        }
    }
}


