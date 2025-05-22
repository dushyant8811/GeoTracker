package com.example.geotracker;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LogFileHelper {
    private static final String TAG = "LogFileHelper";

    public static void appendLog(Context context, String text) {
        try {
            File logFile = new File(context.getExternalFilesDir(null), "geofence_logs.txt");
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            String logEntry = timestamp + " - " + text + "\n";

            FileOutputStream fos = new FileOutputStream(logFile, true);
            fos.write(logEntry.getBytes());
            fos.close();
        } catch (IOException e) {
            Log.e(TAG, "Error writing to log file", e);
        }
    }
}