package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.dto.LoginRequest;
import edu.bgu.semscanapi.dto.LoginResponse;
import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.repository.UserRepository;
import edu.bgu.semscanapi.service.BguAuthSoapService;
import edu.bgu.semscanapi.util.LoggerUtil;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
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

    public AuthController(BguAuthSoapService bguAuthSoapService, UserRepository userRepository) {
        this.bguAuthSoapService = bguAuthSoapService;
        this.userRepository = userRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoggerUtil.generateAndSetCorrelationId();
        String endpoint = "/api/v1/auth/login";

        String sanitizedRequest = String.format("{\"username\":\"%s\"}", request.getUsername());
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
            logger.debug("Resolved local user: id={}, bguUsername={}, email={}, isPresenter={}, isParticipant={}",
                    user.getId(), user.getBguUsername(), user.getEmail(), user.getIsPresenter(), user.getIsParticipant());

            LoggerUtil.setBguUsername(user.getBguUsername());

            LoginResponse response = LoginResponse.success(
                    user.getBguUsername(),
                    user.getEmail(),
                    false,
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
                logger.debug("Updating bguUsername for user {} from {} to {}", user.getId(), user.getBguUsername(), baseUsername);
                user.setBguUsername(baseUsername);
                changed = true;
            }
            if (user.getEmail() == null || user.getEmail().isBlank()) {
                String derivedEmail = deriveEmailFromUsername(normalizedUsername);
                logger.debug("Setting missing email for user {} to {}", user.getId(), derivedEmail);
                user.setEmail(derivedEmail);
                changed = true;
            }

            if (changed) {
                user = userRepository.save(user);
                logger.debug("Persisted updates for user {}", user.getId());
            } else {
                logger.debug("No changes detected for user {}", user.getId());
            }
            return user;
        }

        User newUser = new User();
        newUser.setBguUsername(baseUsername);
        newUser.setFirstName("User");
        newUser.setLastName(baseUsername);
        newUser.setEmail(deriveEmailFromUsername(normalizedUsername));
        newUser.setIsPresenter(false);
        newUser.setIsParticipant(false);

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
}


