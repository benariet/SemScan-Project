package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.dto.UserExistsRequest;
import edu.bgu.semscanapi.dto.UserProfileResponse;
import edu.bgu.semscanapi.dto.UserProfileUpdateRequest;
import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.repository.UserRepository;
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

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
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

            if (userOptional.isEmpty()) {
                logger.warn("User not found for profile update request: {}", sanitizedUsername);
                UserProfileResponse response = UserProfileResponse.failure("User not found");
                LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.NOT_FOUND.value(), response.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            User user = userOptional.get();
            if (!normalizedUsername.equals(user.getBguUsername())) {
                user.setBguUsername(normalizedUsername);
            }

            boolean updated = applyUpdates(user, request);

            if (updated) {
                userRepository.save(user);
                logger.info("Updated user profile for username {}", user.getBguUsername());
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
                    deriveParticipationPreference(user));

            LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.OK.value(), response.getMessage());
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            LoggerUtil.logError(logger, "Failed to update user profile", ex);
            UserProfileResponse response = UserProfileResponse.failure("Failed to update profile");
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

        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (!normalizedEmail.equalsIgnoreCase(user.getEmail())) {
            user.setEmail(normalizedEmail);
            changed = true;
        }

        if (request.getDegree() != null && request.getDegree() != user.getDegree()) {
            user.setDegree(request.getDegree());
            changed = true;
        }

        UserProfileUpdateRequest.ParticipationPreference preference = request.getParticipationPreference();
        if (preference != null) {
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
                    deriveParticipationPreference(user));

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
}


