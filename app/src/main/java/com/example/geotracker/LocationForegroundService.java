package com.example.geotracker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.os.Handler;  // Add this import statement

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.*;

public class LocationForegroundService extends Service {

    private static final String TAG = "LocationForegroundSvc";
    private static final String CHANNEL_ID = "location_channel";
    private static final int NOTIFICATION_ID = 1;

    private Handler wifiCheckHandler = new Handler();
    private static final long WIFI_CHECK_INTERVAL = 300000;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    // Add these at the top of the class (change from private to public)
    public static final String ACTION_START_TRACKING = "com.example.geotracker.START_TRACKING";
    public static final String ACTION_STOP_TRACKING = "com.example.geotracker.STOP_TRACKING";


    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate called");

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, NotificationHelper.getForegroundNotification(this));

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);


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

        // Check permissions before requesting updates
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission not granted");
            stopSelf(); // Stop the service if permission is missing
            return;
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        Log.d(TAG, "Location updates requested");

        startPeriodicWifiChecks();
    }

    private void startPeriodicWifiChecks() {
        wifiCheckHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!WifiValidator.isConnectedToOfficeWifi(LocationForegroundService.this)) {
                    LogFileHelper.appendLog(LocationForegroundService.this,
                            "WiFi validation failed during session");
                    NotificationHelper.sendNotification(LocationForegroundService.this,
                            "Session Paused",
                            "Reconnect to office WiFi to continue tracking");
                }
                wifiCheckHandler.postDelayed(this, WIFI_CHECK_INTERVAL);
            }
        }, WIFI_CHECK_INTERVAL);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service onStartCommand called");
        return START_STICKY; // Ensures service is restarted if killed
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service onDestroy called");
        super.onDestroy();

        wifiCheckHandler.removeCallbacksAndMessages(null);

        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            Log.d(TAG, "Location updates removed");
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification getNotification() {
        SharedPreferences prefs = getSharedPreferences("GeofencePrefs", MODE_PRIVATE);
        String checkInTime = prefs.getString("checkInTime", "Not checked in");

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("GeoTracker Active")
                .setContentText("Tracking since: " + checkInTime)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("GeoTracker Update")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setAutoCancel(true)
                .build();

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID + 1, notification);
        }
    }


    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Location Tracking",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}