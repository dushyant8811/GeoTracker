package com.example.geotracker;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class LogViewerActivity extends AppCompatActivity {

    private TextView logTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_viewer);

        logTextView = findViewById(R.id.logTextView);
        displayLogs();
    }

    private void displayLogs() {
        File logFile = new File(getFilesDir(), "geofence_logs.txt");  // Correct filename here

        if (!logFile.exists()) {
            logTextView.setText("No logs found.");
            return;
        }

        try (FileInputStream fis = new FileInputStream(logFile)) {
            byte[] buffer = new byte[(int) logFile.length()];
            fis.read(buffer);
            String logs = new String(buffer);
            logTextView.setText(logs);
        } catch (IOException e) {
            logTextView.setText("Failed to read log file.");
            e.printStackTrace();
        }
    }
}
