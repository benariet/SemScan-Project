package org.example.semscan.ui.student;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.text.TextUtils;
import android.widget.PopupMenu;
import android.widget.Toast;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.card.MaterialCardView;

import org.example.semscan.R;
import org.example.semscan.data.api.ApiClient;
import org.example.semscan.data.api.ApiService;
import org.example.semscan.data.model.Session;
import org.example.semscan.ui.RolePickerActivity;
import org.example.semscan.ui.SettingsActivity;
import org.example.semscan.ui.auth.LoginActivity;
import org.example.semscan.ui.qr.ModernQRScannerActivity;
import org.example.semscan.utils.ConfigManager;
import org.example.semscan.utils.Logger;
import org.example.semscan.utils.PreferencesManager;
import org.example.semscan.utils.ServerLogger;

import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class StudentHomeActivity extends AppCompatActivity {

    private static final int REQUEST_NOTIFICATION_PERMISSION = 1001;

    private MaterialCardView cardScanAttendance;
    private MaterialCardView cardManualAttendance;
    private MaterialCardView btnChangeRole;
    private PreferencesManager preferencesManager;
    private ApiService apiService;
    private ServerLogger serverLogger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_home);

        Logger.i(Logger.TAG_SCREEN_VIEW, "StudentHomeActivity created");
        Logger.userAction("Open Student Home", "Student home screen opened");

        preferencesManager = PreferencesManager.getInstance(this);
        apiService = ApiClient.getInstance(this).getApiService();
        serverLogger = ServerLogger.getInstance(this);
        
        // Update user context for student logging
        String username = preferencesManager.getUserName();
        String userRole = preferencesManager.getUserRole();
        serverLogger.updateUserContext(username, userRole);

        // Test logging to verify student context
        serverLogger.i(ServerLogger.TAG_SCREEN_VIEW, "StudentHomeActivity created - User: " + username + ", Role: " + userRole);

        // Check if user is actually a participant
        if (!preferencesManager.isParticipant()) {
            Logger.w(Logger.TAG_NAVIGATION, "User is not a participant, navigating to role picker");
            navigateToRolePicker();
            return;
        }

        // Request notification permission for Android 13+
        requestNotificationPermission();

        Logger.i(Logger.TAG_SCREEN_VIEW, "Student user authenticated, setting up UI");
        if (serverLogger != null) {
            serverLogger.userAction("Student Authenticated", "Student home setup initialized");
        }
        initializeViews();
        setupToolbar();
        setupClickListeners();
    }

    private void initializeViews() {
        cardScanAttendance = findViewById(R.id.card_scan_attendance);
        cardManualAttendance = findViewById(R.id.card_manual_attendance);
        btnChangeRole = findViewById(R.id.btn_change_role);

        // Check manual attendance config and update visibility
        updateManualAttendanceVisibility();
    }

    private void updateManualAttendanceVisibility() {
        // Default: show button. Only hide if API explicitly says disabled.
        apiService.getMobileConfig().enqueue(new Callback<ApiService.MobileConfigResponse>() {
            @Override
            public void onResponse(Call<ApiService.MobileConfigResponse> call, Response<ApiService.MobileConfigResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    boolean enabled = response.body().manualAttendanceEnabled;
                    cardManualAttendance.setVisibility(enabled ? View.VISIBLE : View.GONE);
                    Logger.i(Logger.TAG_PREFS, "Manual attendance enabled=" + enabled);
                }
            }

            @Override
            public void onFailure(Call<ApiService.MobileConfigResponse> call, Throwable t) {
                // Keep visible on failure (default)
                Logger.w(Logger.TAG_PREFS, "Failed to check manual attendance config: " + t.getMessage());
            }
        });
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }

        // Explicitly set hamburger icon
        toolbar.setNavigationIcon(R.drawable.ic_menu);

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

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
        Logger.userAction("Navigate", "Opened SettingsActivity from student home");
        if (serverLogger != null) {
            serverLogger.userAction("Navigate", "Opened SettingsActivity from student home");
        }
    }

    private void setupClickListeners() {
        cardScanAttendance.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Logger.userAction("Open QR Scanner", "Student tapped scan attendance card");
                if (serverLogger != null) {
                    serverLogger.userAction("Open QR Scanner", "Student tapped scan attendance card");
                }
                openQRScanner();
            }
        });

        cardManualAttendance.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Logger.userAction("Open Manual Attendance", "Student tapped manual attendance card");
                if (serverLogger != null) {
                    serverLogger.userAction("Open Manual Attendance", "Student tapped manual attendance card");
                }
                openManualAttendanceRequest();
            }
        });

        btnChangeRole.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Logger.userAction("Change Role", "Student clicked change role button");
                if (serverLogger != null) {
                    serverLogger.userAction("Change Role", "Student clicked change role button");
                }
                changeRole();
            }
        });
    }

    private void openQRScanner() {
        Logger.userAction("Open QR Scanner", "Checking for open sessions before opening scanner");
        if (serverLogger != null) {
            serverLogger.userAction("Open QR Scanner", "Checking for open sessions before opening scanner");
        }
        
        // Check if there are any open sessions before allowing QR scanner to open
        checkForOpenSessions();
    }
    
    private void checkForOpenSessions() {
        Logger.i(Logger.TAG_QR_SCAN, "Checking for open sessions before opening QR scanner");
        
        if (serverLogger != null) {
            serverLogger.i(ServerLogger.TAG_QR_SCAN, "CHECKING OPEN SESSIONS - Before opening QR scanner");
        }
        
        Call<List<Session>> call = apiService.getOpenSessions();
        call.enqueue(new Callback<List<Session>>() {
            @Override
            public void onResponse(Call<List<Session>> call, Response<List<Session>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Session> allSessions = response.body();
                    
                    // Filter to only OPEN sessions
                    List<Session> openSessions = new java.util.ArrayList<>();
                    for (Session session : allSessions) {
                        if (session != null && session.getStatus() != null) {
                            String status = session.getStatus().toUpperCase();
                            if ("OPEN".equals(status)) {
                                openSessions.add(session);
                            }
                        }
                    }
                    
                    Logger.i(Logger.TAG_QR_SCAN, "Found " + openSessions.size() + " open sessions");

                    if (serverLogger != null) {
                        serverLogger.i(ServerLogger.TAG_QR_SCAN, "OPEN SESSIONS CHECK - Found " + openSessions.size() + " open sessions");
                    }
                    
                    if (openSessions.isEmpty()) {
                        // No open sessions - show error and don't open scanner
                        Logger.w(Logger.TAG_QR_SCAN, "No open sessions available - preventing QR scanner from opening");

                        if (serverLogger != null) {
                            serverLogger.w(ServerLogger.TAG_QR_SCAN, "QR SCANNER BLOCKED - No open sessions available");
                        }
                        
                        showNoSessionsError();
                    } else {
                        // Open sessions available - proceed to open scanner
                        Logger.i(Logger.TAG_QR_SCAN, "Open sessions available - opening QR scanner");
                        
                        if (serverLogger != null) {
                            serverLogger.userAction("Navigate", "Launching ModernQRScannerActivity - " + openSessions.size() + " open sessions available");
                        }
                        
                        Intent intent = new Intent(StudentHomeActivity.this, ModernQRScannerActivity.class);
                        startActivity(intent);
                    }
                } else {
                    // API error - show error message
                    Logger.e(Logger.TAG_QR_SCAN, "Failed to check for open sessions - Status: " + response.code());

                    if (serverLogger != null) {
                        serverLogger.e(ServerLogger.TAG_QR_SCAN, "FAILED TO CHECK OPEN SESSIONS - Status Code: " + response.code());
                    }
                    
                    showNoSessionsError();
                }
            }
            
            @Override
            public void onFailure(Call<List<Session>> call, Throwable t) {
                Logger.e(Logger.TAG_QR_SCAN, "Failed to check for open sessions", t);

                if (serverLogger != null) {
                    serverLogger.e(ServerLogger.TAG_QR_SCAN, "NETWORK FAILURE - Failed to check open sessions: " + t.getMessage());
                }
                
                // On network failure, show error but don't block (could be temporary network issue)
                // However, for security, we should still block if we can't verify sessions exist
                showNoSessionsError();
            }
        });
    }
    
    private void showNoSessionsError() {
        new AlertDialog.Builder(this)
                .setTitle("No Active Sessions")
                .setMessage("There are no active sessions available right now. Please wait for a presenter to open a session before scanning a QR code.")
                .setPositiveButton(android.R.string.ok, null)
                .setCancelable(true)
                .show();
    }

    private void openManualAttendanceRequest() {
        Logger.userAction("Navigate", "Launching ManualAttendanceRequestActivity");
        if (serverLogger != null) {
            serverLogger.userAction("Navigate", "Launching ManualAttendanceRequestActivity");
        }
        Intent intent = new Intent(this, ManualAttendanceRequestActivity.class);
        startActivity(intent);
    }

    private void changeRole() {
        Logger.userAction("Change Role", "Student clicked change role");
        if (serverLogger != null) {
            serverLogger.userAction("Change Role", "Student clicked change role");
        }
        
        // Preserve username when changing roles - only clear the role, not all user data
        String username = preferencesManager.getUserName();
        String firstName = preferencesManager.getFirstName();
        String lastName = preferencesManager.getLastName();
        String email = preferencesManager.getEmail();
        String degree = preferencesManager.getDegree();
        String participation = preferencesManager.getParticipationPreference();
        
        // Clear only role-related data
        preferencesManager.setUserRole(null);
        preferencesManager.setInitialSetupCompleted(false);
        
        // Restore user data (username is critical for role selection)
        if (username != null && !username.isEmpty()) {
            preferencesManager.setUserName(username);
            Logger.i(Logger.TAG_NAVIGATION, "Preserved username when changing role: " + username);
        }
        if (firstName != null && !firstName.isEmpty()) {
            preferencesManager.setFirstName(firstName);
        }
        if (lastName != null && !lastName.isEmpty()) {
            preferencesManager.setLastName(lastName);
        }
        if (email != null && !email.isEmpty()) {
            preferencesManager.setEmail(email);
        }
        if (degree != null && !degree.isEmpty()) {
            preferencesManager.setDegree(degree);
        }
        if (participation != null && !participation.isEmpty()) {
            preferencesManager.setParticipationPreference(participation);
        }
        
        navigateToRolePicker();
    }

    private void showLogoutDialog() {
        Logger.userAction("Logout", "Student tapped logout button");

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
        Logger.i(Logger.TAG_LOGOUT, "Performing logout");
        if (serverLogger != null) {
            serverLogger.userAction("Logout", "Student logged out");
        }
        preferencesManager.clearUserData();
        // Note: Do NOT clear saved credentials here - "Remember Me" should persist after logout

        Toast.makeText(this, R.string.logout_success, Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void navigateToRolePicker() {
        Intent intent = new Intent(this, RolePickerActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Menu removed - Change Role and Logout are now cards on the home screen
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Menu removed - Change Role and Logout are now cards on the home screen
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        // Prevent going back to role picker
        moveTaskToBack(true);
    }
    
    @Override
    protected void onDestroy() {
        if (serverLogger != null) {
            serverLogger.flushLogs();
        }
        super.onDestroy();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
                    showNotificationPermissionRationale();
                } else {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.POST_NOTIFICATIONS},
                            REQUEST_NOTIFICATION_PERMISSION);
                }
            }
        }
    }

    private void showNotificationPermissionRationale() {
        new AlertDialog.Builder(this)
                .setTitle("Enable Notifications")
                .setMessage("SemScan needs notification permission to alert you when your registration is approved or when you're promoted from a waiting list.")
                .setPositiveButton("Allow", (dialog, which) -> {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.POST_NOTIFICATIONS},
                            REQUEST_NOTIFICATION_PERMISSION);
                })
                .setNegativeButton("Not Now", null)
                .show();
    }

    private void showNotificationSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Notifications Disabled")
                .setMessage("To receive alerts about registration approvals and waiting list updates, please enable notifications in Settings.")
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    Intent intent = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                    intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
                    startActivity(intent);
                })
                .setNegativeButton("Not Now", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Logger.i(Logger.TAG_PREFS, "POST_NOTIFICATIONS permission granted");
            } else {
                Logger.w(Logger.TAG_PREFS, "POST_NOTIFICATIONS permission denied");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
                        showNotificationSettingsDialog();
                    }
                }
            }
        }
    }
}
