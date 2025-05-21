package com.example.geotracker;

import android.content.Context;
import java.io.FileOutputStream;
import java.io.IOException;

public class LogFileHelper {

    private static final String LOG_FILE_NAME = "geofence_logs.txt";

    // Append log text with timestamp to internal storage file
    public static void appendLog(Context context, String text) {
        try (FileOutputStream fos = context.openFileOutput(LOG_FILE_NAME, Context.MODE_APPEND)) {
            String logEntry = System.currentTimeMillis() + ": " + text + "\n";
            fos.write(logEntry.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Optional method to clear logs (delete the log file)
    public static void clearLog(Context context) {
        context.deleteFile(LOG_FILE_NAME);
    }
}
