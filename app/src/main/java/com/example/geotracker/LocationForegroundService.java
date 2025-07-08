package com.example.geotracker;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.os.Handler;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.gms.location.*;

public class LocationForegroundService extends Service {

    private static final String TAG = "LocationForegroundSvc";
    private static final String CHANNEL_ID = "location_channel";
    private static final int NOTIFICATION_ID = 1001;

    private final Handler wifiCheckHandler = new Handler(Looper.getMainLooper());
    private static final long WIFI_CHECK_INTERVAL = 300000; // 5 minutes

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private boolean isTracking = false; // State flag to prevent multiple starts/stops

    // Actions
    public static final String ACTION_START_TRACKING = "com.example.geotracker.START_TRACKING";
    public static final String ACTION_STOP_TRACKING = "com.example.geotracker.STOP_TRACKING";


    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate called");
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
    }

    // This method is now the command router for the service
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            Log.d(TAG, "onStartCommand received action: " + action);

            switch (action) {
                case ACTION_START_TRACKING:
                    startTracking();
                    break;
                case ACTION_STOP_TRACKING:
                    stopTracking();
                    break;
            }
        }
        // Use START_STICKY to ensure the service is restarted if killed by the system
        // while it was supposed to be running.
        return START_STICKY;
    }

    // New method to encapsulate starting the service logic
    private void startTracking() {
        if (isTracking) {
            Log.d(TAG, "Service is already tracking. Ignoring start command.");
            return;
        }
        isTracking = true;
        Log.d(TAG, "Starting location tracking...");

        // Start the service in the foreground
        startForeground(NOTIFICATION_ID, NotificationHelper.getForegroundNotification(this));

        LocationRequest locationRequest = LocationRequest.create()
                .setInterval(5000)
                .setFastestInterval(2000)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                if (result != null && result.getLastLocation() != null) {
                    Location location = result.getLastLocation();
                    Log.d(TAG, "Location update: Lat=" + location.getLatitude() + ", Lon=" + location.getLongitude());
                    // TODO: Handle location update (send to server, save, etc.)
                } else {
                    Log.w(TAG, "Location result is null");
                }
            }
        };

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission not granted. Stopping service.");
            stopTracking();
            return;
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        Log.d(TAG, "Location updates requested");
        startPeriodicWifiChecks();
    }

    // New method to encapsulate stopping the service logic
    private void stopTracking() {
        if (!isTracking) {
            Log.d(TAG, "Service is not tracking. Ignoring stop command.");
            return;
        }
        isTracking = false;
        Log.d(TAG, "Stopping location tracking...");

        // Stop location updates
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            Log.d(TAG, "Location updates removed");
        }

        wifiCheckHandler.removeCallbacksAndMessages(null);

        stopForeground(true);

        stopSelf();
    }

    private void startPeriodicWifiChecks() {
        wifiCheckHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!WifiValidator.isConnectedToOfficeWifi(LocationForegroundService.this)) {
                    NotificationHelper.sendNotification(LocationForegroundService.this,
                            "Session Paused",
                            "Reconnect to office WiFi to continue tracking");
                }
                // Continue checking only if still tracking
                if (isTracking) {
                    wifiCheckHandler.postDelayed(this, WIFI_CHECK_INTERVAL);
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service onDestroy called");
        // Ensure cleanup is done if the service is destroyed by the system
        if (isTracking) {
            stopTracking();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}