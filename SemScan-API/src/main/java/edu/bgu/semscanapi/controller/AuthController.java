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
import java.util.Set;

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

        // Validate request is not null and has required fields
        if (request == null || request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            LoggerUtil.logApiRequest(logger, "POST", endpoint, "{\"username\":\"null\"}");
            LoginResponse response = LoginResponse.failure("Username is required");
            LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.BAD_REQUEST.value(), response.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        if (request.getPassword() == null || request.getPassword().isEmpty()) {
            LoggerUtil.logApiRequest(logger, "POST", endpoint, String.format("{\"username\":\"%s\"}", request.getUsername()));
            LoginResponse response = LoginResponse.failure("Password is required");
            LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.BAD_REQUEST.value(), response.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        String sanitizedRequest = String.format("{\"username\":\"%s\"}", request.getUsername());
        LoggerUtil.logApiRequest(logger, "POST", endpoint, sanitizedRequest);

        try {
            // Log login attempt to database
            if (databaseLoggerService != null) {
                databaseLoggerService.logAction("INFO", "LOGIN_ATTEMPT",
                        String.format("Login attempt for user %s", request.getUsername()),
                        request.getUsername(),
                        String.format("username=%s", request.getUsername()));
            }

            // TEST BYPASS: Allow specific test users to login without BGU SOAP validation
            // These users exist in DB with proper data for testing purposes
            Set<String> testUsers = Set.of(
                    "testphd1", "testphd2", "testphd3",
                    "testmsc1", "testmsc2", "testmsc3", "testmsc4", "testmsc5"
            );
            String tempUsername = request.getUsername().trim().toLowerCase(Locale.ROOT);
            if (tempUsername.contains("@")) {
                tempUsername = tempUsername.substring(0, tempUsername.indexOf('@'));
            }
            boolean authenticated;
            if (testUsers.contains(tempUsername) && "Test123!".equals(request.getPassword())) {
                authenticated = true;
                logger.info("TEST BYPASS: Allowing test user {} to login", tempUsername);
            } else {
                authenticated = bguAuthSoapService.validateUser(request.getUsername(), request.getPassword());
            }

            if (!authenticated) {
                logger.warn("BGU authentication failed for user: {}", request.getUsername());
                // Log failed login to database
                if (databaseLoggerService != null) {
                    databaseLoggerService.logAction("WARN", "LOGIN_FAILED",
                            String.format("Login failed for user %s - Invalid credentials", request.getUsername()),
                            request.getUsername(),
                            String.format("username=%s,reason=INVALID_CREDENTIALS", request.getUsername()));
                }
                LoginResponse response = LoginResponse.failure("Invalid username or password");
                LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.UNAUTHORIZED.value(), response.getMessage());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            logger.debug("BGU authentication succeeded for username: {}", request.getUsername());

            // Check if user exists in database (don't auto-create)
            String normalizedUsername = request.getUsername() != null ? request.getUsername().trim().toLowerCase(Locale.ROOT) : "";
            String baseUsername = normalizedUsername.contains("@")
                    ? normalizedUsername.substring(0, normalizedUsername.indexOf('@'))
                    : normalizedUsername;

            Optional<User> existingUser = userRepository.findByBguUsername(baseUsername);

            String bguUsername = baseUsername;
            String email = normalizedUsername.contains("@") ? normalizedUsername : (baseUsername + "@bgu.ac.il");
            boolean isFirstTime = existingUser.isEmpty();
            boolean isPresenter = existingUser.map(u -> Boolean.TRUE.equals(u.getIsPresenter())).orElse(false);
            boolean isParticipant = existingUser.map(u -> Boolean.TRUE.equals(u.getIsParticipant())).orElse(false);

            LoggerUtil.setBguUsername(bguUsername);

            LoginResponse response = LoginResponse.success(
                    bguUsername,
                    email,
                    isFirstTime,
                    isPresenter,
                    isParticipant);

            // Log successful login to database
            if (databaseLoggerService != null) {
                // Get user if exists for supervisor info, otherwise use null
                User user = existingUser.orElse(null);
                boolean hasSupervisor = user != null && user.getSupervisor() != null;
                
                databaseLoggerService.logBusinessEvent("LOGIN_SUCCESS",
                        String.format("Login successful for user %s (isFirstTime=%s, isPresenter=%s, isParticipant=%s, hasSupervisor=%s)",
                                bguUsername, isFirstTime,
                                isPresenter,
                                isParticipant,
                                hasSupervisor),
                        bguUsername);
            }

            LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.OK.value(), response.getMessage());
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            LoggerUtil.logError(logger, "Unexpected error during BGU authentication", ex);
            // Log unexpected error to database with full stack trace
            if (databaseLoggerService != null) {
                databaseLoggerService.logError("LOGIN_UNEXPECTED_ERROR",
                        String.format("Unexpected error during login for user %s: %s",
                                request != null ? request.getUsername() : "null", ex.getMessage()),
                        ex, request != null ? request.getUsername() : null,
                        String.format("errorType=%s,httpStatus=500", ex.getClass().getSimpleName()));
            }
            LoginResponse response = LoginResponse.failure("Authentication service unavailable");
            LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            LoggerUtil.clearContext();
        }
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


