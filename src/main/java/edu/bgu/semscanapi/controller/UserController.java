package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.dto.FcmTokenRequest;
import edu.bgu.semscanapi.dto.UserExistsRequest;
import edu.bgu.semscanapi.dto.UserProfileResponse;
import edu.bgu.semscanapi.dto.UserProfileUpdateRequest;
import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.repository.UserRepository;
import edu.bgu.semscanapi.service.FcmService;
import edu.bgu.semscanapi.util.LoggerUtil;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Optional;

/**
 * Handles user profile updates pushed from the mobile application.
 */
@RestController
@RequestMapping("/api/v1/users")
@CrossOrigin(origins = "*")
public class UserController {

    private static final Logger logger = LoggerUtil.getLogger(UserController.class);

    private final UserRepository userRepository;
    private final FcmService fcmService;

    public UserController(UserRepository userRepository, FcmService fcmService) {
        this.userRepository = userRepository;
        this.fcmService = fcmService;
    }

    @PostMapping
    public ResponseEntity<UserProfileResponse> upsertUser(@Valid @RequestBody UserProfileUpdateRequest request) {
        LoggerUtil.generateAndSetCorrelationId();
        String endpoint = "/api/v1/users";

        String rawUsername = request.getBguUsername();
        String sanitizedUsername = rawUsername != null ? rawUsername.trim() : "";
        LoggerUtil.logApiRequest(logger, "POST", endpoint, "{\"bguUsername\": \"" + sanitizedUsername + "\"}");

        try {
            if (!StringUtils.hasText(sanitizedUsername)) {
                logger.warn("Missing bguUsername in profile update request");
                UserProfileResponse response = UserProfileResponse.failure("bguUsername is required");
                LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.BAD_REQUEST.value(), response.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            String normalizedUsername = sanitizedUsername.toLowerCase(Locale.ROOT);
            Optional<User> userOptional = userRepository.findByBguUsername(normalizedUsername);
            if (userOptional.isEmpty()) {
                userOptional = userRepository.findByBguUsernameIgnoreCase(sanitizedUsername);
            }

            User user;
            boolean isNewUser = false;

            if (userOptional.isEmpty()) {
                // User doesn't exist - create new user (true upsert behavior)
                logger.info("User not found for username: {}, creating new user", sanitizedUsername);
                user = new User();
                user.setBguUsername(normalizedUsername);
                isNewUser = true;
            } else {
                user = userOptional.get();
            }
            if (!normalizedUsername.equals(user.getBguUsername())) {
                user.setBguUsername(normalizedUsername);
            }

            boolean updated = applyUpdates(user, request);

            if (updated || isNewUser) {
                userRepository.save(user);
                if (isNewUser) {
                    logger.info("Created new user profile for username {}", user.getBguUsername());
                } else {
                    logger.info("Updated user profile for username {}", user.getBguUsername());
                }
            } else {
                logger.info("No profile changes detected for user username {}", user.getBguUsername());
            }

            UserProfileResponse response = UserProfileResponse.success(
                    user.getBguUsername(),
                    updated ? "Profile updated" : "No changes",
                    user.getEmail(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getDegree() != null ? user.getDegree().name() : null,
                    deriveParticipationPreference(user),
                    user.getNationalIdNumber());

            LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.OK.value(), response.getMessage());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            // Re-throw to be handled by GlobalExceptionHandler with proper error message
            throw ex;
        } catch (Exception ex) {
            LoggerUtil.logError(logger, "Failed to update user profile", ex);
            UserProfileResponse response = UserProfileResponse.failure("Failed to update profile: " + ex.getMessage());
            LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            LoggerUtil.clearContext();
        }
    }

    private boolean applyUpdates(User user, UserProfileUpdateRequest request) {
        boolean changed = false;

        if (StringUtils.hasText(request.getFirstName())) {
            String trimmed = request.getFirstName().trim();
            if (!trimmed.equals(user.getFirstName())) {
                user.setFirstName(trimmed);
                changed = true;
            }
        }

        if (StringUtils.hasText(request.getLastName())) {
            String trimmed = request.getLastName().trim();
            if (!trimmed.equals(user.getLastName())) {
                user.setLastName(trimmed);
                changed = true;
            }
        }

        // Handle email update - check for null before processing
        if (request.getEmail() != null) {
            String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
            if (!normalizedEmail.equalsIgnoreCase(user.getEmail())) {
                // Check if email already exists for another user
                Optional<User> existingUserWithEmail = userRepository.findByEmail(normalizedEmail);
                if (existingUserWithEmail.isPresent() && !existingUserWithEmail.get().getId().equals(user.getId())) {
                    logger.warn("Cannot update email to {} for user {} (id={}) - email already exists for user {} (id={})",
                            normalizedEmail, user.getBguUsername(), user.getId(),
                            existingUserWithEmail.get().getBguUsername(), existingUserWithEmail.get().getId());
                    throw new IllegalArgumentException("Email " + normalizedEmail + " is already registered to another user");
                }
                user.setEmail(normalizedEmail);
                changed = true;
            }
        } else if (user.getEmail() != null) {
            // If request email is null but user has an email, clear it
            user.setEmail(null);
            changed = true;
        }

        if (request.getDegree() != null && request.getDegree() != user.getDegree()) {
            user.setDegree(request.getDegree());
            changed = true;
        }

        if (StringUtils.hasText(request.getNationalIdNumber())) {
            String trimmed = request.getNationalIdNumber().trim();
            if (!trimmed.equals(user.getNationalIdNumber())) {
                logger.info("Updating national ID number for user {}: {} -> {}", 
                    user.getBguUsername(), user.getNationalIdNumber(), trimmed);
                user.setNationalIdNumber(trimmed);
                changed = true;
            }
        }

        // Handle seminar abstract update
        if (request.getSeminarAbstract() != null) {
            String trimmed = request.getSeminarAbstract().trim();
            if (!trimmed.equals(user.getSeminarAbstract())) {
                logger.info("Updating seminar abstract for user {}", user.getBguUsername());
                user.setSeminarAbstract(trimmed.isEmpty() ? null : trimmed);
                changed = true;
            }
        }

        UserProfileUpdateRequest.ParticipationPreference preference = request.getParticipationPreference();
        // Default to BOTH if not provided - users can be both presenter and participant
        if (preference == null) {
            preference = UserProfileUpdateRequest.ParticipationPreference.BOTH;
        }
        
        boolean desiredPresenter = preference == UserProfileUpdateRequest.ParticipationPreference.BOTH
                || preference == UserProfileUpdateRequest.ParticipationPreference.PRESENTER_ONLY;
        boolean desiredParticipant = preference == UserProfileUpdateRequest.ParticipationPreference.BOTH
                || preference == UserProfileUpdateRequest.ParticipationPreference.PARTICIPANT_ONLY;

        if (!Boolean.valueOf(desiredPresenter).equals(user.getIsPresenter())) {
            user.setIsPresenter(desiredPresenter);
            changed = true;
        }

        if (!Boolean.valueOf(desiredParticipant).equals(user.getIsParticipant())) {
            user.setIsParticipant(desiredParticipant);
            changed = true;
        }

        return changed;
    }

    @PostMapping("/exists")
    public ResponseEntity<UserExistsResponse> userExists(@Valid @RequestBody UserExistsRequest request) {
        LoggerUtil.generateAndSetCorrelationId();
        String endpoint = "/api/v1/users/exists";
        LoggerUtil.logApiRequest(logger, "POST", endpoint, "{\"username\":\"" + request.getUsername() + "\"}");

        try {
            String trimmed = request.getUsername().trim();
            String normalized = trimmed.toLowerCase(Locale.ROOT);

            boolean exists = userRepository.existsByBguUsernameIgnoreCase(trimmed)
                    || userRepository.existsByBguUsernameIgnoreCase(normalized);

            if (!exists && trimmed.contains("@")) {
                exists = userRepository.existsByEmailIgnoreCase(trimmed);
            }

            if (!exists && !trimmed.contains("@")) {
                String assumedEmail = normalized + "@bgu.ac.il";
                exists = userRepository.existsByEmailIgnoreCase(assumedEmail);
            }

            if (!exists) {
                exists = userRepository.findByBguUsername(normalized).isPresent();
            }

            UserExistsResponse response = new UserExistsResponse(exists);
            LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.OK.value(), "exists=" + exists);
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            LoggerUtil.logError(logger, "Failed to check user existence", ex);
            throw ex;
        } finally {
            LoggerUtil.clearContext();
        }
    }

    private record UserExistsResponse(boolean exists) {}

    @GetMapping("/username/{username}")
    public ResponseEntity<UserProfileResponse> getUserByUsername(@PathVariable String username) {
        LoggerUtil.generateAndSetCorrelationId();
        String endpoint = "/api/v1/users/username/" + username;
        LoggerUtil.logApiRequest(logger, "GET", endpoint, null);

        try {
            if (username == null || username.trim().isEmpty()) {
                LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.BAD_REQUEST.value(), "username required");
                return ResponseEntity.badRequest().build();
            }

            String trimmed = username.trim();
            Optional<User> userOpt = userRepository.findByBguUsername(trimmed.toLowerCase(Locale.ROOT));
            if (userOpt.isEmpty()) {
                userOpt = userRepository.findByBguUsername(trimmed);
            }
            if (userOpt.isEmpty()) {
                LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.NOT_FOUND.value(), "User not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            User user = userOpt.get();
            UserProfileResponse response = UserProfileResponse.success(
                    user.getBguUsername(),
                    "Profile fetched",
                    user.getEmail(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getDegree() != null ? user.getDegree().name() : null,
                    deriveParticipationPreference(user),
                    user.getNationalIdNumber(),
                    user.getSeminarAbstract());

            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.OK.value(), "Profile fetched");
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            LoggerUtil.logError(logger, "Failed to fetch user by username", ex);
            throw ex;
        } finally {
            LoggerUtil.clearContext();
        }
    }

    private UserProfileUpdateRequest.ParticipationPreference deriveParticipationPreference(User user) {
        boolean presenter = Boolean.TRUE.equals(user.getIsPresenter());
        boolean participant = Boolean.TRUE.equals(user.getIsParticipant());

        if (presenter && participant) {
            return UserProfileUpdateRequest.ParticipationPreference.BOTH;
        }
        if (presenter) {
            return UserProfileUpdateRequest.ParticipationPreference.PRESENTER_ONLY;
        }
        if (participant) {
            return UserProfileUpdateRequest.ParticipationPreference.PARTICIPANT_ONLY;
        }
        return null;
    }

    /**
     * Register FCM token for push notifications
     */
    @PostMapping("/{username}/fcm-token")
    public ResponseEntity<?> registerFcmToken(
            @PathVariable String username,
            @RequestBody FcmTokenRequest request) {
        LoggerUtil.generateAndSetCorrelationId();
        String endpoint = "/api/v1/users/" + username + "/fcm-token";
        LoggerUtil.logApiRequest(logger, "POST", endpoint, request.toString());

        try {
            if (username == null || username.trim().isEmpty()) {
                LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.BAD_REQUEST.value(), "username required");
                return ResponseEntity.badRequest().body(new FcmTokenResponse(false, "Username is required"));
            }

            if (request.getFcmToken() == null || request.getFcmToken().trim().isEmpty()) {
                LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.BAD_REQUEST.value(), "fcmToken required");
                return ResponseEntity.badRequest().body(new FcmTokenResponse(false, "FCM token is required"));
            }

            fcmService.registerToken(username.trim(), request.getFcmToken().trim(), request.getDeviceInfo());

            LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.OK.value(), "FCM token registered");
            return ResponseEntity.ok(new FcmTokenResponse(true, "FCM token registered successfully"));

        } catch (Exception ex) {
            LoggerUtil.logError(logger, "Failed to register FCM token", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new FcmTokenResponse(false, "Failed to register FCM token: " + ex.getMessage()));
        } finally {
            LoggerUtil.clearContext();
        }
    }

    /**
     * Remove FCM token (e.g., on logout)
     */
    @DeleteMapping("/{username}/fcm-token")
    public ResponseEntity<?> removeFcmToken(@PathVariable String username) {
        LoggerUtil.generateAndSetCorrelationId();
        String endpoint = "/api/v1/users/" + username + "/fcm-token";
        LoggerUtil.logApiRequest(logger, "DELETE", endpoint, null);

        try {
            if (username == null || username.trim().isEmpty()) {
                LoggerUtil.logApiResponse(logger, "DELETE", endpoint, HttpStatus.BAD_REQUEST.value(), "username required");
                return ResponseEntity.badRequest().body(new FcmTokenResponse(false, "Username is required"));
            }

            fcmService.removeToken(username.trim());

            LoggerUtil.logApiResponse(logger, "DELETE", endpoint, HttpStatus.OK.value(), "FCM token removed");
            return ResponseEntity.ok(new FcmTokenResponse(true, "FCM token removed successfully"));

        } catch (Exception ex) {
            LoggerUtil.logError(logger, "Failed to remove FCM token", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new FcmTokenResponse(false, "Failed to remove FCM token: " + ex.getMessage()));
        } finally {
            LoggerUtil.clearContext();
        }
    }

    private record FcmTokenResponse(boolean success, String message) {}
}


