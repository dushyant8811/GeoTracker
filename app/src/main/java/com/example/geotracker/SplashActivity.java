package com.example.geotracker;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash); // Use the layout we just created

        // Check authentication
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            // User not logged in, go to LoginActivity
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // Get stored role
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String role = prefs.getString("user_role", "");

        // Redirect based on role
        Intent intent;
        if ("hr".equals(role)) {
            intent = new Intent(this, HRDashboardActivity.class);
        } else {
            intent = new Intent(this, MainActivity.class);
        }

        startActivity(intent);
        finish();
    }
}