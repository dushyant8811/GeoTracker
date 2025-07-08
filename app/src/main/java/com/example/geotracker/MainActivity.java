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
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

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

    // Location components
    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;

    private GeofencingClient geofencingClient;
    private PendingIntent geofencePendingIntent;

    private MapView mapView;
    private Marker userMarker;
    private Polygon geofenceCircle;

    private TextView statusTextView;
    private TextView checkInTimeTextView;
    private TextView checkOutTimeTextView;
    private TextView lastUpdateTextView;
    private BroadcastReceiver geofenceUpdateReceiver;
    private boolean isReceiverRegistered = false;

    private final Executor executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String role = prefs.getString("user_role", "");

        if ("hr".equals(role)) {
            startActivity(new Intent(this, HRDashboardActivity.class));
            finish();
            return;
        }

        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_main);

        statusTextView = findViewById(R.id.statusTextView);
        checkInTimeTextView = findViewById(R.id.checkInTimeTextView);
        checkOutTimeTextView = findViewById(R.id.checkOutTimeTextView);
        lastUpdateTextView = findViewById(R.id.lastUpdateTextView);

        Button btnLogout = findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> showLogoutConfirmationDialog());

        Button btnViewRecords = findViewById(R.id.btnViewRecords);
        btnViewRecords.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, AttendanceRecordsActivity.class));
        });

        ImageButton syncButton = findViewById(R.id.syncButton);
        syncButton.setOnClickListener(v -> {
            v.animate().rotationBy(360).setDuration(1000).start();
            OneTimeWorkRequest syncWorkRequest = new OneTimeWorkRequest.Builder(SyncWorker.class).build();
            WorkManager.getInstance(MainActivity.this).enqueue(syncWorkRequest);
            Toast.makeText(this, "Syncing data in background...", Toast.LENGTH_SHORT).show();
        });

        mapView = findViewById(R.id.mapView);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(18.0);
        mapView.getController().setCenter(new GeoPoint(GEOFENCE_LAT, GEOFENCE_LON));

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
        Log.d(TAG, "Logout initiated. Cleaning up session...");

        Intent stopServiceIntent = new Intent(this, LocationForegroundService.class);
        stopServiceIntent.setAction(LocationForegroundService.ACTION_STOP_TRACKING);
        startService(stopServiceIntent);
        Log.d(TAG, "Sent stop command to LocationForegroundService.");

        geofencingClient.removeGeofences(getGeofencePendingIntent())
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Geofences removed successfully."))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to remove geofences.", e));

        SharedPreferences userPrefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        userPrefs.edit().clear().apply();
        SharedPreferences geofencePrefs = getSharedPreferences("SavedGeofences", MODE_PRIVATE);
        geofencePrefs.edit().clear().apply();
        Log.d(TAG, "Cleared user and geofence preferences.");

        FirebaseAuth.getInstance().signOut();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void setupRefreshButton() {
        ImageButton refreshButton = findViewById(R.id.refreshButton);
        refreshButton.setOnClickListener(v -> {
            Animation rotateAnim = AnimationUtils.loadAnimation(this, R.anim.rotate_once);
            v.startAnimation(rotateAnim);
            rotateAnim.setAnimationListener(new Animation.AnimationListener() {
                @Override public void onAnimationStart(Animation animation) { v.setEnabled(false); }
                @Override public void onAnimationEnd(Animation animation) {
                    v.setEnabled(true);
                    updateUI();
                }
                @Override public void onAnimationRepeat(Animation animation) {}
            });
        });
    }

    private PendingIntent getGeofencePendingIntent() {
        if (geofencePendingIntent != null) {
            return geofencePendingIntent;
        }
        Intent intent = new Intent(this, GeofenceBroadcastReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        geofencePendingIntent = PendingIntent.getBroadcast(this, 0, intent, flags);
        return geofencePendingIntent;
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

        if (GeofenceHelper.isGeofenceAlreadyAdded(this)) {
            Log.d(TAG, "Geofence already exists, skipping add.");
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            geofencingClient.addGeofences(request, getGeofencePendingIntent())
                    .addOnSuccessListener(aVoid -> {
                        GeofenceHelper.saveGeofenceToPrefs(this, GEOFENCE_ID, (float) GEOFENCE_LAT, (float) GEOFENCE_LON, GEOFENCE_RADIUS);
                        Log.d(TAG, "Geofence added successfully.");
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
        statusTextView.setText("Status: Refreshing...");
        statusTextView.setTextColor(Color.GRAY);
        statusTextView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);

        String lastUpdateTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        lastUpdateTextView.setText(String.format("Last update: %s", lastUpdateTime));

        executor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(MainActivity.this);
            AttendanceDao dao = db.attendanceDao();
            AttendanceRecord activeRecord = dao.getActiveRecord();
            List<AttendanceRecord> completedRecords = dao.getCompletedRecords();
            AttendanceRecord lastRecord = completedRecords.isEmpty() ? null : completedRecords.get(0);

            runOnUiThread(() -> {
                try {
                    if (activeRecord != null) {
                        checkInTimeTextView.setText(String.format("Check-in: %s", activeRecord.checkInTime));
                        checkOutTimeTextView.setText("Check-out: -");
                        boolean isWifiValid = WifiValidator.isConnectedToOfficeWifi(MainActivity.this);
                        if (isWifiValid) {
                            statusTextView.setText("In office (Verified)");
                            statusTextView.setTextColor(Color.GREEN);
                            setStatusIcon(R.drawable.ic_verified);
                        } else {
                            statusTextView.setText("In office (Unverified)");
                            statusTextView.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.unverified_orange));
                            setStatusIcon(R.drawable.ic_warning);
                        }
                    } else if (lastRecord != null && lastRecord.checkOutTime != null) {
                        checkInTimeTextView.setText(String.format("Check-in: %s", lastRecord.checkInTime));
                        checkOutTimeTextView.setText(String.format("Check-out: %s", lastRecord.checkOutTime));
                        try {
                            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                            Date checkInDate = format.parse(lastRecord.checkInTime);
                            Date checkOutDate = format.parse(lastRecord.checkOutTime);
                            long duration = checkOutDate.getTime() - checkInDate.getTime();
                            String durationText = String.format(Locale.getDefault(), "%dh %02dm", TimeUnit.MILLISECONDS.toHours(duration), TimeUnit.MILLISECONDS.toMinutes(duration) % 60);
                            statusTextView.setText(String.format("Last session: %s", durationText));
                            statusTextView.setTextColor(Color.BLUE);
                            setStatusIcon(R.drawable.ic_history);
                        } catch (ParseException e) {
                            statusTextView.setText("Session recorded");
                            statusTextView.setTextColor(Color.BLUE);
                        }
                    } else {
                        checkInTimeTextView.setText("Check-in: N/A");
                        checkOutTimeTextView.setText("Check-out: N/A");
                        statusTextView.setText("Outside office area");
                        statusTextView.setTextColor(Color.RED);
                        setStatusIcon(R.drawable.ic_location_off);
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
        Drawable icon = ContextCompat.getDrawable(this, iconRes);
        statusTextView.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
    }

    private void createLocationRequest() {
        locationRequest = LocationRequest.create();
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(5000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private void createLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    updateUserLocation(location);
                }
            }
        };
    }

    private void updateUserLocation(Location location) {
        GeoPoint currentPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
        if (userMarker != null) {
            userMarker.setPosition(currentPoint);
            mapView.getController().animateTo(currentPoint);
            mapView.invalidate();
        }
    }

    private void checkPermissionsAndStart() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_FOREGROUND_LOCATION);
        } else {
            handlePostPermissionSetup();
        }
    }

    private void handlePostPermissionSetup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, REQUEST_BACKGROUND_LOCATION);
        } else {
            startAppFeatures();
            checkInitialState();
        }
    }

    private void startAppFeatures() {
        if (!GeofenceHelper.isGeofenceAlreadyAdded(this)) {
            addGeofence();
        } else {
            new GeofenceHelper(this).reRegisterGeofences();
        }
        initMapOverlay();
        startLocationUpdates();
        updateUI();
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
        }
    }

    private void stopLocationUpdates() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerGeofenceUpdateReceiver();
        startLocationUpdates();
        updateUI();
        OneTimeWorkRequest syncWorkRequest = new OneTimeWorkRequest.Builder(SyncWorker.class).build();
        WorkManager.getInstance(this).enqueue(syncWorkRequest);
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
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                handlePostPermissionSetup();
            } else {
                Toast.makeText(this, "Location permission is required.", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_BACKGROUND_LOCATION) {
            startAppFeatures();
            if (!(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(this, "Background location recommended for full functionality.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void checkInitialState() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    float[] results = new float[1];
                    Location.distanceBetween(location.getLatitude(), location.getLongitude(), GEOFENCE_LAT, GEOFENCE_LON, results);
                    if (results[0] <= GEOFENCE_RADIUS) {
                        executor.execute(() -> {
                            AttendanceRecord activeRecord = AppDatabase.getInstance(MainActivity.this).attendanceDao().getActiveRecord();
                            if (activeRecord == null) {
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