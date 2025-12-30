package org.example.semscan.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.widget.CheckBox;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.example.semscan.R;
import org.example.semscan.data.api.ApiClient;
import org.example.semscan.data.api.ApiService;
import org.example.semscan.data.model.User;
import org.example.semscan.ui.RolePickerActivity;
import org.example.semscan.ui.auth.FirstTimeSetupActivity;
import org.example.semscan.ui.teacher.PresenterHomeActivity;
import org.example.semscan.utils.ConfigManager;
import org.example.semscan.utils.Logger;
import org.example.semscan.utils.PreferencesManager;
import org.example.semscan.utils.ServerLogger;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private static final String DEFAULT_SKIP_USERNAME = "skiptester";

    private TextInputEditText editUsername;
    private TextInputEditText editPassword;
    private CheckBox checkboxRememberMe;
    private MaterialButton btnLogin;
    private MaterialButton btnSkipAuth;
    private MaterialButton btnTestEmail;

    private PreferencesManager preferencesManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        preferencesManager = PreferencesManager.getInstance(this);

        editUsername = findViewById(R.id.edit_username);
        editPassword = findViewById(R.id.edit_password);
        checkboxRememberMe = findViewById(R.id.checkbox_remember_me);
        btnLogin = findViewById(R.id.btn_login);
        // Buttons removed from layout but keeping references for potential future use
        btnSkipAuth = null; // findViewById(R.id.btn_skip_auth); // Button removed from layout
        btnTestEmail = null; // findViewById(R.id.btn_test_email); // Button removed from layout

        // Load saved credentials if "Remember Me" was previously checked
        loadSavedCredentials();

        btnLogin.setOnClickListener(v -> handleLogin());
        if (btnSkipAuth != null) {
            btnSkipAuth.setOnClickListener(v -> handleSkipAuth());
        }
        if (btnTestEmail != null) {
            btnTestEmail.setOnClickListener(v -> {
                Intent intent = new Intent(LoginActivity.this, TestEmailActivity.class);
                startActivity(intent);
            });
        }

        Logger.i(Logger.TAG_UI, "LoginActivity created");
    }

    private void handleLogin() {
        String normalized = normalizeInput(editUsername != null ? editUsername.getText() : null);
        if (TextUtils.isEmpty(normalized)) {
            if (editUsername != null) {
                editUsername.setError(getString(R.string.login_username_required));
                editUsername.requestFocus();
            }
            return;
        }

        String passwordInput = null;
        if (editPassword != null) {
            passwordInput = editPassword.getText() != null ? editPassword.getText().toString() : null;
            if (TextUtils.isEmpty(passwordInput)) {
                editPassword.setError(getString(R.string.login_password_required));
                editPassword.requestFocus();
                return;
            } else {
                editPassword.setError(null);
            }
        }

        final String password = passwordInput;
        final String username = normalized;

        preferencesManager.clearUserData();
        preferencesManager.setUserName(username);
        preferencesManager.setInitialSetupCompleted(false);

        Logger.userAction("Login", "User attempting login with username=" + username);
        Logger.i(Logger.TAG_UI, "=== LOGIN FLOW START ===");
        Logger.i(Logger.TAG_UI, "Login button clicked - username=" + username + ", password length=" + (password != null ? password.length() : 0));
        
        try {
            ServerLogger serverLogger = ServerLogger.getInstance(this);
            if (serverLogger != null) {
                serverLogger.i(ServerLogger.TAG_UI, "Login button clicked - username=" + username);
            }
        } catch (Exception e) {
            Logger.e(Logger.TAG_UI, "Failed to log to ServerLogger: " + e.getMessage());
        }

        // Disable login button to prevent multiple clicks
        btnLogin.setEnabled(false);
        btnLogin.setText(getString(R.string.login_button_loading));
        Logger.i(Logger.TAG_UI, "Login button disabled, calling performLogin()");

        // FIRST: Authenticate user via BGU external authentication
        performLogin(username, password);
    }

    private void performLogin(String normalized, String password) {
        Logger.i(Logger.TAG_API, "=== PERFORM LOGIN START ===");
        Logger.i(Logger.TAG_API, "performLogin() called - normalized username=" + normalized);
        
        // FIRST: Authenticate user via BGU external authentication API
        ApiService apiService = ApiClient.getInstance(this).getApiService();
        Logger.i(Logger.TAG_API, "ApiService obtained successfully");
        
        ApiService.LoginRequest loginRequest = new ApiService.LoginRequest(normalized, password != null ? password : "");
        Logger.i(Logger.TAG_API, "LoginRequest created - username=" + normalized + ", password provided=" + (password != null && !password.isEmpty()));
        
        Logger.i(Logger.TAG_API, "Calling login API endpoint...");
        apiService.login(loginRequest).enqueue(new Callback<ApiService.LoginResponse>() {
            @Override
            public void onResponse(Call<ApiService.LoginResponse> call, Response<ApiService.LoginResponse> response) {
                // Check if activity is still valid before processing
                if (isFinishing() || isDestroyed()) {
                    Logger.w(Logger.TAG_API, "Login response - Activity destroyed, ignoring");
                    return;
                }

                Logger.i(Logger.TAG_API, "=== LOGIN API RESPONSE RECEIVED ===");
                Logger.i(Logger.TAG_API, "Response code: " + response.code() + ", isSuccessful: " + response.isSuccessful());

                try {
                    if (response.isSuccessful() && response.body() != null) {
                        Logger.i(Logger.TAG_API, "Response is successful and body is not null");
                        ApiService.LoginResponse loginResponse = response.body();
                        Logger.i(Logger.TAG_API, "LoginResponse object parsed successfully");
                        
                        // Log all response fields for debugging
                        Logger.i(Logger.TAG_API, "Response fields - ok: " + loginResponse.ok + 
                            ", message: " + loginResponse.message + 
                            ", bguUsername: " + loginResponse.bguUsername + 
                            ", email: " + loginResponse.email + 
                            ", isFirstTime: " + loginResponse.isFirstTime + 
                            ", isPresenter: " + loginResponse.isPresenter + 
                            ", isParticipant: " + loginResponse.isParticipant);
                        
                        // Check if authentication was successful
                        if (!loginResponse.ok) {
                            Logger.w(Logger.TAG_API, "Authentication failed - ok=false, message=" + loginResponse.message);
                            // Authentication failed - show error and stop
                            btnLogin.setEnabled(true);
                            btnLogin.setText(getString(R.string.login_button));
                            String errorMsg = loginResponse.message != null ? loginResponse.message : "Invalid username or password";
                            Logger.w(Logger.TAG_API, "Authentication failed: " + errorMsg);
                            Toast.makeText(LoginActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                            return;
                        }
                        
                        Logger.i(Logger.TAG_API, "Authentication successful - ok=true, proceeding with user setup");
                        
                        // Authentication successful - now check if user exists in users database
                        String username = loginResponse.bguUsername != null ? loginResponse.bguUsername : normalized;
                        Logger.i(Logger.TAG_API, "Username determined: " + username + " (from bguUsername: " + loginResponse.bguUsername + ", fallback: " + normalized + ")");
                        
                        preferencesManager.setUserName(username);
                        Logger.i(Logger.TAG_API, "Username saved to preferences");
                        
                        if (loginResponse.email != null) {
                            preferencesManager.setEmail(loginResponse.email);
                            Logger.i(Logger.TAG_API, "Email saved to preferences: " + loginResponse.email);
                        } else {
                            Logger.i(Logger.TAG_API, "No email in response, skipping email save");
                        }
                        
                        // Save credentials if "Remember Me" is checked
                        if (checkboxRememberMe != null && checkboxRememberMe.isChecked()) {
                            Logger.i(Logger.TAG_API, "Remember Me is checked, saving credentials");
                            saveCredentials(normalized, password);
                        } else {
                            Logger.i(Logger.TAG_API, "Remember Me is not checked, clearing saved credentials");
                            // Clear saved credentials if "Remember Me" is unchecked
                            clearSavedCredentials();
                        }
                        
                        // Determine user role with null safety
                        String userRole = "UNKNOWN";
                        Logger.i(Logger.TAG_API, "Determining user role from response...");
                        try {
                            if (loginResponse.isPresenter) {
                                userRole = "PRESENTER";
                                Logger.i(Logger.TAG_API, "User role determined: PRESENTER (from isPresenter=true)");
                            } else if (loginResponse.isParticipant) {
                                userRole = "PARTICIPANT";
                                Logger.i(Logger.TAG_API, "User role determined: PARTICIPANT (from isParticipant=true)");
                            } else {
                                Logger.i(Logger.TAG_API, "User role determined: UNKNOWN (isPresenter=false, isParticipant=false)");
                            }
                        } catch (Exception e) {
                            Logger.e(Logger.TAG_API, "Failed to determine user role from login response: " + e.getMessage() + ", Exception: " + e.getClass().getSimpleName(), e);
                            // Use default UNKNOWN role
                        }
                        
                        Logger.i(Logger.TAG_API, "Updating ServerLogger context with username=" + username + ", role=" + userRole);
                        try {
                            ServerLogger.getInstance(LoginActivity.this).updateUserContext(username, userRole);
                            Logger.i(Logger.TAG_API, "ServerLogger context updated successfully");
                        } catch (Exception e) {
                            Logger.e(Logger.TAG_API, "Failed to update ServerLogger context: " + e.getMessage() + ", Exception: " + e.getClass().getSimpleName(), e);
                            // Continue even if ServerLogger fails
                        }
                        
                        // SECOND: Check if authenticated user exists in users database
                        Logger.i(Logger.TAG_API, "Calling checkUserExistsInDatabase() with username=" + username);
                        checkUserExistsInDatabase(username);
                    } else {
                        Logger.w(Logger.TAG_API, "=== LOGIN API RESPONSE FAILED ===");
                        Logger.w(Logger.TAG_API, "Response is not successful OR body is null");
                        Logger.w(Logger.TAG_API, "Response code: " + response.code() + ", body is null: " + (response.body() == null));

                        // Authentication failed - show error and stop
                        btnLogin.setEnabled(true);
                        btnLogin.setText(getString(R.string.login_button));

                        // Show user-friendly error message based on status code
                        String errorMsg;
                        if (response.code() == 401 || response.code() == 403) {
                            errorMsg = getString(R.string.login_invalid_credentials);
                        } else if (response.code() >= 500) {
                            errorMsg = getString(R.string.login_server_error);
                        } else {
                            errorMsg = getString(R.string.login_invalid_credentials);
                        }

                        // Log the actual error body for debugging
                        try {
                            if (response.errorBody() != null) {
                                String errorBody = response.errorBody().string();
                                Logger.w(Logger.TAG_API, "Error body content: " + errorBody);
                            }
                        } catch (Exception e) {
                            Logger.e(Logger.TAG_API, "Failed to read error body: " + e.getMessage(), e);
                        }

                        Logger.w(Logger.TAG_API, "Authentication failed, code=" + response.code());
                        Toast.makeText(LoginActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    // Catch any unexpected exceptions during response processing
                    Logger.e(Logger.TAG_API, "=== UNEXPECTED ERROR IN LOGIN RESPONSE PROCESSING ===");
                    Logger.e(Logger.TAG_API, "Exception type: " + e.getClass().getSimpleName());
                    Logger.e(Logger.TAG_API, "Exception message: " + e.getMessage());
                    Logger.e(Logger.TAG_API, "Stack trace:", e);
                    try {
                        ServerLogger serverLogger = ServerLogger.getInstance(LoginActivity.this);
                        if (serverLogger != null) {
                            serverLogger.e(ServerLogger.TAG_API, "Unexpected error processing login response: " + e.getMessage() + ", Exception: " + e.getClass().getSimpleName(), e);
                        }
                    } catch (Exception logEx) {
                        Logger.e(Logger.TAG_API, "Failed to log to ServerLogger: " + logEx.getMessage() + ", Exception: " + logEx.getClass().getSimpleName(), logEx);
                    }
                    btnLogin.setEnabled(true);
                    btnLogin.setText(getString(R.string.login_button));
                    Logger.e(Logger.TAG_API, "Login button re-enabled after error");
                    Toast.makeText(LoginActivity.this, "Login failed due to an unexpected error. Please try again.", Toast.LENGTH_LONG).show();
                }
                Logger.i(Logger.TAG_API, "=== LOGIN API RESPONSE HANDLING COMPLETE ===");
            }

            @Override
            public void onFailure(Call<ApiService.LoginResponse> call, Throwable t) {
                // Check if activity is still valid before updating UI
                if (isFinishing() || isDestroyed()) {
                    Logger.w(Logger.TAG_API, "Login failure callback - Activity destroyed, ignoring");
                    return;
                }

                Logger.e(Logger.TAG_API, "=== LOGIN API CALL FAILURE ===");
                Logger.e(Logger.TAG_API, "Network/API call failed - Exception type: " + t.getClass().getSimpleName());
                Logger.e(Logger.TAG_API, "Exception message: " + t.getMessage());
                Logger.e(Logger.TAG_API, "Stack trace:", t);

                try {
                    if (btnLogin != null) {
                        btnLogin.setEnabled(true);
                        btnLogin.setText(getString(R.string.login_button));
                    }
                    Logger.e(Logger.TAG_API, "Login button re-enabled after network failure");
                    
                    // Log network error to ServerLogger with full exception details
                    // This will be queued and sent when network is available
                    try {
                        ServerLogger serverLogger = ServerLogger.getInstance(LoginActivity.this);
                        String requestUrl = call.request() != null ? call.request().url().toString() : "unknown";
                        String errorDetails = String.format("Login network failure - URL: %s, Exception: %s, Message: %s",
                            requestUrl, t.getClass().getSimpleName(), t.getMessage());
                        serverLogger.e(ServerLogger.TAG_API, errorDetails, t);
                    } catch (Exception e) {
                        Logger.e(Logger.TAG_API, "Failed to log to ServerLogger: " + e.getMessage(), e);
                    }
                    
                    // Show user-friendly error message based on exception type
                    String errorMessage = "Network error. Please check your connection and try again.";
                    if (t instanceof java.net.SocketTimeoutException || t instanceof java.net.ConnectException) {
                        errorMessage = "Connection timeout. Please try again.";
                    } else if (t instanceof java.net.UnknownHostException) {
                        errorMessage = "Cannot connect to server. Please check your internet connection.";
                    } else if (t instanceof javax.net.ssl.SSLException) {
                        errorMessage = "SSL connection error. Please check your network settings.";
                        Logger.e(Logger.TAG_API, "Error type: SSLException");
                    } else {
                        Logger.e(Logger.TAG_API, "Error type: " + t.getClass().getSimpleName() + " (using generic message)");
                    }
                    Logger.e(Logger.TAG_API, "Showing error message to user: " + errorMessage);
                    Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    Logger.e(Logger.TAG_API, "=== LOGIN API CALL FAILURE HANDLING COMPLETE ===");
                } catch (Exception e) {
                    Logger.e(Logger.TAG_API, "=== UNEXPECTED ERROR IN ONFAILURE HANDLER ===");
                    Logger.e(Logger.TAG_API, "Exception type: " + e.getClass().getSimpleName());
                    Logger.e(Logger.TAG_API, "Exception message: " + e.getMessage());
                    Logger.e(Logger.TAG_API, "Stack trace:", e);
                    Toast.makeText(LoginActivity.this, "Login failed due to an unexpected error. Please try again.", Toast.LENGTH_LONG).show();
                }
            }
        });
        
        Logger.i(Logger.TAG_API, "Login API call enqueued, waiting for response...");
    }

    private void checkUserExistsInDatabase(String username) {
        // Check if authenticated user exists in users database
        ApiService apiService = ApiClient.getInstance(this).getApiService();
        ApiService.UserExistsRequest existsRequest = new ApiService.UserExistsRequest(username);
        apiService.checkUserExists(existsRequest).enqueue(new Callback<ApiService.UserExistsResponse>() {
            @Override
            public void onResponse(Call<ApiService.UserExistsResponse> call, Response<ApiService.UserExistsResponse> response) {
                Logger.i(Logger.TAG_API, "=== CHECK USER EXISTS RESPONSE RECEIVED ===");
                Logger.i(Logger.TAG_API, "Response code: " + response.code() + ", isSuccessful: " + response.isSuccessful() + ", body is null: " + (response.body() == null));
                
                if (response.isSuccessful() && response.body() != null) {
                    ApiService.UserExistsResponse existsResponse = response.body();
                    Logger.i(Logger.TAG_API, "UserExistsResponse parsed - exists: " + existsResponse.exists);
                    
                    if (!existsResponse.exists) {
                        // User authenticated but doesn't exist in DB - go to initial setup
                        Logger.i(Logger.TAG_API, "User authenticated but NOT in DB - navigating to initial setup");
                        // Ensure username is set
                        preferencesManager.setUserName(username);
                        Logger.i(Logger.TAG_API, "Username saved to preferences: " + username);
                        preferencesManager.setInitialSetupCompleted(false);
                        Logger.i(Logger.TAG_API, "Initial setup marked as incomplete");
                        navigateAfterLogin();
                        return;
                    }
                    
                    // User exists in DB - check profile and proceed
                    Logger.i(Logger.TAG_API, "User EXISTS in DB - checking profile and proceeding");
                    // Ensure username is set
                    preferencesManager.setUserName(username);
                    Logger.i(Logger.TAG_API, "Username saved to preferences: " + username);
                    checkUserProfileAndNavigate(username);
                } else {
                    // Error checking user existence - assume user doesn't exist and go to initial setup
                    Logger.w(Logger.TAG_API, "Failed to check user existence - response not successful or body is null");
                    Logger.w(Logger.TAG_API, "Assuming new user and navigating to initial setup");
                    // Ensure username is set
                    preferencesManager.setUserName(username);
                    Logger.i(Logger.TAG_API, "Username saved to preferences: " + username);
                    preferencesManager.setInitialSetupCompleted(false);
                    Logger.i(Logger.TAG_API, "Initial setup marked as incomplete");
                    navigateAfterLogin();
                }
                Logger.i(Logger.TAG_API, "=== CHECK USER EXISTS RESPONSE HANDLING COMPLETE ===");
            }

            @Override
            public void onFailure(Call<ApiService.UserExistsResponse> call, Throwable t) {
                Logger.e(Logger.TAG_API, "=== CHECK USER EXISTS API CALL FAILURE ===");
                Logger.e(Logger.TAG_API, "Network/API call failed - Exception type: " + t.getClass().getSimpleName());
                Logger.e(Logger.TAG_API, "Exception message: " + t.getMessage());
                Logger.e(Logger.TAG_API, "Stack trace:", t);
                
                // Ensure username is set even on failure
                preferencesManager.setUserName(username);
                Logger.e(Logger.TAG_API, "Username saved to preferences (on failure): " + username);
                Logger.e(Logger.TAG_API, "Failed to check user existence in DB, assuming new user");
                // Network error - assume user doesn't exist in DB and go to initial setup
                preferencesManager.setInitialSetupCompleted(false);
                Logger.e(Logger.TAG_API, "Initial setup marked as incomplete (on failure)");
                navigateAfterLogin();
                Logger.e(Logger.TAG_API, "=== CHECK USER EXISTS FAILURE HANDLING COMPLETE ===");
            }
        });
        
        Logger.i(Logger.TAG_API, "checkUserExists API call enqueued, waiting for response...");
    }

    private void checkUserProfileAndNavigate(String username) {
        Logger.i(Logger.TAG_API, "=== CHECK USER PROFILE AND NAVIGATE START ===");
        Logger.i(Logger.TAG_API, "checkUserProfileAndNavigate() called with username=" + username);
        
        ApiService apiService = ApiClient.getInstance(this).getApiService();
        Logger.i(Logger.TAG_API, "ApiService obtained for getUserProfile");
        Logger.i(Logger.TAG_API, "Calling getUserProfile API...");
        
        apiService.getUserProfile(username).enqueue(new Callback<ApiService.UserProfileResponse>() {
            @Override
            public void onResponse(Call<ApiService.UserProfileResponse> call, Response<ApiService.UserProfileResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiService.UserProfileResponse profile = response.body();
                    // User exists - check if profile is complete
                    boolean isComplete = profile.firstName != null && !profile.firstName.trim().isEmpty()
                            && profile.lastName != null && !profile.lastName.trim().isEmpty()
                            && profile.degree != null && !profile.degree.trim().isEmpty()
                            && profile.participationPreference != null && !profile.participationPreference.trim().isEmpty();
                    
                    if (isComplete) {
                        // Profile is complete - sync to preferences
                        // Ensure username is set (should already be set from auth, but ensure it's here too)
                        preferencesManager.setUserName(username);
                        preferencesManager.setFirstName(profile.firstName);
                        preferencesManager.setLastName(profile.lastName);
                        preferencesManager.setEmail(profile.email);
                        preferencesManager.setDegree(profile.degree);
                        preferencesManager.setParticipationPreference(profile.participationPreference);
                        preferencesManager.setInitialSetupCompleted(true);
                        Logger.i(Logger.TAG_API, "Profile data synced to preferences - firstName: " + profile.firstName + 
                            ", lastName: " + profile.lastName + 
                            ", degree: " + profile.degree + 
                            ", participationPreference: " + profile.participationPreference);
                        
                        if (profile.participationPreference != null) {
                            if (profile.participationPreference.contains("PRESENTER")) {
                                preferencesManager.setUserRole("PRESENTER");
                                Logger.i(Logger.TAG_API, "User role set to PRESENTER");
                            } else if (profile.participationPreference.contains("PARTICIPANT")) {
                                preferencesManager.setUserRole("PARTICIPANT");
                                Logger.i(Logger.TAG_API, "User role set to PARTICIPANT");
                            } else {
                                Logger.i(Logger.TAG_API, "User role not determined from participationPreference: " + profile.participationPreference);
                            }
                        } else {
                            Logger.i(Logger.TAG_API, "participationPreference is null, skipping role determination");
                        }
                        navigateAfterLogin();
                    } else {
                        Logger.i(Logger.TAG_API, "Profile is INCOMPLETE - navigating to initial setup");
                        // Profile exists but incomplete - show initial setup
                        // Ensure username is set
                        preferencesManager.setUserName(username);
                        preferencesManager.setInitialSetupCompleted(false);
                        Logger.i(Logger.TAG_API, "Initial setup marked as incomplete");
                        navigateAfterLogin();
                    }
                } else {
                    Logger.w(Logger.TAG_API, "User profile not found or response failed - code=" + response.code() + ", body is null: " + (response.body() == null));
                    // User doesn't exist - show initial setup (will create user there)
                    // Ensure username is set
                    preferencesManager.setUserName(username);
                    Logger.w(Logger.TAG_API, "Assuming new user, navigating to initial setup");
                    preferencesManager.setInitialSetupCompleted(false);
                    navigateAfterLogin();
                }
                Logger.i(Logger.TAG_API, "=== GET USER PROFILE RESPONSE HANDLING COMPLETE ===");
            }

            @Override
            public void onFailure(Call<ApiService.UserProfileResponse> call, Throwable t) {
                Logger.e(Logger.TAG_API, "=== GET USER PROFILE API CALL FAILURE ===");
                Logger.e(Logger.TAG_API, "Network/API call failed - Exception type: " + t.getClass().getSimpleName());
                Logger.e(Logger.TAG_API, "Exception message: " + t.getMessage());
                Logger.e(Logger.TAG_API, "Stack trace:", t);
                
                // Ensure username is set even on failure
                preferencesManager.setUserName(username);
                Logger.e(Logger.TAG_API, "Username saved to preferences (on failure): " + username);
                Logger.e(Logger.TAG_API, "Failed to fetch user profile, showing initial setup");
                preferencesManager.setInitialSetupCompleted(false);
                Logger.e(Logger.TAG_API, "Initial setup marked as incomplete (on failure)");
                navigateAfterLogin();
                Logger.e(Logger.TAG_API, "=== GET USER PROFILE FAILURE HANDLING COMPLETE ===");
            }
        });
        
        Logger.i(Logger.TAG_API, "getUserProfile API call enqueued, waiting for response...");
    }

    private void navigateAfterLogin() {
        Logger.i(Logger.TAG_UI, "=== NAVIGATE AFTER LOGIN START ===");
        Logger.i(Logger.TAG_UI, "navigateAfterLogin() called");
        Logger.i(Logger.TAG_UI, "hasCompletedInitialSetup: " + preferencesManager.hasCompletedInitialSetup());
        
        // Fetch fresh configuration from backend on every login
        // This runs in background and doesn't block navigation
        // Wrap in try-catch to prevent crashes if ConfigManager initialization fails
        Logger.i(Logger.TAG_UI, "Attempting to refresh config on login...");
        try {
            ConfigManager.getInstance(this).refreshConfig();
            Logger.i(Logger.TAG_UI, "Config refresh initiated successfully (runs in background)");
            try {
                ServerLogger serverLogger = ServerLogger.getInstance(this);
                if (serverLogger != null) {
                    serverLogger.i(ServerLogger.TAG_UI, "Config refresh initiated on login");
                }
            } catch (Exception logEx) {
                Logger.e(Logger.TAG_UI, "Failed to log to ServerLogger: " + logEx.getMessage());
            }
        } catch (Exception e) {
            Logger.e(Logger.TAG_UI, "Failed to refresh config on login: " + e.getMessage() + ", Exception: " + e.getClass().getSimpleName(), e);
            try {
                ServerLogger serverLogger = ServerLogger.getInstance(this);
                if (serverLogger != null) {
                    serverLogger.e(ServerLogger.TAG_UI, "Failed to refresh config on login: " + e.getMessage() + ", Exception: " + e.getClass().getSimpleName(), e);
                }
            } catch (Exception logEx) {
                Logger.e(Logger.TAG_UI, "Failed to log to ServerLogger: " + logEx.getMessage());
            }
            // Continue with navigation even if config refresh fails
            Logger.e(Logger.TAG_UI, "Continuing with navigation despite config refresh failure");
        }
        
        // Check for announcements before navigating
        checkAndShowAnnouncement(() -> {
            Intent intent;
            if (preferencesManager.hasCompletedInitialSetup()) {
                Logger.i(Logger.TAG_UI, "User has completed initial setup - navigating to RolePickerActivity");
                intent = new Intent(this, RolePickerActivity.class);
            } else {
                Logger.i(Logger.TAG_UI, "User has NOT completed initial setup - navigating to FirstTimeSetupActivity");
                intent = new Intent(this, FirstTimeSetupActivity.class);
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            Logger.i(Logger.TAG_UI, "Starting activity: " + intent.getComponent().getClassName());
            startActivity(intent);
            Logger.i(Logger.TAG_UI, "Activity started, finishing LoginActivity");
            finish();
            Logger.i(Logger.TAG_UI, "=== NAVIGATE AFTER LOGIN COMPLETE ===");
            Logger.i(Logger.TAG_UI, "=== LOGIN FLOW END ===");
        });
    }

    /**
     * Check for announcements and show dialog if there's an active one with a new version.
     * @param onComplete Callback to run after announcement is dismissed or if no announcement
     */
    private void checkAndShowAnnouncement(Runnable onComplete) {
        Logger.i(Logger.TAG_UI, "Checking for announcements...");

        ApiService apiService = ApiClient.getInstance(this).getApiService();
        apiService.getAnnouncement().enqueue(new Callback<ApiService.AnnouncementResponse>() {
            @Override
            public void onResponse(Call<ApiService.AnnouncementResponse> call, Response<ApiService.AnnouncementResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiService.AnnouncementResponse announcement = response.body();
                    int lastSeenVersion = preferencesManager.getLastSeenAnnouncementVersion();

                    Logger.i(Logger.TAG_UI, "Announcement: isActive=" + announcement.isActive +
                            ", version=" + announcement.version + ", lastSeen=" + lastSeenVersion);

                    if (announcement.isActive && announcement.version > lastSeenVersion) {
                        // Show announcement dialog
                        runOnUiThread(() -> showAnnouncementDialog(announcement, onComplete));
                    } else {
                        // No new announcement, proceed
                        runOnUiThread(onComplete);
                    }
                } else {
                    Logger.w(Logger.TAG_UI, "Failed to fetch announcement: " + response.code());
                    runOnUiThread(onComplete);
                }
            }

            @Override
            public void onFailure(Call<ApiService.AnnouncementResponse> call, Throwable t) {
                Logger.e(Logger.TAG_UI, "Error fetching announcement", t);
                runOnUiThread(onComplete);
            }
        });
    }

    /**
     * Show the announcement dialog
     */
    private void showAnnouncementDialog(ApiService.AnnouncementResponse announcement, Runnable onComplete) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(announcement.title != null && !announcement.title.isEmpty() ? announcement.title : "Announcement")
                .setMessage(announcement.message)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    // Mark as seen
                    preferencesManager.setLastSeenAnnouncementVersion(announcement.version);
                    dialog.dismiss();
                    onComplete.run();
                });

        // If not blocking, allow dismissing by clicking outside
        AlertDialog dialog = builder.create();
        dialog.setCancelable(!announcement.isBlocking);
        dialog.setCanceledOnTouchOutside(!announcement.isBlocking);

        if (!announcement.isBlocking) {
            dialog.setOnCancelListener(d -> {
                preferencesManager.setLastSeenAnnouncementVersion(announcement.version);
                onComplete.run();
            });
        }

        dialog.show();
    }

    private void handleSkipAuth() {
        String normalized = normalizeInput(editUsername != null ? editUsername.getText() : null);
        if (TextUtils.isEmpty(normalized)) {
            normalized = DEFAULT_SKIP_USERNAME;
        }

        preferencesManager.clearUserData();
        preferencesManager.setUserName(normalized);
        preferencesManager.setUserRole("PRESENTER");
        preferencesManager.setFirstName("Skip");
        preferencesManager.setLastName("Tester");
        
        // Get email domain from ConfigManager with error handling
        String emailDomain;
        try {
            emailDomain = ConfigManager.getInstance(this).getEmailDomain();
        } catch (Exception e) {
            Logger.e(Logger.TAG_UI, "Failed to get email domain from ConfigManager: " + e.getMessage(), e);
            ServerLogger serverLogger = ServerLogger.getInstance(this);
            if (serverLogger != null) {
                serverLogger.e(ServerLogger.TAG_UI, "Failed to get email domain from ConfigManager: " + e.getMessage(), e);
            }
            emailDomain = "@bgu.ac.il"; // Fallback to hardcoded default
        }
        String email = normalized + emailDomain;
        preferencesManager.setEmail(email);
        preferencesManager.setDegree(FirstTimeSetupActivity.DEGREE_PHD);
        preferencesManager.setParticipationPreference(FirstTimeSetupActivity.PARTICIPATION_PRESENTER_ONLY);
        preferencesManager.setInitialSetupCompleted(true);

        ApiService apiService = ApiClient.getInstance(this).getApiService();
        ApiService.UserProfileUpdateRequest request = new ApiService.UserProfileUpdateRequest(
                normalized,
                email,
                "Skip",
                "Tester",
                FirstTimeSetupActivity.DEGREE_PHD,
                FirstTimeSetupActivity.PARTICIPATION_PRESENTER_ONLY,
                null // nationalIdNumber - not provided in skip auth flow
        );
        apiService.upsertUser(request).enqueue(new Callback<ApiService.UserProfileResponse>() {
            @Override
            public void onResponse(Call<ApiService.UserProfileResponse> call, Response<ApiService.UserProfileResponse> response) {
                if (!response.isSuccessful()) {
                    Logger.w(Logger.TAG_API, "Skip auth profile upsert failed, code=" + response.code());
                }
            }

            @Override
            public void onFailure(Call<ApiService.UserProfileResponse> call, Throwable t) {
                Logger.e(Logger.TAG_API, "Skip auth profile upsert error", t);
            }
        });

        ServerLogger.getInstance(this).updateUserContext(normalized, "PRESENTER");

        Logger.userAction("SkipAuth", "Skip auth used with username=" + normalized);

        Toast.makeText(this, getString(R.string.login_skip_auth_success, normalized), Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, PresenterHomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private String normalizeInput(@Nullable CharSequence raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.toString().trim();
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        return value.toLowerCase(java.util.Locale.US).replaceAll("\\s+", "");
    }
    
    /**
     * Load saved credentials if "Remember Me" was previously checked.
     * Username is stored in regular SharedPreferences.
     * Password is stored securely using EncryptedSharedPreferences.
     */
    private void loadSavedCredentials() {
        String savedUsername = preferencesManager.getSavedUsername();
        String savedPassword = preferencesManager.getSavedPassword();
        boolean rememberMeChecked = preferencesManager.isRememberMeEnabled();

        // Always set checkbox based on preference (defaults to true)
        if (checkboxRememberMe != null) {
            checkboxRememberMe.setChecked(rememberMeChecked);
        }

        // Pre-fill credentials if Remember Me was enabled and we have saved data
        if (rememberMeChecked && savedUsername != null && !savedUsername.isEmpty()) {
            // Pre-fill username
            if (editUsername != null) {
                editUsername.setText(savedUsername);
            }

            // Pre-fill password if available (stored encrypted)
            if (savedPassword != null && !savedPassword.isEmpty() && editPassword != null) {
                editPassword.setText(savedPassword);
            }

            Logger.i(Logger.TAG_UI, "Loaded saved credentials for username: " + savedUsername);
        }
    }

    /**
     * Save credentials when "Remember Me" is checked.
     * Username is stored in regular SharedPreferences.
     * Password is stored securely using EncryptedSharedPreferences (AES256-GCM encryption).
     */
    private void saveCredentials(String username, String password) {
        preferencesManager.setSavedUsername(username);
        preferencesManager.setSavedPassword(password);
        preferencesManager.setRememberMeEnabled(true);
        Logger.i(Logger.TAG_UI, "Saved credentials securely for username: " + username);
    }
    
    /**
     * Clear saved credentials when "Remember Me" is unchecked
     */
    private void clearSavedCredentials() {
        preferencesManager.clearSavedCredentials();
        Logger.i(Logger.TAG_UI, "Cleared saved credentials");
    }
}


