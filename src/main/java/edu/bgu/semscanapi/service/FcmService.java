package edu.bgu.semscanapi.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import edu.bgu.semscanapi.config.FirebaseConfig;
import edu.bgu.semscanapi.entity.FcmToken;
import edu.bgu.semscanapi.repository.FcmTokenRepository;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing FCM tokens and sending push notifications
 */
@Service
public class FcmService {

    private static final Logger logger = LoggerUtil.getLogger(FcmService.class);

    @Autowired
    private FcmTokenRepository fcmTokenRepository;

    @Autowired
    private FirebaseConfig firebaseConfig;

    @Autowired(required = false)
    private DatabaseLoggerService databaseLoggerService;

    /**
     * Register or update FCM token for a user
     */
    @Transactional
    public void registerToken(String bguUsername, String token, String deviceInfo) {
        if (bguUsername == null || token == null) {
            logger.warn("Cannot register FCM token: username or token is null");
            return;
        }

        String normalizedUsername = bguUsername.toLowerCase().trim();

        Optional<FcmToken> existing = fcmTokenRepository.findByBguUsername(normalizedUsername);

        if (existing.isPresent()) {
            FcmToken fcmToken = existing.get();
            fcmToken.setFcmToken(token);
            fcmToken.setDeviceInfo(deviceInfo);
            fcmTokenRepository.save(fcmToken);
            logger.info("Updated FCM token for user: {}", normalizedUsername);
        } else {
            FcmToken fcmToken = new FcmToken(normalizedUsername, token, deviceInfo);
            fcmTokenRepository.save(fcmToken);
            logger.info("Registered new FCM token for user: {}", normalizedUsername);
        }

        logAction("FCM_TOKEN_REGISTERED",
                String.format("FCM token registered for user %s", normalizedUsername),
                normalizedUsername,
                String.format("deviceInfo=%s", deviceInfo != null ? deviceInfo : "unknown"));
    }

    /**
     * Remove FCM token for a user (e.g., on logout)
     */
    @Transactional
    public void removeToken(String bguUsername) {
        if (bguUsername == null) return;

        String normalizedUsername = bguUsername.toLowerCase().trim();
        fcmTokenRepository.deleteByBguUsername(normalizedUsername);
        logger.info("Removed FCM token for user: {}", normalizedUsername);

        logAction("FCM_TOKEN_REMOVED",
                String.format("FCM token removed for user %s (logout)", normalizedUsername),
                normalizedUsername,
                null);
    }

    /**
     * Send push notification to a user
     */
    public boolean sendNotification(String bguUsername, String title, String body, Map<String, String> data) {
        if (!firebaseConfig.isInitialized()) {
            logger.debug("Firebase not initialized, skipping push notification");
            return false;
        }

        if (bguUsername == null) {
            logger.warn("Cannot send notification: username is null");
            return false;
        }

        String normalizedUsername = bguUsername.toLowerCase().trim();
        Optional<FcmToken> tokenOpt = fcmTokenRepository.findByBguUsername(normalizedUsername);

        if (tokenOpt.isEmpty()) {
            logger.debug("No FCM token found for user: {}", normalizedUsername);
            return false;
        }

        FcmToken fcmTokenEntity = tokenOpt.get();
        String token = fcmTokenEntity.getFcmToken();

        try {
            Message.Builder messageBuilder = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build());

            if (data != null && !data.isEmpty()) {
                messageBuilder.putAllData(data);
            }

            String response = FirebaseMessaging.getInstance().send(messageBuilder.build());
            logger.info("Push notification sent to {}: {}", normalizedUsername, response);

            // Save the notification content to the database
            fcmTokenEntity.setLastNotificationTitle(title);
            fcmTokenEntity.setLastNotificationBody(body);
            fcmTokenEntity.setLastNotificationSentAt(LocalDateTime.now());
            fcmTokenRepository.save(fcmTokenEntity);

            logAction("FCM_NOTIFICATION_SENT",
                    String.format("Push notification sent to %s: %s", normalizedUsername, title),
                    normalizedUsername,
                    String.format("title=%s,response=%s", title, response));

            return true;

        } catch (FirebaseMessagingException e) {
            logger.error("Failed to send push notification to {}: {}", normalizedUsername, e.getMessage());

            // Handle invalid token
            if (e.getMessagingErrorCode() != null) {
                String errorCode = e.getMessagingErrorCode().name();
                if ("UNREGISTERED".equals(errorCode) || "INVALID_ARGUMENT".equals(errorCode)) {
                    logger.info("Removing invalid FCM token for user: {}", normalizedUsername);
                    removeToken(normalizedUsername);
                }
            }

            logError("FCM_NOTIFICATION_FAILED",
                    String.format("Failed to send push notification to %s: %s", normalizedUsername, e.getMessage()),
                    normalizedUsername,
                    String.format("title=%s,error=%s", title, e.getMessage()));

            return false;
        }
    }

    /**
     * Send approval/decline notification to presenter
     */
    public void sendApprovalNotification(String presenterUsername, Long slotId, String slotDate,
                                          boolean approved, String declineReason) {
        String title;
        String body;

        if (approved) {
            title = "Registration Approved!";
            body = String.format("Your supervisor approved your registration for %s", slotDate);
        } else {
            title = "Registration Declined";
            if (declineReason != null && !declineReason.trim().isEmpty()) {
                body = String.format("Your supervisor declined your registration. Reason: %s", declineReason);
            } else {
                body = "Your supervisor declined your registration.";
            }
        }

        Map<String, String> data = new HashMap<>();
        data.put("type", "APPROVAL");
        data.put("slotId", String.valueOf(slotId));
        data.put("approved", String.valueOf(approved));

        sendNotification(presenterUsername, title, body, data);
    }

    /**
     * Send waiting list promotion notification
     */
    public void sendPromotionNotification(String presenterUsername, Long slotId, String slotDate) {
        String title = "Slot Available!";
        String body = String.format("You've been promoted from the waiting list for %s. Check your email to confirm.", slotDate);

        Map<String, String> data = new HashMap<>();
        data.put("type", "PROMOTION");
        data.put("slotId", String.valueOf(slotId));

        sendNotification(presenterUsername, title, body, data);
    }

    /**
     * Send attendance reminder notification
     */
    public void sendAttendanceReminder(String presenterUsername, Long slotId, String slotDate, String timeRange) {
        String title = "Presentation Reminder";
        String body = String.format("Your presentation is coming up: %s at %s", slotDate, timeRange);

        Map<String, String> data = new HashMap<>();
        data.put("type", "REMINDER");
        data.put("slotId", String.valueOf(slotId));

        sendNotification(presenterUsername, title, body, data);
    }

    private void logAction(String tag, String message, String username, String payload) {
        if (databaseLoggerService != null) {
            databaseLoggerService.logAction("INFO", tag, message, username, payload);
        }
    }

    private void logError(String tag, String message, String username, String payload) {
        if (databaseLoggerService != null) {
            databaseLoggerService.logError(tag, message, null, username, payload);
        }
    }
}
