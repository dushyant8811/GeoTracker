package com.example.geotracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class GeofenceBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "GeofenceReceiver";
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Geofence event received");
        LogFileHelper.appendLog(context, "Geofence event received");

        GeofencingEvent event = GeofencingEvent.fromIntent(intent);
        if (event == null) {
            Log.e(TAG, "Null GeofencingEvent");
            return;
        }

        if (event.hasError()) {
            Log.e(TAG, "Geofencing error: " + event.getErrorCode());
            return;
        }

        int transition = event.getGeofenceTransition();
        String transitionType = getTransitionString(transition);
        String time = sdf.format(new Date());

        SharedPreferences prefs = context.getSharedPreferences("GeofencePrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        switch (transition) {
            case Geofence.GEOFENCE_TRANSITION_ENTER:
                editor.putString("checkInTime", time);
                editor.remove("checkOutTime");
                LogFileHelper.appendLog(context, "Check-in at " + time);

                // Start the foreground service
                Intent startServiceIntent = new Intent(context, LocationForegroundService.class);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(startServiceIntent);
                } else {
                    context.startService(startServiceIntent);
                }

                break;
            case Geofence.GEOFENCE_TRANSITION_EXIT:
                editor.putString("checkOutTime", time);
                LogFileHelper.appendLog(context, "Check-out at " + time);

                // Stop the foreground service
                Intent stopServiceIntent = new Intent(context, LocationForegroundService.class);
                context.stopService(stopServiceIntent);
                break;
            default:
                LogFileHelper.appendLog(context, "Unknown transition: " + transition);
        }
        editor.apply();

        // Update UI
        context.sendBroadcast(new Intent("com.example.geotracker.UPDATE_UI"));

        // Show notification
        String message = transition == Geofence.GEOFENCE_TRANSITION_ENTER ?
                "Checked In ✅" : "Checked Out ❌";
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }

    private String getTransitionString(int transitionType) {
        switch (transitionType) {
            case Geofence.GEOFENCE_TRANSITION_ENTER:
                return "ENTER";
            case Geofence.GEOFENCE_TRANSITION_EXIT:
                return "EXIT";
            default:
                return "UNKNOWN";
        }
    }
}