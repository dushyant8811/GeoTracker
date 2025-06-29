package com.example.geotracker;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.content.SharedPreferences;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etEmail, etPassword;
    private FirebaseAuth mAuth;
    private TextView toggleEmployee, toggleHR;
    private View slider;
    private String selectedRole = "employee";
    private TextInputLayout emailLayout, passwordLayout;
    private float cornerRadius;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if user is already logged in
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser != null) {
            fetchUserRole(currentUser.getUid());
            return;
        }

        setContentView(R.layout.activity_login);

        // Convert 24dp to pixels for corner radius
        Resources r = getResources();
        cornerRadius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                24,
                r.getDisplayMetrics()
        );

        mAuth = auth;
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        toggleEmployee = findViewById(R.id.toggleEmployee);
        toggleHR = findViewById(R.id.toggleHR);
        slider = findViewById(R.id.slider);
        emailLayout = findViewById(R.id.emailLayout);
        passwordLayout = findViewById(R.id.passwordLayout);

        // Initialize slider width after layout is drawn
        slider.post(() -> {
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) slider.getLayoutParams();

            params.width = toggleEmployee.getWidth();
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            params.addRule(RelativeLayout.ALIGN_PARENT_START);
            slider.setLayoutParams(params);

            // Set initial corners (left rounded)
            setSliderCorners(true, true);
        });

        findViewById(R.id.btnLogin).setOnClickListener(v -> loginUser());

        findViewById(R.id.tvForgotPassword).setOnClickListener(v -> {
            Toast.makeText(LoginActivity.this, "Reset password feature coming soon", Toast.LENGTH_SHORT).show();
        });
    }

    public void onToggleClick(View view) {
        boolean wasEmployeeSelected = selectedRole.equals("employee");
        selectedRole = (view.getId() == R.id.toggleEmployee) ? "employee" : "hr";

        if (wasEmployeeSelected != selectedRole.equals("employee")) {
            animateToggle();
        }
    }

    private void animateToggle() {
        int startPosition = slider.getLeft();
        int endPosition = selectedRole.equals("employee") ? 0 : toggleEmployee.getWidth();

        ValueAnimator animator = ValueAnimator.ofInt(startPosition, endPosition);
        animator.addUpdateListener(animation -> {
            int value = (Integer) animation.getAnimatedValue();
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) slider.getLayoutParams();
            params.leftMargin = value;
            slider.setLayoutParams(params);

            // Maintain rounded corners during animation
            setSliderCorners(true, true);
        });

        // Update text colors
        toggleEmployee.setTextColor(ContextCompat.getColor(this,
                selectedRole.equals("employee") ? R.color.toggle_selected_text : R.color.toggle_unselected_text));
        toggleHR.setTextColor(ContextCompat.getColor(this,
                selectedRole.equals("employee") ? R.color.toggle_unselected_text : R.color.toggle_selected_text));

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                // Start with all corners rounded
                setSliderCorners(true, true);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                // Set final corners after animation
                if (selectedRole.equals("employee")) {
                    setSliderCorners(true, true); // Left rounded when on employee
                } else {
                    setSliderCorners(true, true); // Right rounded when on HR
                }
            }
        });

        animator.setDuration(300);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.start();
    }

    private void setSliderCorners(boolean leftRounded, boolean rightRounded) {
        Drawable background = slider.getBackground();

        // Handle InsetDrawable case
        if (background instanceof InsetDrawable) {
            InsetDrawable insetDrawable = (InsetDrawable) background;
            background = insetDrawable.getDrawable();
        }

        // Now try to get the GradientDrawable
        if (background instanceof GradientDrawable) {
            GradientDrawable drawable = (GradientDrawable) background;
            if (leftRounded && rightRounded) {
                // All corners rounded (during animation)
                drawable.setCornerRadius(cornerRadius);
            } else if (leftRounded) {
                // Only left corners rounded (final employee state)
                drawable.setCornerRadii(new float[]{
                        cornerRadius, cornerRadius,  // Top-left
                        0, 0,                        // Top-right
                        0, 0,                        // Bottom-right
                        cornerRadius, cornerRadius    // Bottom-left
                });
            } else if (rightRounded) {
                // Only right corners rounded (final HR state)
                drawable.setCornerRadii(new float[]{
                        0, 0,                        // Top-left
                        cornerRadius, cornerRadius,   // Top-right
                        cornerRadius, cornerRadius,   // Bottom-right
                        0, 0                         // Bottom-left
                });
            } else {
                // No corners rounded (shouldn't happen)
                drawable.setCornerRadii(new float[8]);
            }
        }
    }

    private void loginUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        // Reset errors and stroke colors
        emailLayout.setError(null);
        passwordLayout.setError(null);
        emailLayout.setBoxStrokeColor(ContextCompat.getColor(this, R.color.default_outline));
        passwordLayout.setBoxStrokeColor(ContextCompat.getColor(this, R.color.default_outline));

        if (TextUtils.isEmpty(email)) {
            emailLayout.setError("Email is required");
            emailLayout.setBoxStrokeColor(ContextCompat.getColor(this, R.color.error_red));
            return;
        }

        if (TextUtils.isEmpty(password)) {
            passwordLayout.setError("Password is required");
            passwordLayout.setBoxStrokeColor(ContextCompat.getColor(this, R.color.error_red));
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        fetchUserRole(mAuth.getCurrentUser().getUid());
                    } else {
                        // Reset previous errors
                        emailLayout.setError(null);
                        passwordLayout.setError(null);

                        String error = task.getException().getMessage();
                        Toast.makeText(LoginActivity.this, "Login failed: " + error, Toast.LENGTH_SHORT).show();

                        // Show error on fields
                        if (error.toLowerCase().contains("email")) {
                            emailLayout.setError(error);
                            emailLayout.setBoxStrokeColor(ContextCompat.getColor(this, R.color.error_red));
                        } else if (error.toLowerCase().contains("password")) {
                            passwordLayout.setError(error);
                            passwordLayout.setBoxStrokeColor(ContextCompat.getColor(this, R.color.error_red));
                        }
                    }
                });
    }

    private void fetchUserRole(String userId) {
        FirebaseFirestore.getInstance()
                .collection("employee")
                .document(userId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {
                            String role = document.getString("role");

                            // Verify selected role matches account role
                            if (selectedRole.equals("hr") && !"hr".equals(role)) {
                                Toast.makeText(LoginActivity.this,
                                        "This account is not authorized as HR",
                                        Toast.LENGTH_SHORT).show();
                                FirebaseAuth.getInstance().signOut();
                                return;
                            }

                            redirectBasedOnRole(role);
                        } else {
                            handleMissingUserData();
                        }
                    } else {
                        Toast.makeText(LoginActivity.this,
                                "Failed to fetch user data: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void handleMissingUserData() {
        new AlertDialog.Builder(this)
                .setTitle("Account Issue")
                .setMessage("Your account isn't fully set up. Contact HR.")
                .setPositiveButton("OK", (d, w) -> {
                    FirebaseAuth.getInstance().signOut();
                })
                .show();
    }

    private void redirectBasedOnRole(String role) {
        Intent intent;
        if ("hr".equals(role)) {
            intent = new Intent(this, HRDashboardActivity.class);
        } else {
            intent = new Intent(this, MainActivity.class);
        }

        // Store role in SharedPreferences
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        prefs.edit().putString("user_role", role).apply();

        startActivity(intent);
        finish();
    }
}