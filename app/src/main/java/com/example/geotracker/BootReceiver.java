package com.example.geotracker;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";
    private static final double GEOFENCE_LAT = 28.720126;
    private static final double GEOFENCE_LON = 77.0822006;
    private static final float GEOFENCE_RADIUS = 150;
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private static final int LOCATION_TIMEOUT_MS = 10000; // 10 seconds timeout

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Device rebooted. Initializing geofence recovery...");
            LogFileHelper.appendLog(context, "Device reboot detected");

            // 1. Always re-register geofences first
            new GeofenceHelper(context).reRegisterGeofences();

            // 2. Check session state before reboot
            SharedPreferences prefs = context.getSharedPreferences("GeofencePrefs", Context.MODE_PRIVATE);
            String checkInTime = prefs.getString("checkInTime", "N/A");
            String checkOutTime = prefs.getString("checkOutTime", "N/A");

            // 3. If there was an active session (check-in without check-out) or no session at all
            if ((!checkInTime.equals("N/A") && checkOutTime.equals("N/A")) ||
                    (checkInTime.equals("N/A") && checkOutTime.equals("N/A"))) {

                Log.d(TAG, "Checking location state after reboot...");
                NotificationHelper.sendNotification(context, "GeoTracker", "Recovering your session after reboot");

                // 4. Get current location with timeout fallback
                FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(context);

                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {

                    // Try to get last location quickly first
                    client.getLastLocation().addOnSuccessListener(location -> {
                        if (location != null) {
                            handleLocationResult(context, location, prefs);
                        } else {
                            // If no last location, request fresh one with timeout
                            requestFreshLocationWithTimeout(context, client, prefs);
                        }
                    });
                } else {
                    Log.w(TAG, "Location permission not granted after reboot");
                    LogFileHelper.appendLog(context, "Cannot check location after reboot - permission missing");
                }
            }
        }
    }

    private void handleLocationResult(Context context, Location location, SharedPreferences prefs) {
        float[] results = new float[1];
        Location.distanceBetween(
                location.getLatitude(), location.getLongitude(),
                GEOFENCE_LAT, GEOFENCE_LON, results
        );

        SharedPreferences.Editor editor = prefs.edit();
        String currentTime = sdf.format(new Date());

        if (results[0] <= GEOFENCE_RADIUS) {
            // Inside geofence - handle accordingly
            String checkInTime = prefs.getString("checkInTime", "N/A");
            String checkOutTime = prefs.getString("checkOutTime", "N/A");

            if (checkInTime.equals("N/A") || !checkOutTime.equals("N/A")) {
                // No active session - create new check-in
                editor.putString("checkInTime", currentTime);
                editor.remove("checkOutTime");
                Log.d(TAG, "New check-in recorded after reboot");
                LogFileHelper.appendLog(context, "New check-in at " + currentTime + " after reboot");
            }

            // Start foreground service in all cases
            Intent serviceIntent = new Intent(context, LocationForegroundService.class);
            serviceIntent.setAction(LocationForegroundService.ACTION_START_TRACKING);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }

            NotificationHelper.sendNotification(context, "GeoTracker",
                    "Session " + (checkInTime.equals("N/A") ? "started" : "resumed") +
                            " after reboot at " + currentTime);

        } else {
            // Outside geofence - handle accordingly
            String checkInTime = prefs.getString("checkInTime", "N/A");
            String checkOutTime = prefs.getString("checkOutTime", "N/A");

            if (!checkInTime.equals("N/A") && checkOutTime.equals("N/A")) {
                // Was checked in but now outside - record check-out
                editor.putString("checkOutTime", currentTime);
                Log.d(TAG, "Auto check-out recorded after reboot");
                LogFileHelper.appendLog(context, "Auto check-out at " + currentTime + " after reboot");

                // Stop foreground service
                Intent stopServiceIntent = new Intent(context, LocationForegroundService.class);
                context.stopService(stopServiceIntent);

                NotificationHelper.sendNotification(context, "GeoTracker",
                        "Auto checked-out after reboot at " + currentTime);
            }
        }

        editor.apply();
        context.sendBroadcast(new Intent("com.example.geotracker.UPDATE_UI"));
    }

    private void requestFreshLocationWithTimeout(Context context, FusedLocationProviderClient client, SharedPreferences prefs) {
        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setNumUpdates(1)
                .setExpirationDuration(LOCATION_TIMEOUT_MS);

        client.requestLocationUpdates(locationRequest, new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult != null && !locationResult.getLocations().isEmpty()) {
                    handleLocationResult(context, locationResult.getLastLocation(), prefs);
                } else {
                    Log.w(TAG, "Location request timed out");
                    LogFileHelper.appendLog(context, "Location check after reboot failed - assuming outside");

                    // Default to check-out if we can't determine location
                    SharedPreferences.Editor editor = prefs.edit();
                    String currentTime = sdf.format(new Date());

                    // Only record check-out if there was a check-in
                    String checkInTime = prefs.getString("checkInTime", "N/A");
                    if (!checkInTime.equals("N/A")) {
                        editor.putString("checkOutTime", currentTime);
                        editor.apply();

                        // Stop foreground service
                        Intent stopServiceIntent = new Intent(context, LocationForegroundService.class);
                        context.stopService(stopServiceIntent);

                        NotificationHelper.sendNotification(context, "GeoTracker",
                                "Couldn't verify location - session ended at " + currentTime);
                    }
                }
                client.removeLocationUpdates(this);
            }
        }, Looper.getMainLooper());
    }
}