package com.example.geotracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class GeofenceBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "GeofenceReceiver";
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private final Executor executor = Executors.newSingleThreadExecutor();
    private static final String OFFICE_NAME = "Headquarters"; // Default office name

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Geofence event received");

        if (intent == null) {
            Log.e(TAG, "Received null intent");
            return;
        }

        // Handle manual check-in first
        if ("MANUAL_CHECK_IN".equals(intent.getAction())) {
            if (!WifiValidator.isConnectedToOfficeWifi(context)) {
                NotificationHelper.sendNotification(context,
                        "Verification Failed",
                        "Connect to office WiFi to check-in");
                return;
            }
            handleManualCheckIn(context);
            return;
        }

        GeofencingEvent event = GeofencingEvent.fromIntent(intent);
        if (event == null) {
            Log.e(TAG, "Null GeofencingEvent");
            return;
        }

        if (event.hasError()) {
            Log.e(TAG, "Geofencing error: " + event.getErrorCode());
            return;
        }

        int transition = event.getGeofenceTransition();
        String time = sdf.format(new Date());

        switch (transition) {
            case Geofence.GEOFENCE_TRANSITION_ENTER:
                // First verify Wi-Fi before any check-in processing
                if (!WifiValidator.isConnectedToOfficeWifi(context)) {
                    NotificationHelper.sendNotification(context,
                            "Verification Failed",
                            "Connect to office Wi-Fi to complete check-in");
                    return; // Exit early if Wi-Fi validation fails
                }

                handleEnterTransition(context, time);
                break;

            case Geofence.GEOFENCE_TRANSITION_EXIT:
                // Check Wi-Fi status on exit (for suspicious activity detection)
                boolean stillOnOfficeWifi = WifiValidator.isConnectedToOfficeWifi(context);
                handleExitTransition(context, time);

                if (stillOnOfficeWifi) {
                    NotificationHelper.sendNotification(context,
                            "Attention Needed",
                            "You left the geofence but are still on office Wi-Fi");
                }
                break;

            default:
                NotificationHelper.sendNotification(context,
                        "Geofence Alert",
                        "Unknown geofence transition detected at " + time);
        }

        sendUpdateUIBroadcast(context);
    }

    private void sendUpdateUIBroadcast(Context context) {
        Intent updateIntent = new Intent("com.example.geotracker.UPDATE_UI");
        updateIntent.setPackage(context.getPackageName()); // Add this for Android 8+ compatibility
        context.sendBroadcast(updateIntent);
    }

    private void handleManualCheckIn(Context context) {
        executor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(context);
            AttendanceDao dao = db.attendanceDao();

            // Check if there's an active session
            AttendanceRecord activeRecord = dao.getActiveRecord();
            if (activeRecord == null) {
                // Create new record
                String time = sdf.format(new Date());
                AttendanceRecord record = new AttendanceRecord(OFFICE_NAME, time);
                dao.insert(record);

                // Start foreground service
                Intent serviceIntent = new Intent(context, LocationForegroundService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }

                NotificationHelper.sendNotification(context,
                        "Manual Check-In",
                        "You were already inside the geofence at " + time);
            }
        });
    }

    private void handleEnterTransition(Context context, String time) {
        executor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(context);
            AttendanceDao dao = db.attendanceDao();

            // Check if there's an active session
            AttendanceRecord activeRecord = dao.getActiveRecord();
            if (activeRecord == null) {
                // Create new record
                AttendanceRecord record = new AttendanceRecord(OFFICE_NAME, time);
                dao.insert(record);

                // Start foreground service
                Intent startServiceIntent = new Intent(context, LocationForegroundService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(startServiceIntent);
                } else {
                    context.startService(startServiceIntent);
                }

                NotificationHelper.sendNotification(context,
                        "Geofence Entered",
                        "You entered the geofence at " + time);
            }
        });
    }

    private void handleExitTransition(Context context, String time) {
        executor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(context);
            AttendanceDao dao = db.attendanceDao();

            // Get active session and close it
            AttendanceRecord activeRecord = dao.getActiveRecord();
            if (activeRecord != null) {
                activeRecord.checkOutTime = time;
                dao.update(activeRecord);

                // Stop foreground service
                Intent stopServiceIntent = new Intent(context, LocationForegroundService.class);
                context.stopService(stopServiceIntent);

                NotificationHelper.sendNotification(context,
                        "Geofence Exited",
                        "You exited the geofence at " + time);
            }
        });
    }
}