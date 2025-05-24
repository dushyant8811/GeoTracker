package com.example.geotracker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class NotificationHelper {
    public static final String CHANNEL_ID = "geofence_channel";
    public static final String CHANNEL_ID_HIGH = "geofence_high_priority";
    private static final int NOTIFICATION_ID = 1001;
    private static boolean channelsCreated = false;

    public static void createNotificationChannels(Context context) {
        if (channelsCreated || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        // High priority channel for geofence transitions
        NotificationChannel highChannel = new NotificationChannel(
                CHANNEL_ID_HIGH,
                "Geofence Alerts",
                NotificationManager.IMPORTANCE_HIGH
        );
        highChannel.setDescription("Important geofence entry/exit notifications");
        highChannel.enableLights(true);
        highChannel.setLightColor(Color.RED);
        highChannel.enableVibration(true);
        highChannel.setVibrationPattern(new long[]{0, 500, 200, 500});

        // Default channel for service notifications
        NotificationChannel defaultChannel = new NotificationChannel(
                CHANNEL_ID,
                "GeoTracker Service",
                NotificationManager.IMPORTANCE_LOW
        );
        defaultChannel.setDescription("Background service notifications");
        defaultChannel.setShowBadge(false);

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(highChannel);
            manager.createNotificationChannel(defaultChannel);
            channelsCreated = true;
        }
    }

    public static void sendNotification(Context context, String title, String message) {
        createNotificationChannels(context);

        // Create intent to open app when notification is tapped
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_HIGH)
                .setSmallIcon(R.drawable.ic_launcher_foreground) // Use your custom icon
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher))
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message));

        // Add action button to open app
        builder.addAction(R.drawable.ic_launcher_background, "Open App", pendingIntent);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        try {
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        } catch (SecurityException e) {
            Log.e("NotificationHelper", "Failed to show notification", e);
        }
    }

    public static Notification getForegroundNotification(Context context) {
        createNotificationChannels(context);

        // Get current check-in time for notification
        SharedPreferences prefs = context.getSharedPreferences("GeofencePrefs", Context.MODE_PRIVATE);
        String checkInTime = prefs.getString("checkInTime", "Active session");

        // Create intent to open app when notification is tapped
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("GeoTracker Active")
                .setContentText("Tracking since: " + checkInTime)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher))
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
}