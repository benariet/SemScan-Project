package org.example.semscan.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import org.example.semscan.R;
import org.example.semscan.ui.auth.LoginActivity;

/**
 * Helper class for managing notification channels and displaying notifications.
 */
public class NotificationHelper {

    // Notification channel IDs
    public static final String CHANNEL_APPROVALS = "semscan_approvals";
    public static final String CHANNEL_PROMOTIONS = "semscan_promotions";
    public static final String CHANNEL_REMINDERS = "semscan_reminders";

    // Notification IDs
    private static final int NOTIFICATION_ID_APPROVAL = 1001;
    private static final int NOTIFICATION_ID_PROMOTION = 1002;
    private static final int NOTIFICATION_ID_REMINDER = 1003;

    private NotificationHelper() {
        // Private constructor to prevent instantiation
    }

    /**
     * Creates all notification channels. Should be called in Application.onCreate().
     */
    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager =
                    context.getSystemService(NotificationManager.class);

            // Sound for notifications
            Uri defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();

            // Vibration pattern: wait 0ms, vibrate 250ms, wait 100ms, vibrate 250ms
            long[] vibrationPattern = {0, 250, 100, 250};

            // Registration Updates channel (high importance)
            NotificationChannel approvalsChannel = new NotificationChannel(
                    CHANNEL_APPROVALS,
                    "Registration Updates",
                    NotificationManager.IMPORTANCE_HIGH);
            approvalsChannel.setDescription("Notifications about registration approvals and declines");
            approvalsChannel.enableVibration(true);
            approvalsChannel.setVibrationPattern(vibrationPattern);
            approvalsChannel.enableLights(true);
            approvalsChannel.setLightColor(Color.GREEN);
            approvalsChannel.setSound(defaultSound, audioAttributes);
            approvalsChannel.setShowBadge(true);
            approvalsChannel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            notificationManager.createNotificationChannel(approvalsChannel);

            // Waiting List channel (high importance)
            NotificationChannel promotionsChannel = new NotificationChannel(
                    CHANNEL_PROMOTIONS,
                    "Waiting List Updates",
                    NotificationManager.IMPORTANCE_HIGH);
            promotionsChannel.setDescription("Notifications when you're promoted from a waiting list");
            promotionsChannel.enableVibration(true);
            promotionsChannel.setVibrationPattern(vibrationPattern);
            promotionsChannel.enableLights(true);
            promotionsChannel.setLightColor(Color.BLUE);
            promotionsChannel.setSound(defaultSound, audioAttributes);
            promotionsChannel.setShowBadge(true);
            promotionsChannel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            notificationManager.createNotificationChannel(promotionsChannel);

            // Reminders channel (high importance - reminders are important too)
            NotificationChannel remindersChannel = new NotificationChannel(
                    CHANNEL_REMINDERS,
                    "Presentation Reminders",
                    NotificationManager.IMPORTANCE_HIGH);
            remindersChannel.setDescription("Reminders for upcoming presentations");
            remindersChannel.enableVibration(true);
            remindersChannel.setVibrationPattern(vibrationPattern);
            remindersChannel.enableLights(true);
            remindersChannel.setLightColor(Color.YELLOW);
            remindersChannel.setSound(defaultSound, audioAttributes);
            remindersChannel.setShowBadge(true);
            notificationManager.createNotificationChannel(remindersChannel);

            Logger.d("NotificationHelper", "Notification channels created");
        }
    }

    /**
     * Shows an approval notification (approved or declined).
     */
    public static void showApprovalNotification(Context context, String title, String body,
                                                  long slotId, boolean approved) {
        showNotification(context, CHANNEL_APPROVALS, NOTIFICATION_ID_APPROVAL, title, body, slotId);
    }

    /**
     * Shows a waiting list promotion notification.
     */
    public static void showPromotionNotification(Context context, String title, String body, long slotId) {
        showNotification(context, CHANNEL_PROMOTIONS, NOTIFICATION_ID_PROMOTION, title, body, slotId);
    }

    /**
     * Shows a presentation reminder notification.
     */
    public static void showReminderNotification(Context context, String title, String body, long slotId) {
        showNotification(context, CHANNEL_REMINDERS, NOTIFICATION_ID_REMINDER, title, body, slotId);
    }

    /**
     * Generic notification display method.
     */
    private static void showNotification(Context context, String channelId, int notificationId,
                                          String title, String body, long slotId) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Create intent to open the app
        Intent intent = new Intent(context, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (slotId > 0) {
            intent.putExtra("slotId", slotId);
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Determine color based on channel
        int color;
        if (CHANNEL_APPROVALS.equals(channelId)) {
            color = Color.parseColor("#4CAF50"); // Green for approvals
        } else if (CHANNEL_PROMOTIONS.equals(channelId)) {
            color = Color.parseColor("#2196F3"); // Blue for promotions
        } else {
            color = Color.parseColor("#FF9800"); // Orange for reminders
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setColor(color)
                .setColorized(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        notificationManager.notify(notificationId, builder.build());
        Logger.d("NotificationHelper", "Notification shown: " + title);
    }
}
