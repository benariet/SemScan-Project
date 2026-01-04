package org.example.semscan.service;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.example.semscan.utils.Logger;
import org.example.semscan.utils.NotificationHelper;
import org.example.semscan.utils.PreferencesManager;

import java.util.Map;

/**
 * Firebase Cloud Messaging service for handling push notifications.
 * Handles incoming messages and token refresh events.
 */
public class SemScanMessagingService extends FirebaseMessagingService {

    private static final String TAG = "SemScanMessaging";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Logger.d(TAG, "FCM token refreshed");

        // Store the token locally
        PreferencesManager prefsManager = PreferencesManager.getInstance(this);
        prefsManager.setFcmToken(token);

        // If user is logged in, send the token to server
        String username = prefsManager.getBguUsername();
        if (username != null && !username.isEmpty()) {
            sendTokenToServer(this, username, token);
        }
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Logger.d(TAG, "FCM message received from: " + remoteMessage.getFrom());

        // Handle data payload
        Map<String, String> data = remoteMessage.getData();
        String type = data.get("type");
        String slotIdStr = data.get("slotId");
        long slotId = 0;
        if (slotIdStr != null) {
            try {
                slotId = Long.parseLong(slotIdStr);
            } catch (NumberFormatException e) {
                Logger.w(TAG, "Invalid slotId in FCM message: " + slotIdStr);
            }
        }

        // Handle notification payload
        RemoteMessage.Notification notification = remoteMessage.getNotification();
        if (notification != null) {
            String title = notification.getTitle();
            String body = notification.getBody();

            if (title != null && body != null) {
                showNotificationByType(type, title, body, slotId);
            }
        } else if (data.containsKey("title") && data.containsKey("body")) {
            // Fallback to data payload for title/body
            String title = data.get("title");
            String body = data.get("body");
            showNotificationByType(type, title, body, slotId);
        }
    }

    /**
     * Shows notification based on the notification type.
     */
    private void showNotificationByType(String type, String title, String body, long slotId) {
        if (type == null) {
            type = "GENERAL";
        }

        switch (type) {
            case "APPROVAL":
                NotificationHelper.showApprovalNotification(this, title, body, slotId);
                break;

            case "PROMOTION":
                NotificationHelper.showPromotionNotification(this, title, body, slotId);
                break;

            case "REMINDER":
                NotificationHelper.showReminderNotification(this, title, body, slotId);
                break;

            default:
                // Default to approval channel for unknown types
                NotificationHelper.showApprovalNotification(this, title, body, slotId);
                break;
        }
    }

    /**
     * Sends the FCM token to the backend server.
     * This is called when token is refreshed while user is logged in.
     */
    public static void sendTokenToServer(Context context, String username, String token) {
        if (username == null || username.isEmpty() || token == null || token.isEmpty()) {
            Logger.w(TAG, "Cannot send token to server: missing username or token");
            return;
        }

        // Use a background thread to send the token
        new Thread(() -> {
            try {
                org.example.semscan.data.api.ApiClient apiClient =
                        org.example.semscan.data.api.ApiClient.getInstance(context);
                org.example.semscan.data.api.ApiService apiService = apiClient.getApiService();

                // Build device info
                String deviceInfo = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL +
                        " (Android " + android.os.Build.VERSION.RELEASE +
                        ", SDK " + android.os.Build.VERSION.SDK_INT + ")";

                org.example.semscan.data.api.ApiService.FcmTokenRequest request =
                        new org.example.semscan.data.api.ApiService.FcmTokenRequest(token, deviceInfo);

                retrofit2.Response<Void> response = apiService.registerFcmToken(username, request).execute();

                if (response.isSuccessful()) {
                    Logger.i(TAG, "FCM token registered with server");
                } else {
                    Logger.w(TAG, "Failed to register FCM token: " + response.code());
                }
            } catch (Exception e) {
                Logger.e(TAG, "Error sending FCM token to server", e);
            }
        }).start();
    }
}
