package org.example.semscan.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.text.TextUtils;
import android.widget.TextView;

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
    private TextView textWelcomeMessage;
    private PreferencesManager preferencesManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_picker);
        
        Logger.i(Logger.TAG_UI, "RolePickerActivity created");
        
        preferencesManager = PreferencesManager.getInstance(this);
        
        initializeViews();
        setupToolbar();
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
        textWelcomeMessage = findViewById(R.id.text_welcome_message);
        updateWelcomeMessage();
    }

    private void updateWelcomeMessage() {
        String firstName = preferencesManager.getFirstName();
        String lastName = preferencesManager.getLastName();

        String displayName = "";
        if (!TextUtils.isEmpty(firstName) && !TextUtils.isEmpty(lastName)) {
            displayName = firstName + " " + lastName;
        } else if (!TextUtils.isEmpty(firstName)) {
            displayName = firstName;
        } else {
            // Fallback to username if no name is set
            displayName = preferencesManager.getUserName();
        }

        if (TextUtils.isEmpty(displayName)) {
            textWelcomeMessage.setText(R.string.welcome_default);
            return;
        }

        textWelcomeMessage.setText(getString(R.string.welcome_user, displayName));
        Logger.i(Logger.TAG_UI, "Welcome message set for: " + displayName);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        // Explicitly set hamburger icon
        toolbar.setNavigationIcon(R.drawable.ic_menu_dark);

        // Set up hamburger menu click
        toolbar.setNavigationOnClickListener(v -> showHamburgerMenu(toolbar));
    }

    private void showHamburgerMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_hamburger, popup.getMenu());

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_settings) {
                openSettings();
                return true;
            } else if (id == R.id.action_logout) {
                showLogoutDialog();
                return true;
            }
            return false;
        });

        popup.show();
    }

    private void showLogoutDialog() {
        Logger.userAction("Logout", "User tapped logout from role picker menu");

        new AlertDialog.Builder(this)
                .setTitle(R.string.logout_confirm_title)
                .setMessage(R.string.logout_confirm_message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    performLogout();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void performLogout() {
        Logger.i(Logger.TAG_UI, "Performing logout from role picker");
        preferencesManager.clearUserData();
        // Note: Do NOT clear saved credentials here - "Remember Me" should persist after logout

        Toast.makeText(this, R.string.logout_success, Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
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
