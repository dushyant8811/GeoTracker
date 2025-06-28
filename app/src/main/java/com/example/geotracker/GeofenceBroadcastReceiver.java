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

public class GeofenceBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "GeofenceReceiver";
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

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
        SharedPreferences prefs = context.getSharedPreferences("GeofencePrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        switch (transition) {
            case Geofence.GEOFENCE_TRANSITION_ENTER:
                String existingCheckIn = prefs.getString("checkInTime", "N/A");
                String existingCheckOut = prefs.getString("checkOutTime", "N/A");

                // First verify Wi-Fi before any check-in processing
                if (!WifiValidator.isConnectedToOfficeWifi(context)) {

                    NotificationHelper.sendNotification(context,
                            "Verification Failed",
                            "Connect to office Wi-Fi to complete check-in");
                    return; // Exit early if Wi-Fi validation fails
                }

                if (existingCheckIn.equals("N/A") || !existingCheckOut.equals("N/A")) {
                    editor.putString("checkInTime", time);
                    editor.remove("checkOutTime");

                    Intent startServiceIntent = new Intent(context, LocationForegroundService.class);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(startServiceIntent);
                    } else {
                        context.startService(startServiceIntent);
                    }

                    NotificationHelper.sendNotification(context,
                            "Geofence Entered",
                            "You entered the geofence at " + time + "\nWi-Fi verified");
                }
                handleEnterTransition(context, editor, time);
                break;

            case Geofence.GEOFENCE_TRANSITION_EXIT:
                // Check Wi-Fi status on exit (for suspicious activity detection)
                boolean stillOnOfficeWifi = WifiValidator.isConnectedToOfficeWifi(context);

                handleExitTransition(context, editor, time);

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

        editor.apply();
        context.sendBroadcast(new Intent("com.example.geotracker.UPDATE_UI"));

        sendUpdateUIBroadcast(context);
    }

    private void sendUpdateUIBroadcast(Context context) {
        Intent updateIntent = new Intent("com.example.geotracker.UPDATE_UI");
        updateIntent.setPackage(context.getPackageName()); // Add this for Android 8+ compatibility
        context.sendBroadcast(updateIntent);
    }

    private void handleManualCheckIn(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("GeofencePrefs", Context.MODE_PRIVATE);
        String checkInTime = prefs.getString("checkInTime", "N/A");
        String checkOutTime = prefs.getString("checkOutTime", "N/A");

        if (checkInTime.equals("N/A") || !checkOutTime.equals("N/A")) {
            String time = sdf.format(new Date());
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("checkInTime", time);
            editor.remove("checkOutTime");
            editor.apply();

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
    }

    private void handleEnterTransition(Context context, SharedPreferences.Editor editor, String time) {
        String existingCheckOut = context.getSharedPreferences("GeofencePrefs", Context.MODE_PRIVATE)
                .getString("checkOutTime", "N/A");

        if (existingCheckOut.equals("N/A")) {
            // Already inside, no need to update
            return;
        }

        editor.putString("checkInTime", time);
        editor.remove("checkOutTime");

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

    private void handleExitTransition(Context context, SharedPreferences.Editor editor, String time) {
        String existingCheckIn = context.getSharedPreferences("GeofencePrefs", Context.MODE_PRIVATE)
                .getString("checkInTime", "N/A");

        if (existingCheckIn.equals("N/A")) {
            // Wasn't checked in, ignore exit
            return;
        }

        editor.putString("checkOutTime", time);
        
        // Stop foreground service
        Intent stopServiceIntent = new Intent(context, LocationForegroundService.class);
        context.stopService(stopServiceIntent);

        NotificationHelper.sendNotification(context,
                "Geofence Exited",
                "You exited the geofence at " + time);
    }
}