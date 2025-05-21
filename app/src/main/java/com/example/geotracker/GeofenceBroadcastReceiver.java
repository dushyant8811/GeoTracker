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

    @Override
    public void onReceive(Context context, Intent intent) {
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
                break;
            case Geofence.GEOFENCE_TRANSITION_EXIT:
                transitionMessage = "Checked Out ❌";
                break;
            case Geofence.GEOFENCE_TRANSITION_DWELL:
                transitionMessage = "Dwelling inside geofence";
                break;
            default:
                transitionMessage = "Unknown transition";
        }

        for (Geofence geofence : triggeringGeofences) {
            String id = geofence.getRequestId();
            Log.d("GEOFENCE", "ID: " + id + ", Event: " + transitionMessage);
            Toast.makeText(context, transitionMessage, Toast.LENGTH_LONG).show();
        }
    }
}