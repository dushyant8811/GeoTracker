package com.example.geotracker;
import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private static final String GEOFENCE_ID = "MY_GEOFENCE";
    private static final double GEOFENCE_LAT = 28.7162322;
    private static final double GEOFENCE_LON = 77.1191449;
    private static final float GEOFENCE_RADIUS = 150; // meters

    private static final int REQUEST_FOREGROUND_LOCATION = 100;
    private static final int REQUEST_BACKGROUND_LOCATION = 101;

    private static final String CHANNEL_ID = "geofence_channel";
    private static final int NOTIFICATION_ID = 1;

    private GeofencingClient geofencingClient;
    private PendingIntent geofencePendingIntent;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    private MapView mapView;
    private Marker userMarker;
    private Polygon geofenceCircle;

    private Boolean wasInsideGeofence = null;  // Track previous geofence state

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate called");
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_main);

        createNotificationChannel();

        mapView = findViewById(R.id.mapView);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        IMapController mapController = mapView.getController();
        mapController.setZoom(18.0);
        mapController.setCenter(new GeoPoint(GEOFENCE_LAT, GEOFENCE_LON));

        geofencingClient = LocationServices.getGeofencingClient(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        checkPermissionsAndStart();
    }

    private void checkPermissionsAndStart() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Requesting foreground location permission");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_FOREGROUND_LOCATION);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Requesting background location permission");
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                            REQUEST_BACKGROUND_LOCATION);
                } else {
                    Log.d(TAG, "All location permissions granted");
                    startAppFeatures();
                }
            } else {
                // Background location permission not needed below Android 10
                startAppFeatures();
            }
        }
    }

    private void startAppFeatures() {
        addGeofence();
        initMapOverlay();
        startLocationUpdates();

        Intent serviceIntent = new Intent(this, LocationForegroundService.class);
        startForegroundService(serviceIntent);
    }

    private void createNotificationChannel() {
        Log.d(TAG, "Creating notification channel if needed");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Geofence Notifications";
            String description = "Notifications for Geofence state changes";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
            Log.d(TAG, "Notification channel created");
        }
    }

    private void showNotification(String title, String message) {
        Log.d(TAG, "Showing notification: " + message);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private void addGeofence() {
        Log.d(TAG, "Adding geofence");
        Geofence geofence = new Geofence.Builder()
                .setRequestId(GEOFENCE_ID)
                .setCircularRegion(GEOFENCE_LAT, GEOFENCE_LON, GEOFENCE_RADIUS)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                .build();

        GeofencingRequest geofencingRequest = new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build();

        geofencePendingIntent = PendingIntent.getBroadcast(
                this, 0,
                new Intent(this, GeofenceBroadcastReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
        );

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Geofence added successfully");
                        Toast.makeText(this, "Geofence added", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to add geofence", e);
                        Toast.makeText(this, "Failed to add geofence", Toast.LENGTH_SHORT).show();
                    });
        } else {
            Log.w(TAG, "Location permission not granted, cannot add geofence");
        }
    }

    private void initMapOverlay() {
        Log.d(TAG, "Initializing map overlays");
        geofenceCircle = new Polygon();
        geofenceCircle.setPoints(Polygon.pointsAsCircle(new GeoPoint(GEOFENCE_LAT, GEOFENCE_LON), GEOFENCE_RADIUS));
        geofenceCircle.setFillColor(0x44FF0000); // Red transparent
        geofenceCircle.setStrokeColor(0xFFFF0000);
        geofenceCircle.setStrokeWidth(2f);
        mapView.getOverlays().add(geofenceCircle);

        Marker centerMarker = new Marker(mapView);
        centerMarker.setPosition(new GeoPoint(GEOFENCE_LAT, GEOFENCE_LON));
        centerMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        centerMarker.setTitle("Geofence Center");
        mapView.getOverlays().add(centerMarker);

        userMarker = new Marker(mapView);
        userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        userMarker.setTitle("You are here");
        mapView.getOverlays().add(userMarker);

        mapView.invalidate();
        Log.d(TAG, "Map overlays initialized");
    }

    private void startLocationUpdates() {
        Log.d(TAG, "Starting location updates");
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(5000); // 5 seconds
        locationRequest.setFastestInterval(2000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                if (result == null || result.getLastLocation() == null) {
                    Log.d(TAG, "Location result or last location is null");
                    return;
                }

                Location location = result.getLastLocation();
                Log.d(TAG, "Location update received: " + location.getLatitude() + ", " + location.getLongitude());

                GeoPoint userLocation = new GeoPoint(location.getLatitude(), location.getLongitude());

                userMarker.setPosition(userLocation);

                float[] distance = new float[1];
                Location.distanceBetween(
                        location.getLatitude(), location.getLongitude(),
                        GEOFENCE_LAT, GEOFENCE_LON,
                        distance
                );

                boolean isInside = distance[0] <= GEOFENCE_RADIUS;
                Log.d(TAG, "Distance from geofence center: " + distance[0] + " meters. Inside geofence? " + isInside);

                if (wasInsideGeofence == null || wasInsideGeofence != isInside) {
                    wasInsideGeofence = isInside;

                    if (isInside) {
                        Log.d(TAG, "User entered geofence");
                        geofenceCircle.setFillColor(0x4400FF00); // Transparent green
                        geofenceCircle.setStrokeColor(0xFF00FF00);
                        showNotification("Geofence Status", "You are INSIDE the geofence");
                    } else {
                        Log.d(TAG, "User exited geofence");
                        geofenceCircle.setFillColor(0x44FF0000); // Transparent red
                        geofenceCircle.setStrokeColor(0xFFFF0000);
                        showNotification("Geofence Status", "You are OUTSIDE the geofence");
                    }
                    mapView.invalidate();
                }
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
            Log.d(TAG, "Location updates requested");
        } else {
            Log.w(TAG, "Location permission not granted, cannot request updates");
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy called, but not stopping services");
        super.onDestroy();

        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult called");
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_FOREGROUND_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Foreground location permission granted");

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Log.d(TAG, "Requesting background location permission");
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                            REQUEST_BACKGROUND_LOCATION);
                } else {
                    startAppFeatures();
                }
            } else {
                Log.w(TAG, "Foreground location permission denied");
                Toast.makeText(this, "Foreground location permission is required", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_BACKGROUND_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Background location permission granted");
                startAppFeatures();
            } else {
                Log.w(TAG, "Background location permission denied");
                Toast.makeText(this, "Background location permission is recommended for full functionality", Toast.LENGTH_LONG).show();
                // Optionally guide user to settings to enable background location
                startAppFeatures(); // You can still start with limited functionality
            }
        }
    }
}