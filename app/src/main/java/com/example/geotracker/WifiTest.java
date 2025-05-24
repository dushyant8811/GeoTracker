package com.example.geotracker;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;
import android.content.pm.PackageManager;

import androidx.appcompat.app.AppCompatActivity;

public class WifiTest extends AppCompatActivity {

    private static final String TARGET_SSID = "Poco";
    private static final String TARGET_BSSID = "4a:fd:f9:2a:57:09"; // Replace with actual BSSID

//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_wifi);
//
//        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
//        }
//
////        TextView tvStatus = findViewById(R.id.tv_status);
////        Button btnVerify = findViewById(R.id.btn_verify);
//
////        btnVerify.setOnClickListener(v -> {
////            if (isConnectedToTargetWifi()) {
////                tvStatus.setText("Presence Confirmed: Connected to the target Wi-Fi.");
////                Toast.makeText(this, "You are connected to the required Wi-Fi!", Toast.LENGTH_SHORT).show();
////            } else {
////                tvStatus.setText("Verification Failed: Please connect to the target Wi-Fi.");
////                Toast.makeText(this, "Not connected to the required Wi-Fi.", Toast.LENGTH_SHORT).show();
////            }
////        });
////    }
//
//    private boolean isConnectedToTargetWifi() {
//        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
//        if (wifiManager != null) {
//            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
//            if (wifiInfo != null) {
//                String currentSsid = wifiInfo.getSSID();
//                String currentBssid = wifiInfo.getBSSID();
//
//                // Log for debugging
//                Log.d("WiFiInfo", "Connected SSID: " + currentSsid);
//                Log.d("WiFiInfo", "Connected BSSID: " + currentBssid);
//
//                // Compare SSID and BSSID
//                return TARGET_SSID.equals(currentSsid.replace("\"", "")) && TARGET_BSSID.equals(currentBssid);
//            } else {
//                Log.e("WiFiError", "Unable to retrieve Wi-Fi info.");
//                Toast.makeText(this, "Error: Could not retrieve Wi-Fi information.", Toast.LENGTH_SHORT).show();
//            }
//        } else {
//            Log.e("WiFiError", "Wi-Fi Manager is null.");
//            Toast.makeText(this, "Error: Wi-Fi is disabled or unavailable.", Toast.LENGTH_SHORT).show();
//        }
//        return false;
//    }
}

