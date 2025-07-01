package com.example.geotracker;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
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
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";
    private static final double GEOFENCE_LAT = 28.720126;
    private static final double GEOFENCE_LON = 77.0822006;
    private static final float GEOFENCE_RADIUS = 150;
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private static final int LOCATION_TIMEOUT_MS = 10000; // 10 seconds timeout
    private static final String OFFICE_NAME = "Headquarters"; // Default office name
    private final Executor executor = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Device rebooted. Initializing geofence recovery...");

            // 1. Always re-register geofences first
            new GeofenceHelper(context).reRegisterGeofences();

            // 2. Check session state before reboot using Room DB
            executor.execute(() -> {
                AppDatabase db = AppDatabase.getInstance(context);
                AttendanceDao dao = db.attendanceDao();

                // Check if there was an active session (check-in without check-out)
                AttendanceRecord activeRecord = dao.getActiveRecord();
                boolean hasActiveSession = (activeRecord != null);

                // Also check if there are no sessions at all (unknown state)
                boolean noSessions = (dao.getAllRecords().size() == 0);

                if (hasActiveSession || noSessions) {
                    Log.d(TAG, "Checking location state after reboot...");
                    NotificationHelper.sendNotification(context, "GeoTracker", "Recovering your session after reboot");

                    // 4. Get current location with timeout fallback
                    FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(context);

                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {

                        // Try to get last location quickly first
                        client.getLastLocation().addOnSuccessListener(location -> {
                            if (location != null) {
                                handleLocationResult(context, location, activeRecord);
                            } else {
                                // If no last location, request fresh one with timeout
                                requestFreshLocationWithTimeout(context, client, activeRecord);
                            }
                        });
                    } else {
                        Log.w(TAG, "Location permission not granted after reboot");
                    }
                }
            });
        }
    }

    private void handleLocationResult(Context context, Location location, AttendanceRecord activeRecord) {
        float[] results = new float[1];
        Location.distanceBetween(
                location.getLatitude(), location.getLongitude(),
                GEOFENCE_LAT, GEOFENCE_LON, results
        );

        String currentTime = sdf.format(new Date());
        AppDatabase db = AppDatabase.getInstance(context);
        AttendanceDao dao = db.attendanceDao();

        if (results[0] <= GEOFENCE_RADIUS) {
            // Inside geofence
            executor.execute(() -> {
                if (activeRecord == null) {
                    // No active session - create new check-in
                    AttendanceRecord newRecord = new AttendanceRecord(OFFICE_NAME, currentTime);
                    newRecord.userId = FirebaseAuth.getInstance().getCurrentUser().getUid(); // Add user ID
                    dao.insert(newRecord);
                    Log.d(TAG, "New check-in recorded after reboot");

                }

                // Start foreground service in all cases
                Intent serviceIntent = new Intent(context, LocationForegroundService.class);
                serviceIntent.setAction(LocationForegroundService.ACTION_START_TRACKING);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }

                String message = activeRecord == null ?
                        "Session started after reboot at " + currentTime :
                        "Session resumed after reboot at " + currentTime;
                NotificationHelper.sendNotification(context, "GeoTracker", message);
            });
        } else {
            // Outside geofence
            executor.execute(() -> {
                if (activeRecord != null) {
                    // Was checked in but now outside - record check-out
                    activeRecord.checkOutTime = currentTime;
                    dao.update(activeRecord);
                    Log.d(TAG, "Auto check-out recorded after reboot");

                    new FirestoreSyncHelper().syncRecords(context);

                    // Stop foreground service
                    Intent stopServiceIntent = new Intent(context, LocationForegroundService.class);
                    context.stopService(stopServiceIntent);

                    NotificationHelper.sendNotification(context, "GeoTracker",
                            "Auto checked-out after reboot at " + currentTime);
                }
            });
        }

        context.sendBroadcast(new Intent("com.example.geotracker.UPDATE_UI"));
    }

    private void requestFreshLocationWithTimeout(Context context, FusedLocationProviderClient client, AttendanceRecord activeRecord) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Location permission lost since initial check in onReceive()");
            handleLocationPermissionLoss(context, activeRecord);
            return;
        }

        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setNumUpdates(1)
                .setExpirationDuration(LOCATION_TIMEOUT_MS);

        try {
            client.requestLocationUpdates(locationRequest, new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    if (locationResult != null && locationResult.getLastLocation() != null) {
                        handleLocationResult(context, locationResult.getLastLocation(), activeRecord);
                    } else {
                        Log.w(TAG, "Location request timed out or failed");
                        handleLocationPermissionLoss(context, activeRecord);
                    }
                }
            }, Looper.getMainLooper());
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException when requesting location updates", e);
            handleLocationPermissionLoss(context, activeRecord);
        }
    }

    private void handleLocationPermissionLoss(Context context, AttendanceRecord activeRecord) {
        Log.w(TAG, "Handling location permission loss");
        String currentTime = sdf.format(new Date());

        if (activeRecord != null) {
            executor.execute(() -> {
                AppDatabase db = AppDatabase.getInstance(context);
                AttendanceDao dao = db.attendanceDao();

                activeRecord.checkOutTime = currentTime;
                activeRecord.completed = true;
                dao.update(activeRecord);

                // Stop foreground service
                Intent stopServiceIntent = new Intent(context, LocationForegroundService.class);
                context.stopService(stopServiceIntent);

                NotificationHelper.sendNotification(context, "GeoTracker",
                        "Location permission revoked - session ended at " + currentTime);
            });
        }
    }
}