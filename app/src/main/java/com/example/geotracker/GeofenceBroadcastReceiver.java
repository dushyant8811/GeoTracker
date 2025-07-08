package com.example.geotracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class GeofenceBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "GeofenceReceiver";
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private final Executor executor = Executors.newSingleThreadExecutor();
    private static final String OFFICE_NAME = "Headquarters";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Geofence event received");

        if (intent == null) {
            Log.e(TAG, "Received null intent");
            return;
        }

        if ("MANUAL_CHECK_IN".equals(intent.getAction())) {
            if (!WifiValidator.isConnectedToOfficeWifi(context)) {
                NotificationHelper.sendNotification(context, "Verification Failed", "Connect to office WiFi to check-in");
                return;
            }
            handleManualCheckIn(context);
            return;
        }

        GeofencingEvent event = GeofencingEvent.fromIntent(intent);
        if (event == null || event.hasError()) {
            Log.e(TAG, "Geofencing error or null event: " + (event != null ? event.getErrorCode() : "null event"));
            return;
        }

        int transition = event.getGeofenceTransition();
        String time = sdf.format(new Date());

        if (transition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            if (!WifiValidator.isConnectedToOfficeWifi(context)) {
                NotificationHelper.sendNotification(context, "Verification Failed", "Connect to office Wi-Fi to complete check-in");
                return;
            }
            handleEnterTransition(context, time);
        } else if (transition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            handleExitTransition(context, time);
            if (WifiValidator.isConnectedToOfficeWifi(context)) {
                NotificationHelper.sendNotification(context, "Attention Needed", "You left the geofence but are still on office Wi-Fi");
            }
        } else {
            NotificationHelper.sendNotification(context, "Geofence Alert", "Unknown geofence transition detected at " + time);
        }

        sendUpdateUIBroadcast(context);
    }

    private void sendUpdateUIBroadcast(Context context) {
        Intent updateIntent = new Intent("com.example.geotracker.UPDATE_UI");
        updateIntent.setPackage(context.getPackageName());
        context.sendBroadcast(updateIntent);
    }

    private void handleManualCheckIn(Context context) {
        executor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(context);
            AttendanceDao dao = db.attendanceDao();

            if (dao.getActiveRecord() == null) {
                String time = sdf.format(new Date());
                AttendanceRecord record = new AttendanceRecord(OFFICE_NAME, time);

                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (user != null) {
                    record.userId = user.getUid();
                }

                dao.insert(record);

                Intent serviceIntent = new Intent(context, LocationForegroundService.class);
                serviceIntent.setAction(LocationForegroundService.ACTION_START_TRACKING);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }

                NotificationHelper.sendNotification(context, "Manual Check-In", "You were checked in at " + time);
            }
        });
    }

    private void handleEnterTransition(Context context, String time) {
        executor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(context);
            AttendanceDao dao = db.attendanceDao();

            if (dao.getActiveRecord() == null) {
                AttendanceRecord record = new AttendanceRecord(OFFICE_NAME, time);
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (user != null) {
                    record.userId = user.getUid();
                }
                dao.insert(record);

                Intent startServiceIntent = new Intent(context, LocationForegroundService.class);
                startServiceIntent.setAction(LocationForegroundService.ACTION_START_TRACKING);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(startServiceIntent);
                } else {
                    context.startService(startServiceIntent);
                }
                NotificationHelper.sendNotification(context, "Geofence Entered", "You entered the geofence at " + time);
            }
        });
    }

    private void handleExitTransition(Context context, String time) {
        executor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(context);
            AttendanceDao dao = db.attendanceDao();

            AttendanceRecord activeRecord = dao.getActiveRecord();
            if (activeRecord != null) {
                activeRecord.checkOutTime = time;
                activeRecord.completed = true;
                dao.update(activeRecord);

                Intent stopServiceIntent = new Intent(context, LocationForegroundService.class);
                stopServiceIntent.setAction(LocationForegroundService.ACTION_STOP_TRACKING);
                context.startService(stopServiceIntent);

                NotificationHelper.sendNotification(context, "Geofence Exited", "You exited the geofence at " + time);

                // Trigger sync using WorkManager for reliability
                Log.d(TAG, "Enqueuing SyncWorker to sync completed session.");
                OneTimeWorkRequest syncWorkRequest = new OneTimeWorkRequest.Builder(SyncWorker.class).build();
                WorkManager.getInstance(context).enqueue(syncWorkRequest);
            }
        });
    }
}