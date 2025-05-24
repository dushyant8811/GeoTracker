package com.example.geotracker;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;

import java.util.ArrayList;
import java.util.List;

public class GeofenceHelper {

    private static final String TAG = "GeofenceHelper";
    private static final String PREFS_NAME = "SavedGeofences";

    private final Context context;
    private final GeofencingClient geofencingClient;

    public GeofenceHelper(Context context) {
        this.context = context;
        this.geofencingClient = LocationServices.getGeofencingClient(context);
    }

    public void reRegisterGeofences() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        String id = prefs.getString("geofenceId", null);
        float lat = prefs.getFloat("lat", 0);
        float lng = prefs.getFloat("lng", 0);
        float radius = prefs.getFloat("radius", 0);

        if (id == null || radius == 0) {
            Log.w(TAG, "No saved geofence to re-register.");
            return;
        }

        Geofence geofence = new Geofence.Builder()
                .setRequestId(id)
                .setCircularRegion(lat, lng, radius)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .build();

        GeofencingRequest request = new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build();

        Intent intent = new Intent(context, GeofenceBroadcastReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, flags);

        geofencingClient.addGeofences(request, pendingIntent)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Geofence re-registered"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to re-register geofence: " + e.getMessage()));
    }

    public static void saveGeofenceToPrefs(Context context, String id, float lat, float lng, float radius) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putString("geofenceId", id);
        editor.putFloat("lat", lat);
        editor.putFloat("lng", lng);
        editor.putFloat("radius", radius);
        editor.apply();
    }

    public static boolean isGeofenceAlreadyAdded(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.contains("geofenceId"); // Fixed to check correct prefs
    }

}
