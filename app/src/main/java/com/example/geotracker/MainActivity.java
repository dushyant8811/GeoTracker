package com.example.geotracker;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String GEOFENCE_ID = "MY_GEOFENCE";
    private static final double GEOFENCE_LAT = 28.720126;
    private static final double GEOFENCE_LON = 77.0822006;
    private static final float GEOFENCE_RADIUS = 150;
    private static final int REQUEST_FOREGROUND_LOCATION = 100;
    private static final int REQUEST_BACKGROUND_LOCATION = 101;

    // Location components
    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;

    private GeofencingClient geofencingClient;
    private MapView mapView;
    private Marker userMarker;
    private Polygon geofenceCircle;

    private TextView statusTextView;
    private TextView checkInTimeTextView;
    private TextView checkOutTimeTextView;
    private BroadcastReceiver geofenceUpdateReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_main);

        // Initialize UI components
        statusTextView = findViewById(R.id.statusTextView);
        checkInTimeTextView = findViewById(R.id.checkInTimeTextView);
        checkOutTimeTextView = findViewById(R.id.checkOutTimeTextView);

        Button openLogsButton = findViewById(R.id.openLogsButton);
        openLogsButton.setOnClickListener(v -> startActivity(new Intent(this, LogViewerActivity.class)));

        // Map setup
        mapView = findViewById(R.id.mapView);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(18.0);
        mapView.getController().setCenter(new GeoPoint(GEOFENCE_LAT, GEOFENCE_LON));

        // Location services setup
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        geofencingClient = LocationServices.getGeofencingClient(this);

        createLocationRequest();
        createLocationCallback();
        checkPermissionsAndStart();
    }

    private void addGeofence() {
        Geofence geofence = new Geofence.Builder()
                .setRequestId(GEOFENCE_ID)
                .setCircularRegion(GEOFENCE_LAT, GEOFENCE_LON, GEOFENCE_RADIUS)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                .build();

        GeofencingRequest request = new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build();

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, 0,
                new Intent(this, GeofenceBroadcastReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
        );

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            geofencingClient.addGeofences(request, pendingIntent)
                    .addOnSuccessListener(aVoid -> Toast.makeText(this, "Geofence added", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Geofence addition failed", e);
                        Toast.makeText(this, "Geofence setup failed", Toast.LENGTH_SHORT).show();
                    });
        }
    }

    // Rest of the methods (initMapOverlay, updateUI, etc.)
    private void initMapOverlay() {
        geofenceCircle = new Polygon();
        geofenceCircle.setPoints(Polygon.pointsAsCircle(new GeoPoint(GEOFENCE_LAT, GEOFENCE_LON), GEOFENCE_RADIUS));
        geofenceCircle.setFillColor(0x44FF0000);
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
        userMarker.setTitle("Your Location");
        mapView.getOverlays().add(userMarker);

        mapView.invalidate();
    }

    private void registerGeofenceUpdateReceiver() {
        geofenceUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateUI();
            }
        };

        // Register the receiver with the correct intent filter
        IntentFilter filter = new IntentFilter("com.example.geotracker.UPDATE_UI");
        registerReceiver(geofenceUpdateReceiver, filter);
    }
    private void updateUI() {
        SharedPreferences prefs = getSharedPreferences("GeofencePrefs", MODE_PRIVATE);
        String checkInTime = prefs.getString("checkInTime", "N/A");
        String checkOutTime = prefs.getString("checkOutTime", "N/A");

        checkInTimeTextView.setText("Check-in Time: " + checkInTime);
        checkOutTimeTextView.setText("Check-out Time: " + checkOutTime);

        if (checkInTime.equals("N/A") && checkOutTime.equals("N/A")) {
            statusTextView.setText("Status: Outside geofence");
        } else if (!checkInTime.equals("N/A") && checkOutTime.equals("N/A")) {
            statusTextView.setText("Status: Inside geofence");
        } else {
            try {
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                Date checkInDate = format.parse(checkInTime);
                Date checkOutDate = format.parse(checkOutTime);
                long diff = checkOutDate.getTime() - checkInDate.getTime();
                long hours = diff / (60 * 60 * 1000);
                long minutes = (diff % (60 * 60 * 1000)) / (60 * 1000);

                String total = String.format(Locale.getDefault(), "%d hrs %d mins", hours, minutes);
                statusTextView.setText(String.format("Last check-out: %s (Total: %s)", checkOutTime, total));
            } catch (ParseException e) {
                statusTextView.setText("Status: Time calculation error");
                Log.e(TAG, "Date parsing error", e);
            }
        }
    }

    private void createLocationRequest() {
        locationRequest = LocationRequest.create();
        locationRequest.setInterval(10000); // Update interval 10 seconds
        locationRequest.setFastestInterval(5000); // Fastest update interval 5 seconds
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private void createLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                for (Location location : locationResult.getLocations()) {
                    updateUserLocation(location);
                }
            }
        };
    }

    private void updateUserLocation(Location location) {
        GeoPoint currentPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
        userMarker.setPosition(currentPoint);
        mapView.getController().animateTo(currentPoint);
        mapView.invalidate();
    }

    private void checkPermissionsAndStart() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_FOREGROUND_LOCATION);
        } else {
            handlePostPermissionSetup();
        }
    }

    private void handlePostPermissionSetup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                    REQUEST_BACKGROUND_LOCATION);
        } else {
            startAppFeatures();
        }
    }

    private void startAppFeatures() {
        addGeofence();
        initMapOverlay();
        startLocationUpdates();
        updateUI();
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
        }
    }

    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    // Rest of the existing methods (addGeofence, initMapOverlay, updateUI, etc.)
    // ... [Keep all the existing methods from your previous code] ...

    @Override
    protected void onResume() {
        super.onResume();
        registerGeofenceUpdateReceiver();
        if (locationCallback != null) {
            startLocationUpdates();
        }
        updateUI();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(geofenceUpdateReceiver);
        stopLocationUpdates();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_FOREGROUND_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                handlePostPermissionSetup();
            } else {
                Toast.makeText(this, "Location permission required", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_BACKGROUND_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startAppFeatures();
            } else {
                Toast.makeText(this, "Background location recommended for full functionality", Toast.LENGTH_LONG).show();
                startAppFeatures();
            }
        }
    }
}