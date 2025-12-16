package org.example.semscan.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.Nullable;
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

        // Disable login button to prevent multiple clicks
        btnLogin.setEnabled(false);
        btnLogin.setText(getString(R.string.login_button_loading));

        // FIRST: Authenticate user via BGU external authentication
        performLogin(username, password);
    }

    private void performLogin(String normalized, String password) {
        // FIRST: Authenticate user via BGU external authentication API
        ApiService apiService = ApiClient.getInstance(this).getApiService();
        ApiService.LoginRequest loginRequest = new ApiService.LoginRequest(normalized, password != null ? password : "");
        apiService.login(loginRequest).enqueue(new Callback<ApiService.LoginResponse>() {
            @Override
            public void onResponse(Call<ApiService.LoginResponse> call, Response<ApiService.LoginResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiService.LoginResponse loginResponse = response.body();
                    
                    // Check if authentication was successful
                    if (!loginResponse.ok) {
                        // Authentication failed - show error and stop
                        btnLogin.setEnabled(true);
                        btnLogin.setText(getString(R.string.login_button));
                        String errorMsg = loginResponse.message != null ? loginResponse.message : "Invalid username or password";
                        Logger.w(Logger.TAG_API, "Authentication failed: " + errorMsg);
                        Toast.makeText(LoginActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                        return;
                    }
                    
                    // Authentication successful - now check if user exists in users database
                    String username = loginResponse.bguUsername != null ? loginResponse.bguUsername : normalized;
                    preferencesManager.setUserName(username);
                    
                    if (loginResponse.email != null) {
                        preferencesManager.setEmail(loginResponse.email);
                    }
                    
                    // Save credentials if "Remember Me" is checked
                    if (checkboxRememberMe != null && checkboxRememberMe.isChecked()) {
                        saveCredentials(normalized, password);
                    } else {
                        // Clear saved credentials if "Remember Me" is unchecked
                        clearSavedCredentials();
                    }
                    
                    ServerLogger.getInstance(LoginActivity.this).updateUserContext(
                            username, 
                            loginResponse.isPresenter ? "PRESENTER" : (loginResponse.isParticipant ? "PARTICIPANT" : "UNKNOWN")
                    );
                    
                    // SECOND: Check if authenticated user exists in users database
                    checkUserExistsInDatabase(username);
                } else {
                    // Authentication failed - show error and stop
                    btnLogin.setEnabled(true);
                    btnLogin.setText(getString(R.string.login_button));
                    String errorMsg = "Invalid username or password";
                    try {
                        if (response.errorBody() != null) {
                            String errorBody = response.errorBody().string();
                            if (errorBody.length() > 0) {
                                errorMsg = errorBody.length() > 100 ? errorBody.substring(0, 100) : errorBody;
                            }
                        }
                    } catch (Exception e) {
                        // Ignore
                    }
                    Logger.w(Logger.TAG_API, "Authentication failed, code=" + response.code() + ", message=" + errorMsg);
                    Toast.makeText(LoginActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ApiService.LoginResponse> call, Throwable t) {
                btnLogin.setEnabled(true);
                btnLogin.setText(getString(R.string.login_button));
                Logger.e(Logger.TAG_API, "Authentication API call error", t);
                
                // Log network error to ServerLogger with full exception details
                // This will be queued and sent when network is available
                ServerLogger serverLogger = ServerLogger.getInstance(LoginActivity.this);
                String requestUrl = call.request() != null ? call.request().url().toString() : "unknown";
                String errorDetails = String.format("Login network failure - URL: %s, Exception: %s, Message: %s",
                    requestUrl, t.getClass().getSimpleName(), t.getMessage());
                serverLogger.e(ServerLogger.TAG_API, errorDetails, t);
                
                // Show user-friendly error message based on exception type
                String errorMessage = getString(R.string.error_network_connection);
                if (t instanceof java.net.SocketTimeoutException || t instanceof java.net.ConnectException) {
                    errorMessage = getString(R.string.error_network_timeout);
                } else if (t instanceof java.net.UnknownHostException) {
                    errorMessage = getString(R.string.error_server_unavailable);
                }
                
                Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void checkUserExistsInDatabase(String username) {
        // Check if authenticated user exists in users database
        ApiService apiService = ApiClient.getInstance(this).getApiService();
        ApiService.UserExistsRequest existsRequest = new ApiService.UserExistsRequest(username);
        apiService.checkUserExists(existsRequest).enqueue(new Callback<ApiService.UserExistsResponse>() {
            @Override
            public void onResponse(Call<ApiService.UserExistsResponse> call, Response<ApiService.UserExistsResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiService.UserExistsResponse existsResponse = response.body();
                    if (!existsResponse.exists) {
                        // User authenticated but doesn't exist in DB - go to initial setup
                        // Ensure username is set
                        preferencesManager.setUserName(username);
                        Logger.i(Logger.TAG_API, "User authenticated but not in DB, navigating to initial setup: " + username);
                        preferencesManager.setInitialSetupCompleted(false);
                        navigateAfterLogin();
                        return;
                    }
                    
                    // User exists in DB - check profile and proceed
                    // Ensure username is set
                    preferencesManager.setUserName(username);
                    Logger.i(Logger.TAG_API, "User authenticated and exists in DB: " + username);
                    checkUserProfileAndNavigate(username);
                } else {
                    // Error checking user existence - assume user doesn't exist and go to initial setup
                    // Ensure username is set
                    preferencesManager.setUserName(username);
                    Logger.w(Logger.TAG_API, "Failed to check user existence in DB, code=" + response.code() + ", assuming new user");
                    preferencesManager.setInitialSetupCompleted(false);
                    navigateAfterLogin();
                }
            }

            @Override
            public void onFailure(Call<ApiService.UserExistsResponse> call, Throwable t) {
                // Ensure username is set even on failure
                preferencesManager.setUserName(username);
                Logger.e(Logger.TAG_API, "Failed to check user existence in DB, assuming new user", t);
                // Network error - assume user doesn't exist in DB and go to initial setup
                preferencesManager.setInitialSetupCompleted(false);
                navigateAfterLogin();
            }
        });
    }

    private void checkUserProfileAndNavigate(String username) {
        ApiService apiService = ApiClient.getInstance(this).getApiService();
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
                        
                        if (profile.participationPreference != null) {
                            if (profile.participationPreference.contains("PRESENTER")) {
                                preferencesManager.setUserRole("PRESENTER");
                            } else if (profile.participationPreference.contains("PARTICIPANT")) {
                                preferencesManager.setUserRole("PARTICIPANT");
                            }
                        }
                        navigateAfterLogin();
                    } else {
                        // Profile exists but incomplete - show initial setup
                        // Ensure username is set
                        preferencesManager.setUserName(username);
                        preferencesManager.setInitialSetupCompleted(false);
                        navigateAfterLogin();
                    }
                } else {
                    // User doesn't exist - show initial setup (will create user there)
                    // Ensure username is set
                    preferencesManager.setUserName(username);
                    Logger.w(Logger.TAG_API, "User profile not found, code=" + response.code());
                    preferencesManager.setInitialSetupCompleted(false);
                    navigateAfterLogin();
                }
            }

            @Override
            public void onFailure(Call<ApiService.UserProfileResponse> call, Throwable t) {
                // Ensure username is set even on failure
                preferencesManager.setUserName(username);
                Logger.e(Logger.TAG_API, "Failed to fetch user profile, showing initial setup", t);
                preferencesManager.setInitialSetupCompleted(false);
                navigateAfterLogin();
            }
        });
    }

    private void navigateAfterLogin() {
        Intent intent;
        if (preferencesManager.hasCompletedInitialSetup()) {
            intent = new Intent(this, RolePickerActivity.class);
        } else {
            intent = new Intent(this, FirstTimeSetupActivity.class);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
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
        preferencesManager.setEmail(normalized + "@bgu.ac.il");
        preferencesManager.setDegree(FirstTimeSetupActivity.DEGREE_PHD);
        preferencesManager.setParticipationPreference(FirstTimeSetupActivity.PARTICIPATION_PRESENTER_ONLY);
        preferencesManager.setInitialSetupCompleted(true);

        ApiService apiService = ApiClient.getInstance(this).getApiService();
        ApiService.UserProfileUpdateRequest request = new ApiService.UserProfileUpdateRequest(
                normalized,
                normalized + "@bgu.ac.il",
                "Skip",
                "Tester",
                FirstTimeSetupActivity.DEGREE_PHD,
                FirstTimeSetupActivity.PARTICIPATION_PRESENTER_ONLY,
                null // nationalIdNumber - not provided in skip auth flow
        );
        apiService.upsertUser(request).enqueue(new Callback<User>() {
            @Override
            public void onResponse(Call<User> call, Response<User> response) {
                if (!response.isSuccessful()) {
                    Logger.w(Logger.TAG_API, "Skip auth profile upsert failed, code=" + response.code());
                }
            }

            @Override
            public void onFailure(Call<User> call, Throwable t) {
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
     * Load saved credentials if "Remember Me" was previously checked
     */
    private void loadSavedCredentials() {
        String savedUsername = preferencesManager.getSavedUsername();
        String savedPassword = preferencesManager.getSavedPassword();
        boolean rememberMeChecked = preferencesManager.isRememberMeEnabled();
        
        if (rememberMeChecked && savedUsername != null && !savedUsername.isEmpty()) {
            // Pre-fill username
            if (editUsername != null) {
                editUsername.setText(savedUsername);
            }
            
            // Pre-fill password if available (optional - for security, you might want to skip this)
            if (savedPassword != null && !savedPassword.isEmpty() && editPassword != null) {
                editPassword.setText(savedPassword);
            }
            
            // Check the "Remember Me" checkbox
            if (checkboxRememberMe != null) {
                checkboxRememberMe.setChecked(true);
            }
            
            Logger.i(Logger.TAG_UI, "Loaded saved credentials for: " + savedUsername);
        }
    }
    
    /**
     * Save credentials when "Remember Me" is checked
     * Note: Storing passwords in SharedPreferences is not secure for production apps.
     * Consider using Android Keystore or token-based authentication instead.
     */
    private void saveCredentials(String username, String password) {
        preferencesManager.setSavedUsername(username);
        preferencesManager.setSavedPassword(password);
        preferencesManager.setRememberMeEnabled(true);
        Logger.i(Logger.TAG_UI, "Saved credentials for: " + username);
    }
    
    /**
     * Clear saved credentials when "Remember Me" is unchecked
     */
    private void clearSavedCredentials() {
        preferencesManager.clearSavedCredentials();
        Logger.i(Logger.TAG_UI, "Cleared saved credentials");
    }
}


