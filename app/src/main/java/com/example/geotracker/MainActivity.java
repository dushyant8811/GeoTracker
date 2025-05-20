package com.example.geotracker;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
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

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;

import java.util.Collections;

public class MainActivity extends AppCompatActivity {

    private static final String GEOFENCE_ID = "MY_GEOFENCE";
    private static final double GEOFENCE_LAT = 28.720126;
    private static final double GEOFENCE_LON = 77.0822006;
    private static final float GEOFENCE_RADIUS = 100; // meters
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;

    private GeofencingClient geofencingClient;
    private PendingIntent geofencePendingIntent;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    private MapView mapView;
    private Marker userMarker;
    private Polygon geofenceCircle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_main);

        mapView = findViewById(R.id.mapView);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        IMapController mapController = mapView.getController();
        mapController.setZoom(18.0);
        mapController.setCenter(new GeoPoint(GEOFENCE_LAT, GEOFENCE_LON));

        geofencingClient = LocationServices.getGeofencingClient(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            addGeofence();
            initMapOverlay();
            startLocationUpdates();
        }
    }

    private void addGeofence() {
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
                        Toast.makeText(this, "Geofence added", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to add geofence", Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    });
        }
    }

    private void initMapOverlay() {
        // Draw geofence circle
        geofenceCircle = new Polygon();
        geofenceCircle.setPoints(Polygon.pointsAsCircle(new GeoPoint(GEOFENCE_LAT, GEOFENCE_LON), GEOFENCE_RADIUS));
        geofenceCircle.setFillColor(0x44FF0000); // Red by default
        geofenceCircle.setStrokeColor(0xFFFF0000);
        geofenceCircle.setStrokeWidth(2f);
        mapView.getOverlays().add(geofenceCircle);

        // Add geofence center marker
        Marker centerMarker = new Marker(mapView);
        centerMarker.setPosition(new GeoPoint(GEOFENCE_LAT, GEOFENCE_LON));
        centerMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        centerMarker.setTitle("Geofence Center");
        mapView.getOverlays().add(centerMarker);

        // User marker
        userMarker = new Marker(mapView);
        userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        userMarker.setTitle("You are here");
        mapView.getOverlays().add(userMarker);

        mapView.invalidate();
    }

    private void startLocationUpdates() {
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(5000); // 5 seconds
        locationRequest.setFastestInterval(2000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                if (result == null || result.getLastLocation() == null) return;

                Location location = result.getLastLocation();
                GeoPoint userLocation = new GeoPoint(location.getLatitude(), location.getLongitude());

                // Update user marker
                userMarker.setPosition(userLocation);

                // Calculate distance to geofence
                float[] distance = new float[1];
                Location.distanceBetween(
                        location.getLatitude(), location.getLongitude(),
                        GEOFENCE_LAT, GEOFENCE_LON,
                        distance
                );

                // Change circle color based on geofence
                if (distance[0] <= GEOFENCE_RADIUS) {
                    geofenceCircle.setFillColor(0x4400FF00); // Transparent Green
                    geofenceCircle.setStrokeColor(0xFF00FF00);
                    Toast.makeText(MainActivity.this, "You are INSIDE the geofence", Toast.LENGTH_SHORT).show();
                } else {
                    geofenceCircle.setFillColor(0x44FF0000); // Transparent Red
                    geofenceCircle.setStrokeColor(0xFFFF0000);
                    Toast.makeText(MainActivity.this, "You are OUTSIDE the geofence", Toast.LENGTH_SHORT).show();
                }

                mapView.invalidate();
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            addGeofence();
            initMapOverlay();
            startLocationUpdates();
        } else {
            Toast.makeText(this, "Location permissions are required", Toast.LENGTH_LONG).show();
        }
    }
}
