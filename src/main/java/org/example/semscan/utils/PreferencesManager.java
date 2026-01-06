package org.example.semscan.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Locale;

public class PreferencesManager {
    private static final String PREFS_NAME = "semscan_prefs";
    private static final String ENCRYPTED_PREFS_NAME = "semscan_secure_prefs";
    private static final String KEY_USER_ROLE = "user_role";
    private static final String KEY_USERNAME = "bgu_username";
    private static final String KEY_FIRST_NAME = "first_name";
    private static final String KEY_LAST_NAME = "last_name";
    private static final String KEY_EMAIL = "user_email";
    private static final String KEY_DEGREE = "user_degree";
    private static final String KEY_PARTICIPATION = "participation_preference";
    private static final String KEY_SETUP_COMPLETED = "initial_setup_completed";
    private static final String KEY_NATIONAL_ID = "national_id";
    private static final String KEY_SUPERVISOR_NAME = "supervisor_name";
    private static final String KEY_SUPERVISOR_EMAIL = "supervisor_email";
    private static final String KEY_SEMINAR_ABSTRACT = "seminar_abstract";
    private static final String KEY_PRESENTATION_TOPIC = "presentation_topic";
    private static final String KEY_LAST_SEEN_ANNOUNCEMENT_VERSION = "last_seen_announcement_version";
    private static final String KEY_FCM_TOKEN = "fcm_token";

    // Remember Me credentials
    private static final String KEY_SAVED_USERNAME = "saved_username";
    private static final String KEY_SAVED_PASSWORD = "saved_password"; // Stored encrypted
    private static final String KEY_REMEMBER_ME = "remember_me_enabled";

    private static PreferencesManager instance;
    private SharedPreferences prefs;
    private SharedPreferences encryptedPrefs;
    private Context appContext;

    private PreferencesManager(Context context) {
        this.appContext = context.getApplicationContext();
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        initEncryptedPrefs(context);
    }

    private void initEncryptedPrefs(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            encryptedPrefs = EncryptedSharedPreferences.create(
                    context,
                    ENCRYPTED_PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            Logger.i(Logger.TAG_PREFERENCES, "EncryptedSharedPreferences initialized successfully");
        } catch (GeneralSecurityException | IOException e) {
            Logger.e(Logger.TAG_PREFERENCES, "Failed to initialize EncryptedSharedPreferences", e);
            // Fallback: don't store password if encryption fails
            encryptedPrefs = null;
        }
    }

    public static synchronized PreferencesManager getInstance(Context context) {
        if (instance == null) {
            instance = new PreferencesManager(context.getApplicationContext());
        }
        return instance;
    }
    
    // User Role
    public void setUserRole(String role) {
        Logger.prefs(KEY_USER_ROLE, role);
        prefs.edit().putString(KEY_USER_ROLE, role).apply();
    }
    
    public String getUserRole() {
        return prefs.getString(KEY_USER_ROLE, null);
    }
    
    public boolean isPresenter() {
        return "PRESENTER".equals(getUserRole());
    }
    
    public boolean isParticipant() {
        return "PARTICIPANT".equals(getUserRole());
    }
    
    public boolean hasRole() {
        return getUserRole() != null;
    }
    
    // User Name
    public void setUserName(String userName) {
        String normalized = userName;
        if (normalized != null) {
            normalized = normalized.trim().toLowerCase(Locale.US);
        }
        Logger.prefs(KEY_USERNAME, normalized);
        if (normalized == null || normalized.isEmpty()) {
            prefs.edit().remove(KEY_USERNAME).apply();
        } else {
            prefs.edit().putString(KEY_USERNAME, normalized).apply();
        }
    }
    
    public String getUserName() {
        return prefs.getString(KEY_USERNAME, null);
    }

    public void setFirstName(String firstName) {
        Logger.prefs(KEY_FIRST_NAME, firstName);
        prefs.edit().putString(KEY_FIRST_NAME, firstName).apply();
    }

    public String getFirstName() {
        return prefs.getString(KEY_FIRST_NAME, null);
    }

    public void setLastName(String lastName) {
        Logger.prefs(KEY_LAST_NAME, lastName);
        prefs.edit().putString(KEY_LAST_NAME, lastName).apply();
    }

    public String getLastName() {
        return prefs.getString(KEY_LAST_NAME, null);
    }

    public void setEmail(String email) {
        Logger.prefs(KEY_EMAIL, email);
        prefs.edit().putString(KEY_EMAIL, email).apply();
    }

    public String getEmail() {
        return prefs.getString(KEY_EMAIL, null);
    }

    public void setDegree(String degree) {
        Logger.prefs(KEY_DEGREE, degree);
        prefs.edit().putString(KEY_DEGREE, degree).apply();
    }

    public String getDegree() {
        return prefs.getString(KEY_DEGREE, null);
    }

    public void setParticipationPreference(String preference) {
        Logger.prefs(KEY_PARTICIPATION, preference);
        prefs.edit().putString(KEY_PARTICIPATION, preference).apply();
    }

    public String getParticipationPreference() {
        return prefs.getString(KEY_PARTICIPATION, null);
    }

    public void setInitialSetupCompleted(boolean completed) {
        Logger.prefs(KEY_SETUP_COMPLETED, String.valueOf(completed));
        prefs.edit().putBoolean(KEY_SETUP_COMPLETED, completed).apply();
    }

    public boolean hasCompletedInitialSetup() {
        return prefs.getBoolean(KEY_SETUP_COMPLETED, false);
    }

    // National ID
    public void setNationalId(String nationalId) {
        Logger.prefs(KEY_NATIONAL_ID, nationalId);
        prefs.edit().putString(KEY_NATIONAL_ID, nationalId).apply();
    }

    public String getNationalId() {
        return prefs.getString(KEY_NATIONAL_ID, null);
    }

    // Supervisor Name
    public void setSupervisorName(String supervisorName) {
        Logger.prefs(KEY_SUPERVISOR_NAME, supervisorName);
        prefs.edit().putString(KEY_SUPERVISOR_NAME, supervisorName).apply();
    }

    public String getSupervisorName() {
        return prefs.getString(KEY_SUPERVISOR_NAME, null);
    }

    // Supervisor Email
    public void setSupervisorEmail(String supervisorEmail) {
        Logger.prefs(KEY_SUPERVISOR_EMAIL, supervisorEmail);
        prefs.edit().putString(KEY_SUPERVISOR_EMAIL, supervisorEmail).apply();
    }

    public String getSupervisorEmail() {
        return prefs.getString(KEY_SUPERVISOR_EMAIL, null);
    }

    // Seminar Abstract
    public void setSeminarAbstract(String seminarAbstract) {
        Logger.prefs(KEY_SEMINAR_ABSTRACT, seminarAbstract);
        prefs.edit().putString(KEY_SEMINAR_ABSTRACT, seminarAbstract).apply();
    }

    public String getSeminarAbstract() {
        return prefs.getString(KEY_SEMINAR_ABSTRACT, null);
    }

    // Presentation Topic
    public void setPresentationTopic(String topic) {
        Logger.prefs(KEY_PRESENTATION_TOPIC, topic);
        prefs.edit().putString(KEY_PRESENTATION_TOPIC, topic).apply();
    }

    public String getPresentationTopic() {
        return prefs.getString(KEY_PRESENTATION_TOPIC, null);
    }

    // Announcement Version Tracking
    public void setLastSeenAnnouncementVersion(int version) {
        Logger.prefs(KEY_LAST_SEEN_ANNOUNCEMENT_VERSION, String.valueOf(version));
        prefs.edit().putInt(KEY_LAST_SEEN_ANNOUNCEMENT_VERSION, version).apply();
    }

    public int getLastSeenAnnouncementVersion() {
        return prefs.getInt(KEY_LAST_SEEN_ANNOUNCEMENT_VERSION, 0);
    }

    // FCM Token
    public void setFcmToken(String token) {
        Logger.prefs(KEY_FCM_TOKEN, token != null ? token.substring(0, Math.min(10, token.length())) + "..." : null);
        if (token == null || token.isEmpty()) {
            prefs.edit().remove(KEY_FCM_TOKEN).apply();
        } else {
            prefs.edit().putString(KEY_FCM_TOKEN, token).apply();
        }
    }

    public String getFcmToken() {
        return prefs.getString(KEY_FCM_TOKEN, null);
    }

    // Alias for consistency with other code
    public String getBguUsername() {
        return getUserName();
    }

    // Clear all preferences
    public void clearAll() {
        prefs.edit().clear().apply();
    }
    
    // Clear user data but keep settings
    public void clearUserData() {
        prefs.edit()
                .remove(KEY_USER_ROLE)
                .remove(KEY_USERNAME)
                .remove(KEY_FIRST_NAME)
                .remove(KEY_LAST_NAME)
                .remove(KEY_EMAIL)
                .remove(KEY_DEGREE)
                .remove(KEY_PARTICIPATION)
                .remove(KEY_SETUP_COMPLETED)
                .remove(KEY_LAST_SEEN_ANNOUNCEMENT_VERSION)  // Clear so new user sees announcements
                .apply();
    }
    
    // Remember Me functionality
    public void setSavedUsername(String username) {
        Logger.prefs(KEY_SAVED_USERNAME, username);
        if (username == null || username.isEmpty()) {
            prefs.edit().remove(KEY_SAVED_USERNAME).apply();
        } else {
            prefs.edit().putString(KEY_SAVED_USERNAME, username).apply();
        }
    }
    
    public String getSavedUsername() {
        return prefs.getString(KEY_SAVED_USERNAME, null);
    }
    
    public void setRememberMeEnabled(boolean enabled) {
        Logger.prefs(KEY_REMEMBER_ME, String.valueOf(enabled));
        prefs.edit().putBoolean(KEY_REMEMBER_ME, enabled).apply();
    }
    
    public boolean isRememberMeEnabled() {
        return prefs.getBoolean(KEY_REMEMBER_ME, true);  // Default to true - Remember Me is ON by default
    }

    /**
     * Save password securely using EncryptedSharedPreferences
     */
    public void setSavedPassword(String password) {
        if (encryptedPrefs == null) {
            Logger.w(Logger.TAG_PREFERENCES, "Cannot save password: EncryptedSharedPreferences not available");
            logToServer("Cannot save password: EncryptedSharedPreferences not available");
            return;
        }
        if (password == null || password.isEmpty()) {
            encryptedPrefs.edit().remove(KEY_SAVED_PASSWORD).apply();
            Logger.i(Logger.TAG_PREFERENCES, "Cleared saved password");
            logToServer("Cleared saved password (encrypted storage)");
        } else {
            encryptedPrefs.edit().putString(KEY_SAVED_PASSWORD, password).apply();
            Logger.i(Logger.TAG_PREFERENCES, "Password saved securely");
            logToServer("Password saved securely (AES256-GCM encrypted)");
        }
    }

    /**
     * Get saved password from EncryptedSharedPreferences
     */
    public String getSavedPassword() {
        if (encryptedPrefs == null) {
            Logger.w(Logger.TAG_PREFERENCES, "Cannot retrieve password: EncryptedSharedPreferences not available");
            return null;
        }
        String password = encryptedPrefs.getString(KEY_SAVED_PASSWORD, null);
        if (password != null && !password.isEmpty()) {
            logToServer("Password loaded from encrypted storage");
        }
        return password;
    }

    /**
     * Clear saved username, password and remember me setting
     */
    public void clearSavedCredentials() {
        prefs.edit()
                .remove(KEY_SAVED_USERNAME)
                .remove(KEY_REMEMBER_ME)
                .apply();
        // Also clear encrypted password
        if (encryptedPrefs != null) {
            encryptedPrefs.edit().remove(KEY_SAVED_PASSWORD).apply();
        }
        Logger.i(Logger.TAG_PREFERENCES, "Cleared saved credentials");
        logToServer("Cleared saved credentials (username, password, remember me)");
    }

    /**
     * Helper method to log security events to ServerLogger
     */
    private void logToServer(String message) {
        try {
            ServerLogger serverLogger = ServerLogger.getInstance(appContext);
            if (serverLogger != null) {
                serverLogger.security("CredentialStorage", message);
            }
        } catch (Exception e) {
            // Ignore - avoid circular dependency or initialization issues
        }
    }
}
