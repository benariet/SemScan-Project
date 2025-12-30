package org.example.semscan.ui;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.example.semscan.R;
import org.example.semscan.data.api.ApiClient;
import org.example.semscan.data.api.ApiService;
import org.example.semscan.ui.auth.LoginActivity;
import org.example.semscan.utils.ConfigManager;
import org.example.semscan.utils.Logger;
import org.example.semscan.utils.PreferencesManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SettingsActivity extends AppCompatActivity {

    // Personal Information
    private TextInputEditText editUsername;
    private TextInputEditText editFirstName;
    private TextInputEditText editLastName;
    private TextInputEditText editNationalId;

    // Academic Degree
    private Spinner spinnerDegree;

    // Buttons
    private MaterialButton btnSave;
    private MaterialButton btnLogout;
    private MaterialButton btnReportBug;

    // Version
    private TextView textVersion;

    private PreferencesManager preferencesManager;
    private ApiService apiService;

    private static final String[] DEGREES = {"M.Sc.", "Ph.D."};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Logger.i(Logger.TAG_UI, "SettingsActivity created");

        preferencesManager = PreferencesManager.getInstance(this);
        apiService = ApiClient.getInstance(this).getApiService();

        initializeViews();
        setupToolbar();
        setupSpinner();
        setupClickListeners();
        loadCurrentSettings();
        fetchUserProfileFromServer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCurrentSettings();
    }

    private void initializeViews() {
        // Personal Information
        editUsername = findViewById(R.id.edit_username);
        editFirstName = findViewById(R.id.edit_first_name);
        editLastName = findViewById(R.id.edit_last_name);
        editNationalId = findViewById(R.id.edit_national_id);

        // Academic Degree
        spinnerDegree = findViewById(R.id.spinner_degree);

        // Buttons
        btnSave = findViewById(R.id.btn_save);
        btnLogout = findViewById(R.id.btn_logout);
        btnReportBug = findViewById(R.id.btn_report_bug);

        // Version
        textVersion = findViewById(R.id.text_version);
        updateVersionDisplay();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                DEGREES
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDegree.setAdapter(adapter);
    }

    private void setupClickListeners() {
        btnSave.setOnClickListener(v -> saveSettings());
        btnLogout.setOnClickListener(v -> showLogoutDialog());
        btnReportBug.setOnClickListener(v -> reportBugOrProblem());
    }

    private void loadCurrentSettings() {
        Logger.i(Logger.TAG_UI, "Loading current settings");

        // Load Username (read-only)
        String username = preferencesManager.getUserName();
        if (username != null && !username.isEmpty()) {
            editUsername.setText(username);
        }

        // Load Personal Information
        String firstName = preferencesManager.getFirstName();
        String lastName = preferencesManager.getLastName();
        String nationalId = preferencesManager.getNationalId();

        if (firstName != null && !firstName.isEmpty()) {
            editFirstName.setText(firstName);
        }
        if (lastName != null && !lastName.isEmpty()) {
            editLastName.setText(lastName);
        }
        if (nationalId != null && !nationalId.isEmpty()) {
            editNationalId.setText(nationalId);
        }

        // Load Academic Degree
        String degree = preferencesManager.getDegree();
        if (degree != null && !degree.isEmpty()) {
            // Normalize degree value - database stores "PhD"/"MSc", spinner shows "Ph.D."/"M.Sc."
            String normalizedDegree = degree.toUpperCase().replace(".", "").replace(" ", "");
            for (int i = 0; i < DEGREES.length; i++) {
                String normalizedOption = DEGREES[i].toUpperCase().replace(".", "").replace(" ", "");
                if (normalizedOption.equals(normalizedDegree)) {
                    spinnerDegree.setSelection(i);
                    break;
                }
            }
        }
    }

    private void saveSettings() {
        Logger.userAction("Save Settings", "User clicked save settings button");

        // Get values
        String firstName = editFirstName.getText() != null ? editFirstName.getText().toString().trim() : "";
        String lastName = editLastName.getText() != null ? editLastName.getText().toString().trim() : "";
        String nationalId = editNationalId.getText() != null ? editNationalId.getText().toString().trim() : "";
        String degree = spinnerDegree.getSelectedItem().toString();
        String username = preferencesManager.getUserName();
        String email = preferencesManager.getEmail();
        String participation = preferencesManager.getParticipationPreference();

        // Disable save button while saving
        btnSave.setEnabled(false);
        btnSave.setText("Saving...");

        try {
            // Save locally first
            preferencesManager.setFirstName(firstName);
            preferencesManager.setLastName(lastName);
            preferencesManager.setNationalId(nationalId);
            preferencesManager.setDegree(degree);

            // Normalize degree for API (M.Sc. -> MSc, Ph.D. -> PhD)
            String normalizedDegree = degree.replace(".", "").replace(" ", "");
            if ("MSc".equalsIgnoreCase(normalizedDegree)) {
                normalizedDegree = "MSc";
            } else if ("PhD".equalsIgnoreCase(normalizedDegree)) {
                normalizedDegree = "PhD";
            }

            // Update on server
            ApiService.UserProfileUpdateRequest request = new ApiService.UserProfileUpdateRequest(
                    username,
                    email,
                    firstName,
                    lastName,
                    normalizedDegree,
                    participation,
                    nationalId
            );

            apiService.upsertUser(request).enqueue(new Callback<ApiService.UserProfileResponse>() {
                @Override
                public void onResponse(Call<ApiService.UserProfileResponse> call, Response<ApiService.UserProfileResponse> response) {
                    runOnUiThread(() -> {
                        btnSave.setEnabled(true);
                        btnSave.setText(R.string.save);

                        if (response.isSuccessful()) {
                            Logger.i(Logger.TAG_UI, "Settings saved to server successfully");
                            Toast.makeText(SettingsActivity.this, "Settings saved successfully!", Toast.LENGTH_SHORT).show();
                        } else {
                            Logger.w(Logger.TAG_UI, "Failed to save settings to server: " + response.code());
                            Toast.makeText(SettingsActivity.this, "Saved locally, but failed to sync to server", Toast.LENGTH_LONG).show();
                        }
                    });
                }

                @Override
                public void onFailure(Call<ApiService.UserProfileResponse> call, Throwable t) {
                    runOnUiThread(() -> {
                        btnSave.setEnabled(true);
                        btnSave.setText(R.string.save);
                        Logger.e(Logger.TAG_UI, "Failed to save settings to server", t);
                        Toast.makeText(SettingsActivity.this, "Saved locally, but failed to sync to server", Toast.LENGTH_LONG).show();
                    });
                }
            });

        } catch (Exception e) {
            btnSave.setEnabled(true);
            btnSave.setText(R.string.save);
            Logger.e(Logger.TAG_UI, "Failed to save settings", e);
            Toast.makeText(this, R.string.error_save_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void showLogoutDialog() {
        Logger.userAction("Logout", "User tapped logout button");

        new AlertDialog.Builder(this)
                .setTitle(R.string.logout_confirm_title)
                .setMessage(R.string.logout_confirm_message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> performLogout())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void performLogout() {
        Logger.i(Logger.TAG_UI, "Performing logout");
        preferencesManager.clearUserData();
        // Note: Do NOT clear saved credentials here - "Remember Me" should persist after logout

        Toast.makeText(this, R.string.logout_success, Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void reportBugOrProblem() {
        Logger.userAction("Report Bug", "User clicked report bug button");

        try {
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

            String supportEmail = ConfigManager.getInstance(this).getSupportEmail();
            String subject = "SemScan - Bug Report or Problem";
            String bodyText = body.toString();
            String mailtoUri = "mailto:" + supportEmail +
                    "?subject=" + Uri.encode(subject) +
                    "&body=" + Uri.encode(bodyText);

            Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
            emailIntent.setData(Uri.parse(mailtoUri));

            if (emailIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(Intent.createChooser(emailIntent, "Send email via..."));
            } else {
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

    private void updateVersionDisplay() {
        String version = ConfigManager.getInstance(this).getAppVersion();
        if (textVersion != null) {
            textVersion.setText(getString(R.string.version, version));
        }
    }

    private void fetchUserProfileFromServer() {
        String username = preferencesManager.getUserName();
        if (username == null || username.isEmpty()) {
            Logger.w(Logger.TAG_UI, "Cannot fetch user profile - no username");
            return;
        }

        Logger.i(Logger.TAG_UI, "Fetching user profile from server for: " + username);

        Call<ApiService.UserProfileResponse> call = apiService.getUserProfile(username);
        call.enqueue(new Callback<ApiService.UserProfileResponse>() {
            @Override
            public void onResponse(Call<ApiService.UserProfileResponse> call, Response<ApiService.UserProfileResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiService.UserProfileResponse profile = response.body();
                    Logger.i(Logger.TAG_UI, "User profile fetched successfully");

                    // Update national ID from server if available
                    if (profile.nationalIdNumber != null && !profile.nationalIdNumber.isEmpty()) {
                        runOnUiThread(() -> {
                            editNationalId.setText(profile.nationalIdNumber);
                            // Also save to preferences for offline access
                            preferencesManager.setNationalId(profile.nationalIdNumber);
                        });
                        Logger.i(Logger.TAG_UI, "National ID populated from server");
                    }
                } else {
                    Logger.w(Logger.TAG_UI, "Failed to fetch user profile: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ApiService.UserProfileResponse> call, Throwable t) {
                Logger.e(Logger.TAG_UI, "Failed to fetch user profile from server", t);
                // Silently fail - use local data
            }
        });
    }
}
