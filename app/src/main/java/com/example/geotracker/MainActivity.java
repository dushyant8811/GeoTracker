package com.example.geotracker;

import android.Manifest;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.view.animation.AnimationUtils;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String GEOFENCE_ID = "MY_GEOFENCE";
    private static final double GEOFENCE_LAT = 28.720126;
    private static final double GEOFENCE_LON = 77.0822006;
    private static final float GEOFENCE_RADIUS = 150;
    private static final int REQUEST_FOREGROUND_LOCATION = 100;
    private static final int REQUEST_BACKGROUND_LOCATION = 101;
    private static final int REQUEST_WIFI_PERMISSION = 102;

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
    private TextView lastUpdateTextView;
    private BroadcastReceiver geofenceUpdateReceiver;
    private FirebaseFirestore db;
    private boolean isReceiverRegistered = false;

    private final Executor executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check authentication - no FirebaseApp.initialize needed
        FirebaseApp.initializeApp(this);

        // Improved authentication check
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        // Check stored role
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String role = prefs.getString("user_role", "");

        if ("hr".equals(role)) {
            startActivity(new Intent(this, HRDashboardActivity.class));
            finish();
            return;
        }

        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_main);

        // Initialize UI components
        statusTextView = findViewById(R.id.statusTextView);
        checkInTimeTextView = findViewById(R.id.checkInTimeTextView);
        checkOutTimeTextView = findViewById(R.id.checkOutTimeTextView);
        lastUpdateTextView = findViewById(R.id.lastUpdateTextView);

        // Initialize and set up logout button
        Button btnLogout = findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> showLogoutConfirmationDialog());

        // for roomdb testing
        Button btnViewRecords = findViewById(R.id.btnViewRecords);
        btnViewRecords.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, AttendanceRecordsActivity.class));
        });

        ImageButton syncButton = findViewById(R.id.syncButton);
        syncButton.setOnClickListener(v -> {
            v.animate().rotationBy(360).setDuration(1000).start();
            new FirestoreSyncHelper().syncRecords(MainActivity.this);
            Toast.makeText(this, "Syncing data...", Toast.LENGTH_SHORT).show();
        });

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
        setupRefreshButton();
    }

    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes", (dialog, which) -> performLogout())
                .setNegativeButton("No", null)
                .show();
    }

    private void performLogout() {
        FirebaseAuth.getInstance().signOut();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void setupRefreshButton() {
        ImageButton refreshButton = findViewById(R.id.refreshButton);
        refreshButton.setOnClickListener(v -> {
            // Start rotation animation
            Animation rotateAnim = AnimationUtils.loadAnimation(this, R.anim.rotate_once);
            v.startAnimation(rotateAnim);

            // Update UI after animation completes
            rotateAnim.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    v.setEnabled(false);
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    v.setEnabled(true);
                    updateUI();
                }

                @Override
                public void onAnimationRepeat(Animation animation) {}
            });
        });
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

        if (GeofenceHelper.isGeofenceAlreadyAdded(this)) {
            Log.d(TAG, "Geofence already exists, skipping add.");
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            geofencingClient.addGeofences(request, pendingIntent)
                    .addOnSuccessListener(aVoid -> {
                        GeofenceHelper.saveGeofenceToPrefs(this, GEOFENCE_ID,
                                (float) GEOFENCE_LAT, (float) GEOFENCE_LON, GEOFENCE_RADIUS);
                        Log.d(TAG, "Geofence added silently");
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Geofence addition failed", e);
                        Toast.makeText(this, "Geofence setup failed", Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void initMapOverlay() {
        mapView.getOverlays().clear();

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
        if (!isReceiverRegistered) {
            geofenceUpdateReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    updateUI();
                }
            };
            IntentFilter filter = new IntentFilter("com.example.geotracker.UPDATE_UI");
            registerReceiver(geofenceUpdateReceiver, filter);
            isReceiverRegistered = true;
        }
    }

    private void updateUI() {
        // Show refreshing state immediately
        statusTextView.setText("Status: Refreshing...");
        statusTextView.setTextColor(Color.GRAY);
        statusTextView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);

        String lastUpdateTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        lastUpdateTextView.setText(String.format("Last update: %s", lastUpdateTime));

        // Fetch data from Room database
        executor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(MainActivity.this);
            AttendanceDao dao = db.attendanceDao();

            // Get active record and last session
            AttendanceRecord activeRecord = dao.getActiveRecord();
            List<AttendanceRecord> completedRecords = dao.getCompletedRecords();
            AttendanceRecord lastRecord = completedRecords.isEmpty() ? null : completedRecords.get(0);

            runOnUiThread(() -> {
                try {
                    if (activeRecord != null) {
                        // Currently checked in
                        checkInTimeTextView.setText(String.format("Check-in: %s", activeRecord.checkInTime));
                        checkOutTimeTextView.setText("Check-out: -");

                        boolean isWifiValid = WifiValidator.isConnectedToOfficeWifi(MainActivity.this);
                        if (isWifiValid) {
                            statusTextView.setText("In office (Verified)");
                            statusTextView.setTextColor(Color.GREEN);
                            statusTextView.setCompoundDrawablesWithIntrinsicBounds(
                                    R.drawable.ic_verified, 0, 0, 0);
                        } else {
                            statusTextView.setText("In office (Unverified)");
                            statusTextView.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.unverified_orange));
                            statusTextView.setCompoundDrawablesWithIntrinsicBounds(
                                    R.drawable.ic_warning, 0, 0, 0);
                        }
                    } else if (lastRecord != null && lastRecord.checkOutTime != null) {
                        // Previous session
                        checkInTimeTextView.setText(String.format("Check-in: %s", lastRecord.checkInTime));
                        checkOutTimeTextView.setText(String.format("Check-out: %s", lastRecord.checkOutTime));

                        try {
                            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                            Date checkInDate = format.parse(lastRecord.checkInTime);
                            Date checkOutDate = format.parse(lastRecord.checkOutTime);

                            long duration = checkOutDate.getTime() - checkInDate.getTime();
                            String durationText = String.format(Locale.getDefault(),
                                    "%dh %02dm",
                                    TimeUnit.MILLISECONDS.toHours(duration),
                                    TimeUnit.MILLISECONDS.toMinutes(duration) % 60);

                            statusTextView.setText(String.format("Last session: %s", durationText));
                            statusTextView.setTextColor(Color.BLUE);
                            statusTextView.setCompoundDrawablesWithIntrinsicBounds(
                                    R.drawable.ic_history, 0, 0, 0);
                        } catch (ParseException e) {
                            statusTextView.setText("Session recorded");
                            statusTextView.setTextColor(Color.BLUE);
                        }
                    } else {
                        // No session data
                        checkInTimeTextView.setText("Check-in: N/A");
                        checkOutTimeTextView.setText("Check-out: N/A");

                        statusTextView.setText("Outside office area");
                        statusTextView.setTextColor(Color.RED);
                        statusTextView.setCompoundDrawablesWithIntrinsicBounds(
                                R.drawable.ic_location_off, 0, 0, 0);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "UI update failed", e);
                    statusTextView.setText("Status update failed");
                    statusTextView.setTextColor(Color.RED);
                }
            });
        });
    }

    private void setStatusIcon(@DrawableRes int iconRes) {
        Drawable icon = null;
        if (iconRes != 0) {
            icon = ContextCompat.getDrawable(this, iconRes);
            icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
        }
        statusTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_WIFI_STATE
                    },
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
            checkInitialState();
        }
    }

    private void startAppFeatures() {
        if (!GeofenceHelper.isGeofenceAlreadyAdded(this)) {
            addGeofence();
        } else {
            Log.d(TAG, "Geofence already exists, skipping add.");
        }

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

    @Override
    protected void onResume() {
        super.onResume();
        registerGeofenceUpdateReceiver();
        if (locationCallback != null) {
            startLocationUpdates();
        }
        updateUI();

        new FirestoreSyncHelper().syncRecords(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(geofenceUpdateReceiver);
                isReceiverRegistered = false;
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Receiver not registered");
            }
        }
        stopLocationUpdates();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_FOREGROUND_LOCATION) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                handlePostPermissionSetup();
            } else {
                Toast.makeText(this, "Location and WiFi permissions required", Toast.LENGTH_LONG).show();
                // Optionally re-request permissions or show explanation
            }
        } else if (requestCode == REQUEST_BACKGROUND_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startAppFeatures();
            } else {
                Toast.makeText(this, "Background location recommended for full functionality", Toast.LENGTH_LONG).show();
                startAppFeatures(); // Still proceed without background location
            }
        }
    }

    private void checkInitialState() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    float[] results = new float[1];
                    Location.distanceBetween(
                            location.getLatitude(), location.getLongitude(),
                            GEOFENCE_LAT, GEOFENCE_LON, results
                    );

                    if (results[0] <= GEOFENCE_RADIUS) {
                        executor.execute(() -> {
                            AppDatabase db = AppDatabase.getInstance(MainActivity.this);
                            AttendanceDao dao = db.attendanceDao();
                            AttendanceRecord activeRecord = dao.getActiveRecord();

                            // Only trigger manual check-in if no active session
                            if (activeRecord == null) {
                                // Trigger manual check-in
                                Intent intent = new Intent(MainActivity.this, GeofenceBroadcastReceiver.class);
                                intent.setAction("MANUAL_CHECK_IN");
                                sendBroadcast(intent);
                            }
                        });
                    }
                }
            });
        }
    }
}