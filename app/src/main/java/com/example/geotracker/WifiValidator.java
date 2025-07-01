package com.example.geotracker;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;
import androidx.core.app.ActivityCompat;

public class WifiValidator {
    private static final String TAG = "WifiValidator";
    private static final String TARGET_SSID = "Poco";
    private static final String TARGET_BSSID = "96:2f:85:c6:4a:86";

    public static boolean isConnectedToOfficeWifi(Context context) {
        if (!hasWifiPermissions(context)) {
            Log.w(TAG, "Missing WiFi permissions");
            return false;
        }

        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) return false;

        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo == null) return false;

        String currentSSID = sanitizeSsid(wifiInfo.getSSID());
        String currentBSSID = wifiInfo.getBSSID();

        Log.d(TAG, "SSID: " + currentSSID + ", BSSID: " + currentBSSID);

        return TARGET_SSID.equals(currentSSID) &&
                TARGET_BSSID.equalsIgnoreCase(currentBSSID);
    }

    private static String sanitizeSsid(String ssid) {
        // Remove surrounding quotes if present
        return ssid.replaceAll("^\"|\"$", "");
    }

    private static boolean hasWifiPermissions(Context context) {
        return ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
}