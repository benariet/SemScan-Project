package org.example.semscan.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import org.example.semscan.constants.ApiConstants;
import org.example.semscan.data.api.ApiClient;
import org.example.semscan.data.api.ApiService;
import org.example.semscan.utils.Logger;
import org.example.semscan.utils.ServerLogger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Response;

/**
 * ConfigManager - Manages backend-driven configuration for the mobile app
 * 
 * Fetches configuration from backend API endpoint /api/v1/config/mobile
 * Caches configuration in SharedPreferences for offline access
 * Always fetches fresh config on every user login
 * Falls back to cached config or hardcoded defaults if fetch fails
 */
public class ConfigManager {
    // SharedPreferences name for local caching (NOT a database table!)
    // The actual database table is app_config (existing table, used by backend)
    private static final String PREFS_NAME = "config_cache_prefs";
    private static final String KEY_CONFIG_CACHE = "config_cache";
    private static final String KEY_CONFIG_TIMESTAMP = "config_timestamp";
    
    private static ConfigManager instance;
    private Context context;
    private ApiService.MobileConfigResponse cachedConfig;
    private ExecutorService executorService;
    private final Gson gson = new Gson();
    
    private ConfigManager(Context context) {
        try {
            // Guard against null context
            if (context == null) {
                Logger.w(Logger.TAG_PREFS, "ConfigManager initialized with null context - will use defaults only");
                this.context = null;
                this.executorService = null;
                this.cachedConfig = null;
                return;
            }
            
            this.context = context.getApplicationContext();
            this.executorService = Executors.newSingleThreadExecutor();
            loadCachedConfig();
            
            // Use only local Logger during initialization to avoid circular dependency
            // ServerLogger needs ApiClient, which needs ConfigManager - would create circular dependency
            Logger.i(Logger.TAG_PREFS, "ConfigManager initialized successfully");
        } catch (Exception e) {
            // Use only local Logger during initialization to avoid circular dependency
            Logger.e(Logger.TAG_PREFS, "Failed to initialize ConfigManager: " + e.getMessage() + ", Exception: " + e.getClass().getSimpleName(), e);
            // Continue with defaults - don't crash
            try {
                // Guard against null context in fallback
                if (context != null) {
                    this.context = context.getApplicationContext();
                    this.executorService = Executors.newSingleThreadExecutor();
                } else {
                    this.context = null;
                    this.executorService = null;
                    Logger.w(Logger.TAG_PREFS, "ConfigManager fallback: context is null - will use defaults only");
                }
            } catch (Exception e2) {
                Logger.e(Logger.TAG_PREFS, "Critical: Failed to initialize ConfigManager fallback: " + e2.getMessage() + ", Exception: " + e2.getClass().getSimpleName(), e2);
                this.context = null;
                this.executorService = null;
            }
        }
    }
    
    public static synchronized ConfigManager getInstance(Context context) {
        if (instance == null) {
            try {
                // Use only local Logger during initialization to avoid circular dependency
                Logger.i(Logger.TAG_PREFS, "Creating new ConfigManager instance");
                instance = new ConfigManager(context.getApplicationContext());
            } catch (Exception e) {
                // Use only local Logger during initialization to avoid circular dependency
                Logger.e(Logger.TAG_PREFS, "Failed to create ConfigManager instance: " + e.getMessage() + ", Exception: " + e.getClass().getSimpleName(), e);
                // Create a minimal instance that will use defaults
                // This ensures we never return null and never crash
                try {
                    instance = createMinimalInstance(context.getApplicationContext());
                } catch (Exception e2) {
                    Logger.e(Logger.TAG_PREFS, "Critical: Failed to create minimal ConfigManager: " + e2.getMessage(), e2);
                    // Absolute last resort - try one more time with null-safe context
                    try {
                        Context appContext = context != null ? context.getApplicationContext() : null;
                        instance = new ConfigManager(appContext);
                    } catch (Exception e3) {
                        Logger.e(Logger.TAG_PREFS, "Fatal: All ConfigManager creation attempts failed: " + e3.getMessage(), e3);
                        // This should never happen, but if it does, we're in serious trouble
                        // Create instance with null context - constructor should handle it
                        instance = new ConfigManager(null);
                    }
                }
            }
        }
        return instance;
    }
    
    /**
     * Create a minimal ConfigManager instance that uses only defaults
     * This is used as a fallback if normal initialization fails
     */
    private static ConfigManager createMinimalInstance(Context context) {
        // Create instance with minimal initialization - just set defaults
        ConfigManager minimal = new ConfigManager(context != null ? context.getApplicationContext() : null);
        // Force cachedConfig to null so it uses defaults
        minimal.cachedConfig = null;
        return minimal;
    }
    
    /**
     * Refresh configuration from backend API
     * Called on every user login to ensure fresh config
     * Runs in background (non-blocking)
     */
    public void refreshConfig() {
        // Don't crash if executorService is null (minimal instance)
        if (executorService == null) {
            Logger.w(Logger.TAG_PREFS, "Cannot refresh config - executorService is null (minimal instance)");
            return;
        }
        
        // Guard against null context
        if (context == null) {
            Logger.w(Logger.TAG_PREFS, "Cannot refresh config - context is null");
            return;
        }
        
        executorService.execute(() -> {
            try {
                Logger.i(Logger.TAG_PREFS, "Fetching mobile config from backend API");
                
                // Get ApiClient - this might fail if there's a circular dependency issue
                // Use try-catch to handle gracefully
                ApiService apiService;
                try {
                    apiService = ApiClient.getInstance(context).getApiService();
                } catch (Exception e) {
                    Logger.e(Logger.TAG_PREFS, "Failed to get ApiClient for config refresh: " + e.getMessage() + ", Exception: " + e.getClass().getSimpleName(), e);
                    // Try to log to ServerLogger only after ConfigManager is initialized
                    try {
                        if (context != null) {
                            ServerLogger serverLogger = ServerLogger.getInstance(context);
                            if (serverLogger != null) {
                                serverLogger.e(ServerLogger.TAG_UI, "Failed to get ApiClient for config refresh: " + e.getMessage() + ", Exception: " + e.getClass().getSimpleName(), e);
                            }
                        }
                    } catch (Exception logEx) {
                        // Ignore ServerLogger errors to prevent cascading failures
                        Logger.e(Logger.TAG_PREFS, "Failed to log to ServerLogger: " + logEx.getMessage());
                    }
                    return; // Exit early if we can't get ApiClient
                }
                
                Call<ApiService.MobileConfigResponse> call = apiService.getMobileConfig();
                Response<ApiService.MobileConfigResponse> response = call.execute();
                
                if (response.isSuccessful() && response.body() != null) {
                    cachedConfig = response.body();
                    saveCachedConfig(cachedConfig);
                    
                    Logger.i(Logger.TAG_PREFS, "Successfully fetched and cached mobile config");
                    // Try to log to ServerLogger only after ConfigManager is initialized
                    try {
                        if (context != null) {
                            ServerLogger serverLogger = ServerLogger.getInstance(context);
                            if (serverLogger != null) {
                                serverLogger.i(ServerLogger.TAG_UI, "Successfully fetched and cached mobile config");
                            }
                        }
                    } catch (Exception logEx) {
                        // Ignore ServerLogger errors to prevent cascading failures
                        Logger.e(Logger.TAG_PREFS, "Failed to log to ServerLogger: " + logEx.getMessage());
                    }
                } else {
                    String errorMsg = "Failed to fetch config: " + (response.code() > 0 ? "HTTP " + response.code() : "Unknown error");
                    Logger.w(Logger.TAG_PREFS, errorMsg);
                    // Try to log to ServerLogger only after ConfigManager is initialized
                    try {
                        if (context != null) {
                            ServerLogger serverLogger = ServerLogger.getInstance(context);
                            if (serverLogger != null) {
                                serverLogger.w(ServerLogger.TAG_UI, errorMsg);
                            }
                        }
                    } catch (Exception logEx) {
                        // Ignore ServerLogger errors to prevent cascading failures
                        Logger.e(Logger.TAG_PREFS, "Failed to log to ServerLogger: " + logEx.getMessage());
                    }
                }
            } catch (Exception e) {
                Logger.e(Logger.TAG_PREFS, "Failed to fetch mobile config: " + e.getMessage() + ", Exception: " + e.getClass().getSimpleName(), e);
                // Try to log to ServerLogger only after ConfigManager is initialized
                try {
                    if (context != null) {
                        ServerLogger serverLogger = ServerLogger.getInstance(context);
                        if (serverLogger != null) {
                            serverLogger.e(ServerLogger.TAG_UI, "Failed to fetch mobile config: " + e.getMessage() + ", Exception: " + e.getClass().getSimpleName(), e);
                        }
                    }
                } catch (Exception logEx) {
                    // Ignore ServerLogger errors to prevent cascading failures
                    Logger.e(Logger.TAG_PREFS, "Failed to log to ServerLogger: " + logEx.getMessage());
                }
                // Use cached config or defaults - no user-facing error
            }
        });
    }
    
    /**
     * Load cached configuration from SharedPreferences
     */
    private void loadCachedConfig() {
        // Guard against null context
        if (context == null) {
            Logger.w(Logger.TAG_PREFS, "Cannot load cached config - context is null, will use defaults");
            cachedConfig = null;
            return;
        }
        
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String json = prefs.getString(KEY_CONFIG_CACHE, null);
            if (json != null) {
                try {
                    cachedConfig = gson.fromJson(json, ApiService.MobileConfigResponse.class);
                    // Use only local Logger during initialization to avoid circular dependency
                    Logger.i(Logger.TAG_PREFS, "Loaded cached mobile config");
                } catch (JsonSyntaxException e) {
                    // Use only local Logger during initialization to avoid circular dependency
                    Logger.e(Logger.TAG_PREFS, "Failed to parse cached config: " + e.getMessage() + ", Exception: " + e.getClass().getSimpleName(), e);
                    cachedConfig = null;
                }
            } else {
                // Use only local Logger during initialization to avoid circular dependency
                Logger.i(Logger.TAG_PREFS, "No cached config found, will use defaults");
            }
        } catch (Exception e) {
            // Use only local Logger during initialization to avoid circular dependency
            Logger.e(Logger.TAG_PREFS, "Failed to load cached config: " + e.getMessage() + ", Exception: " + e.getClass().getSimpleName(), e);
            cachedConfig = null;
        }
    }
    
    /**
     * Save configuration to SharedPreferences cache
     */
    private void saveCachedConfig(ApiService.MobileConfigResponse config) {
        if (config == null) return;
        
        // Guard against null context
        if (context == null) {
            Logger.w(Logger.TAG_PREFS, "Cannot save cached config - context is null");
            return;
        }
        
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String json = gson.toJson(config);
            prefs.edit()
                .putString(KEY_CONFIG_CACHE, json)
                .putLong(KEY_CONFIG_TIMESTAMP, System.currentTimeMillis())
                .apply();
            
            Logger.i(Logger.TAG_PREFS, "Saved mobile config to cache");
            try {
                ServerLogger serverLogger = ServerLogger.getInstance(context);
                if (serverLogger != null) {
                    serverLogger.i(ServerLogger.TAG_UI, "Saved mobile config to cache");
                }
            } catch (Exception e) {
                // Ignore ServerLogger errors to prevent cascading failures
                Logger.e(Logger.TAG_PREFS, "Failed to log to ServerLogger: " + e.getMessage());
            }
        } catch (Exception e) {
            Logger.e(Logger.TAG_PREFS, "Failed to save cached config: " + e.getMessage(), e);
        }
    }
    
    // =============================
    // Getter Methods with Fallbacks
    // =============================
    
    public String getServerUrl() {
        if (cachedConfig != null && cachedConfig.serverUrl != null && !cachedConfig.serverUrl.trim().isEmpty()) {
            return cachedConfig.serverUrl.trim();
        }
        return ApiConstants.SERVER_URL; // Fallback to hardcoded default
    }
    
    public String getExportEmailRecipients() {
        if (cachedConfig != null && cachedConfig.exportEmailRecipients != null && !cachedConfig.exportEmailRecipients.trim().isEmpty()) {
            return cachedConfig.exportEmailRecipients.trim();
        }
        return ""; // No fallback - must be configured in backend
    }

    public String getSupportEmail() {
        if (cachedConfig != null && cachedConfig.supportEmail != null && !cachedConfig.supportEmail.trim().isEmpty()) {
            return cachedConfig.supportEmail.trim();
        }
        return ""; // No fallback - must be configured in backend
    }
    
    public String getEmailDomain() {
        if (cachedConfig != null && cachedConfig.emailDomain != null && !cachedConfig.emailDomain.trim().isEmpty()) {
            return cachedConfig.emailDomain.trim();
        }
        return "@bgu.ac.il"; // Fallback
    }
    
    public String getTestEmailRecipient() {
        if (cachedConfig != null && cachedConfig.testEmailRecipient != null && !cachedConfig.testEmailRecipient.trim().isEmpty()) {
            return cachedConfig.testEmailRecipient.trim();
        }
        return "talbnwork@gmail.com"; // Fallback
    }
    
    public int getConnectionTimeoutSeconds() {
        if (cachedConfig != null && cachedConfig.connectionTimeoutSeconds > 0) {
            return cachedConfig.connectionTimeoutSeconds;
        }
        return ApiConstants.CONNECTION_TIMEOUT_SECONDS; // Fallback
    }
    
    public int getReadTimeoutSeconds() {
        if (cachedConfig != null && cachedConfig.readTimeoutSeconds > 0) {
            return cachedConfig.readTimeoutSeconds;
        }
        return ApiConstants.READ_TIMEOUT_SECONDS; // Fallback
    }
    
    public int getWriteTimeoutSeconds() {
        if (cachedConfig != null && cachedConfig.writeTimeoutSeconds > 0) {
            return cachedConfig.writeTimeoutSeconds;
        }
        return ApiConstants.WRITE_TIMEOUT_SECONDS; // Fallback
    }
    
    public int getManualAttendanceWindowBeforeMinutes() {
        if (cachedConfig != null && cachedConfig.manualAttendanceWindowBeforeMinutes > 0) {
            return cachedConfig.manualAttendanceWindowBeforeMinutes;
        }
        return ApiConstants.MANUAL_ATTENDANCE_WINDOW_BEFORE_MINUTES; // Fallback
    }
    
    public int getManualAttendanceWindowAfterMinutes() {
        if (cachedConfig != null && cachedConfig.manualAttendanceWindowAfterMinutes > 0) {
            return cachedConfig.manualAttendanceWindowAfterMinutes;
        }
        return ApiConstants.MANUAL_ATTENDANCE_WINDOW_AFTER_MINUTES; // Fallback
    }
    
    public int getMaxExportFileSizeMb() {
        if (cachedConfig != null && cachedConfig.maxExportFileSizeMb > 0) {
            return cachedConfig.maxExportFileSizeMb;
        }
        return ApiConstants.MAX_EXPORT_FILE_SIZE_MB; // Fallback
    }
    
    public int getToastDurationError() {
        if (cachedConfig != null && cachedConfig.toastDurationError > 0) {
            return cachedConfig.toastDurationError;
        }
        return ApiConstants.TOAST_DURATION_ERROR; // Fallback
    }
    
    public int getToastDurationSuccess() {
        if (cachedConfig != null && cachedConfig.toastDurationSuccess > 0) {
            return cachedConfig.toastDurationSuccess;
        }
        return ApiConstants.TOAST_DURATION_SUCCESS; // Fallback
    }
    
    public int getToastDurationInfo() {
        if (cachedConfig != null && cachedConfig.toastDurationInfo > 0) {
            return cachedConfig.toastDurationInfo;
        }
        return ApiConstants.TOAST_DURATION_INFO; // Fallback
    }
    
    public int getPresenterSlotOpenWindowBeforeMinutes() {
        if (cachedConfig != null && cachedConfig.presenterSlotOpenWindowBeforeMinutes > 0) {
            return cachedConfig.presenterSlotOpenWindowBeforeMinutes;
        }
        return 30; // Fallback default
    }
    
    public int getPresenterSlotOpenWindowAfterMinutes() {
        if (cachedConfig != null && cachedConfig.presenterSlotOpenWindowAfterMinutes > 0) {
            return cachedConfig.presenterSlotOpenWindowAfterMinutes;
        }
        return 15; // Fallback default
    }
    
    public int getStudentAttendanceWindowBeforeMinutes() {
        if (cachedConfig != null && cachedConfig.studentAttendanceWindowBeforeMinutes > 0) {
            return cachedConfig.studentAttendanceWindowBeforeMinutes;
        }
        return 5; // Fallback default
    }
    
    public int getStudentAttendanceWindowAfterMinutes() {
        if (cachedConfig != null && cachedConfig.studentAttendanceWindowAfterMinutes > 0) {
            return cachedConfig.studentAttendanceWindowAfterMinutes;
        }
        return 10; // Fallback default
    }
    
    public int getWaitingListApprovalWindowHours() {
        if (cachedConfig != null && cachedConfig.waitingListApprovalWindowHours > 0) {
            return cachedConfig.waitingListApprovalWindowHours;
        }
        return 24; // Fallback default
    }

    public int getPresenterCloseSessionDurationMinutes() {
        if (cachedConfig != null && cachedConfig.presenterCloseSessionDurationMinutes > 0) {
            return cachedConfig.presenterCloseSessionDurationMinutes;
        }
        return 15; // Hardcoded fallback (15 minutes)
    }
    
    public String getEmailFromName() {
        if (cachedConfig != null && cachedConfig.emailFromName != null && !cachedConfig.emailFromName.trim().isEmpty()) {
            return cachedConfig.emailFromName.trim();
        }
        return "SemScan System"; // Fallback
    }
    
    public String getEmailReplyTo() {
        if (cachedConfig != null && cachedConfig.emailReplyTo != null && !cachedConfig.emailReplyTo.trim().isEmpty()) {
            return cachedConfig.emailReplyTo.trim();
        }
        return "noreply@bgu.ac.il"; // Fallback
    }
    
    public String getEmailBccList() {
        if (cachedConfig != null && cachedConfig.emailBccList != null && !cachedConfig.emailBccList.trim().isEmpty()) {
            return cachedConfig.emailBccList.trim();
        }
        return "admin@bgu.ac.il"; // Fallback
    }

    public String getAppVersion() {
        if (cachedConfig != null && cachedConfig.appVersion != null && !cachedConfig.appVersion.trim().isEmpty()) {
            return cachedConfig.appVersion.trim();
        }
        return "1.0"; // Fallback to default version
    }
    
    /**
     * Get the maximum number of users allowed on waiting list per slot
     * Default is 2 if not configured
     */
    public int getWaitingListLimitPerSlot() {
        if (cachedConfig != null && cachedConfig.waitingListLimitPerSlot > 0) {
            return cachedConfig.waitingListLimitPerSlot;
        }
        return 2; // Fallback default
    }
    
    /**
     * Get the capacity weight for PhD students (how many slots a PhD counts as)
     * Default is 2 if not configured
     */
    public int getPhdCapacityWeight() {
        if (cachedConfig != null && cachedConfig.phdCapacityWeight > 0) {
            return cachedConfig.phdCapacityWeight;
        }
        return 2; // Fallback default
    }
}

