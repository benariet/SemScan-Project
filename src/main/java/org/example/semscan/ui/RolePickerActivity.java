package org.example.semscan.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import org.example.semscan.R;
import org.example.semscan.ui.auth.LoginActivity;
import org.example.semscan.ui.student.StudentHomeActivity;
import org.example.semscan.ui.teacher.PresenterHomeActivity;
import org.example.semscan.utils.Logger;
import org.example.semscan.utils.PreferencesManager;

public class RolePickerActivity extends AppCompatActivity {
    
    private MaterialCardView cardPresenter;
    private MaterialCardView cardStudent;
    private MaterialButton btnSettings;
    private PreferencesManager preferencesManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_picker);
        
        Logger.i(Logger.TAG_UI, "RolePickerActivity created");
        
        preferencesManager = PreferencesManager.getInstance(this);
        
        initializeViews();
        setupClickListeners();
        
        // Check if user already has a role selected
        if (preferencesManager.hasRole()) {
            // CRITICAL: Validate username before auto-navigation
            String username = preferencesManager.getUserName();
            if (username == null || username.isEmpty()) {
                Logger.e(Logger.TAG_UI, "ERROR: Username is NULL or empty in RolePickerActivity.onCreate()!");
                Logger.e(Logger.TAG_UI, "User has role but no username. This should not happen. Redirecting to login.");
                Toast.makeText(this, "Username not found. Please log in again.", Toast.LENGTH_LONG).show();
                // Navigate back to login
                Intent intent = new Intent(this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
                return;
            }
            
            String currentRole = preferencesManager.getUserRole();
            Logger.i(Logger.TAG_UI, "User already has role: " + currentRole + " with username: " + username + ", navigating to home");
            navigateToHome();
        } else {
            Logger.i(Logger.TAG_UI, "No role selected, showing role picker");
        }
    }
    
    private void initializeViews() {
        cardPresenter = findViewById(R.id.card_presenter);
        cardStudent = findViewById(R.id.card_student);
        btnSettings = findViewById(R.id.btn_settings);
    }
    
    private void setupClickListeners() {
        cardPresenter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectRole("PRESENTER");
            }
        });
        
        cardStudent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectRole("PARTICIPANT");
            }
        });
        
        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSettings();
            }
        });
    }
    
    private void selectRole(String role) {
        Logger.userAction("Select Role", "User selected role: " + role);
        Logger.i(Logger.TAG_UI, "Setting user role to: " + role);
        
        // CRITICAL: Ensure username is preserved when setting role
        String username = preferencesManager.getUserName();
        if (username == null || username.isEmpty()) {
            Logger.e(Logger.TAG_UI, "ERROR: Username is NULL or empty in RolePickerActivity!");
            Logger.e(Logger.TAG_UI, "Cannot set role without username. User must log in again.");
            Toast.makeText(this, "Username not found. Please log in again.", Toast.LENGTH_LONG).show();
            // Navigate back to login
            Intent intent = new Intent(this, org.example.semscan.ui.auth.LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }
        
        Logger.i(Logger.TAG_UI, "Setting role with username: " + username);
        preferencesManager.setUserRole(role);
        
        // Final check: Log all values before navigation
        Logger.i(Logger.TAG_UI, "=== Role Selected ===");
        Logger.i(Logger.TAG_UI, "Username: " + preferencesManager.getUserName());
        Logger.i(Logger.TAG_UI, "Role: " + preferencesManager.getUserRole());
        Logger.i(Logger.TAG_UI, "Is Participant: " + preferencesManager.isParticipant());
        Logger.i(Logger.TAG_UI, "Is Presenter: " + preferencesManager.isPresenter());
        Logger.i(Logger.TAG_UI, "=====================");
        
        navigateToHome();
    }
    
    private void navigateToHome() {
        Intent intent;
        String targetActivity;
        
        if (preferencesManager.isPresenter()) {
            intent = new Intent(this, PresenterHomeActivity.class);
            targetActivity = "PresenterHomeActivity";
        } else if (preferencesManager.isParticipant()) {
            intent = new Intent(this, StudentHomeActivity.class);
            targetActivity = "StudentHomeActivity";
        } else {
            // This shouldn't happen, but just in case
            Logger.w(Logger.TAG_UI, "No valid role found for navigation");
            Toast.makeText(this, "Please select a role", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Logger.i(Logger.TAG_UI, "Navigating to: " + targetActivity);
        
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    private void openSettings() {
        Logger.userAction("Open Settings", "User clicked settings from role picker");
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }
    
    @Override
    public void onBackPressed() {
        // Prevent going back from role picker
        moveTaskToBack(true);
    }
}
