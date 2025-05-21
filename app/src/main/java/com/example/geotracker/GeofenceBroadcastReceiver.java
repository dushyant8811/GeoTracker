package com.example.geotracker;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import java.util.List;

public class GeofenceBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "GeofenceReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {

        Log.d(TAG, "Geofence event received");
        LogFileHelper.appendLog(context, "Geofence event received");



        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);

        if (geofencingEvent == null) {
            Log.e("GEOFENCE", "GeofencingEvent is null");
            return;
        }

        if (geofencingEvent.hasError()) {
            Log.e("GEOFENCE", "Geofencing error: " + geofencingEvent.getErrorCode());
            return;
        }

        List<Geofence> triggeringGeofences = geofencingEvent.getTriggeringGeofences();
        if (triggeringGeofences == null || triggeringGeofences.isEmpty()) {
            Log.w("GEOFENCE", "No triggering geofences");
            return;
        }

        int transitionType = geofencingEvent.getGeofenceTransition();

        String transitionMessage;

        switch (transitionType) {
            case Geofence.GEOFENCE_TRANSITION_ENTER:
                transitionMessage = "Checked In ✅";
                Log.d(TAG, "GEOFENCE_TRANSITION_ENTER detected");
                LogFileHelper.appendLog(context, "GEOFENCE_TRANSITION_ENTER detected");

                break;
            case Geofence.GEOFENCE_TRANSITION_EXIT:
                transitionMessage = "Checked Out ❌";
                Log.d(TAG, "GEOFENCE_TRANSITION_EXIT detected");
                LogFileHelper.appendLog(context, "GEOFENCE_TRANSITION_EXIT detected");
                break;
            default:
                transitionMessage = "Unknown transition";
                Log.w(TAG, "Unknown geofence transition: " + transitionType);
        }

        for (Geofence geofence : triggeringGeofences) {
            String id = geofence.getRequestId();
            Log.d("GEOFENCE", "ID: " + id + ", Event: " + transitionMessage);
            Toast.makeText(context, transitionMessage, Toast.LENGTH_LONG).show();
        }
    }
}