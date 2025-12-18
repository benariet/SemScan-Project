package org.example.semscan.ui;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;

import org.example.semscan.R;
import org.example.semscan.ui.auth.LoginActivity;
import org.example.semscan.utils.ConfigManager;
import org.example.semscan.utils.Logger;
import org.example.semscan.utils.PreferencesManager;

public class SettingsActivity extends AppCompatActivity {

    private EditText editUsername;
    private MaterialButton btnSave;
    private MaterialButton btnClearData;
    private MaterialButton btnLogout;
    private MaterialButton btnReportBug;

    private PreferencesManager preferencesManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Logger.i(Logger.TAG_UI, "SettingsActivity created");

        preferencesManager = PreferencesManager.getInstance(this);

        initializeViews();
        setupToolbar();
        setupClickListeners();
        loadCurrentSettings();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload settings in case username was updated elsewhere
        loadCurrentSettings();
    }

    private void initializeViews() {
        editUsername = findViewById(R.id.edit_username);
        btnSave = findViewById(R.id.btn_save);
        btnClearData = findViewById(R.id.btn_clear_data);
        btnLogout = findViewById(R.id.btn_logout);
        btnReportBug = findViewById(R.id.btn_report_bug);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void setupClickListeners() {
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
            }
        });

        btnClearData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showClearDataDialog();
            }
        });

        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLogoutDialog();
            }
        });

        btnReportBug.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                reportBugOrProblem();
            }
        });
    }

    private void loadCurrentSettings() {
        String username = preferencesManager.getUserName();
        Logger.i(Logger.TAG_UI, "Loading current settings");
        Logger.i(Logger.TAG_UI, "Current username from preferences: " + username);
        
        // Also check if there's a bguUsername stored
        if (username == null || username.isEmpty()) {
            // Try to get from other sources if needed
            Logger.w(Logger.TAG_UI, "Username is null or empty, checking alternative sources");
        }

        if (username != null && !username.isEmpty()) {
            editUsername.setText(username);
            Logger.i(Logger.TAG_UI, "Username set in EditText: " + username);
        } else {
            editUsername.setText("");
            Logger.w(Logger.TAG_UI, "Username is empty, EditText cleared");
        }
    }

    private void saveSettings() {
        Logger.userAction("Save Settings", "User clicked save settings button");

        String usernameInput = editUsername.getText().toString().trim();

        if (usernameInput.isEmpty()) {
            Logger.w(Logger.TAG_UI, "Save settings failed - Username is empty");
            editUsername.setError(getString(R.string.login_username_required));
            Toast.makeText(this, R.string.login_username_required, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            preferencesManager.setUserName(usernameInput);

            Logger.i(Logger.TAG_UI, "Settings saved successfully for username=" + usernameInput);
            Toast.makeText(this, R.string.settings_username_saved, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Logger.e(Logger.TAG_UI, "Failed to save settings", e);
            Toast.makeText(this, R.string.error_save_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void showClearDataDialog() {
        Logger.userAction("Clear Data Dialog", "User clicked clear data button");

        new AlertDialog.Builder(this)
                .setTitle("Clear All Data")
                .setMessage("This will clear all user data and settings. Are you sure?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        clearAllData();
                    }
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void clearAllData() {
        Logger.userAction("Clear All Data", "User confirmed clearing all data");

        preferencesManager.clearAll();
        Toast.makeText(this, "All data cleared", Toast.LENGTH_SHORT).show();

        finish();
    }

    private void showLogoutDialog() {
        Logger.userAction("Logout", "User tapped logout button");

        new AlertDialog.Builder(this)
                .setTitle(R.string.logout_confirm_title)
                .setMessage(R.string.logout_confirm_message)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        performLogout();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void performLogout() {
        Logger.i(Logger.TAG_UI, "Performing logout");
        preferencesManager.clearUserData();

        Toast.makeText(this, R.string.logout_success, Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void reportBugOrProblem() {
        Logger.userAction("Report Bug", "User clicked report bug button");

        try {
            // Pre-fill body with user info if available
            String username = preferencesManager.getUserName();
            String userRole = preferencesManager.getUserRole();
            StringBuilder body = new StringBuilder();
            body.append("Hello,\n\n");
            body.append("I'm reporting a bug or having a problem with SemScan.\n\n");
            if (username != null && !username.isEmpty()) {
                body.append("Username: ").append(username).append("\n");
            }
            if (userRole != null && !userRole.isEmpty()) {
                body.append("Role: ").append(userRole).append("\n");
            }
            body.append("\n");
            body.append("Description of the issue:\n");
            body.append("[Please describe the bug or problem here]\n\n");
            body.append("Steps to reproduce:\n");
            body.append("[Please describe the steps]\n\n");
            body.append("Expected behavior:\n");
            body.append("[What should happen?]\n\n");
            body.append("Actual behavior:\n");
            body.append("[What actually happens?]\n");
            
            // Build mailto URI with recipient, subject, and body
            String supportEmail = ConfigManager.getInstance(this).getSupportEmail();
            String subject = "SemScan - Bug Report or Problem";
            String bodyText = body.toString();
            String mailtoUri = "mailto:" + supportEmail +
                    "?subject=" + Uri.encode(subject) +
                    "&body=" + Uri.encode(bodyText);
            
            // Create email intent with mailto URI
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
            emailIntent.setData(Uri.parse(mailtoUri));

            // Check if there's an app that can handle this intent
            if (emailIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(Intent.createChooser(emailIntent, "Send email via..."));
            } else {
                // No email app available - show message
                Toast.makeText(this, "No email app found. Please send an email to " + supportEmail, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Logger.e(Logger.TAG_UI, "Failed to open email intent", e);
            String supportEmail = ConfigManager.getInstance(this).getSupportEmail();
            Toast.makeText(this, "Failed to open email. Please send an email to " + supportEmail, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
