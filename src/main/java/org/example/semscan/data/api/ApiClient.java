package org.example.semscan.data.api;

import android.content.Context;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import org.example.semscan.constants.ApiConstants;
import org.example.semscan.utils.ConfigManager;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.security.SecureRandom;

public class ApiClient {
    private static final String DEFAULT_BASE_URL = ApiConstants.SERVER_URL;
    private static ApiClient instance;
    private ApiService apiService;
    private String currentBaseUrl;
    private Context context;
    
    private ApiClient(Context context) {
        this.context = context.getApplicationContext();
        // Use ConfigManager if available, otherwise fallback to hardcoded URL
        // This handles circular dependency: ConfigManager needs ApiClient, ApiClient needs ConfigManager
        String baseUrl = getServerUrlFromConfig(context);
        currentBaseUrl = normalizeBaseUrl(baseUrl);
        
        // Log the current API URL for debugging
        android.util.Log.i("ApiClient", "Current API Base URL: " + currentBaseUrl);
        
        createApiService();
    }
    
    /**
     * Get server URL from ConfigManager if available, otherwise use hardcoded default
     * This handles the circular dependency: ConfigManager needs ApiClient to fetch config,
     * but ApiClient needs ConfigManager for the URL. First call uses hardcoded URL.
     */
    private String getServerUrlFromConfig(Context context) {
        try {
            ConfigManager configManager = ConfigManager.getInstance(context);
            String configUrl = configManager.getServerUrl();
            if (configUrl != null && !configUrl.trim().isEmpty()) {
                return configUrl;
            }
        } catch (Exception e) {
            // ConfigManager not ready yet - use hardcoded default
            android.util.Log.d("ApiClient", "ConfigManager not ready, using hardcoded URL: " + e.getMessage());
        }
        return DEFAULT_BASE_URL;
    }
    
    private void createApiService() {
        // Standard HTTP logging for Android Logcat (with password sanitization)
        HttpLoggingInterceptor httpLogging = new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger() {
            @Override
            public void log(String message) {
                // Sanitize passwords before logging
                String sanitized = sanitizePasswords(message);
                android.util.Log.i("OkHttp", sanitized);
            }
        });
        httpLogging.setLevel(HttpLoggingInterceptor.Level.BODY);
        
        // Custom interceptor for ServerLogger (app_logs) - logs request/response bodies
        ApiLoggingInterceptor apiLogging = new ApiLoggingInterceptor(context);
        
        // Configure SSL for self-signed certificates
        // Note: This trusts user certificates (configured in network_security_config.xml)
        // For production, use proper certificate pinning
        
        // Get timeout values from ConfigManager if available, otherwise use hardcoded defaults
        int connectionTimeout = getTimeoutFromConfig(context, "connection");
        int readTimeout = getTimeoutFromConfig(context, "read");
        int writeTimeout = getTimeoutFromConfig(context, "write");
        
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                .addInterceptor(httpLogging) // Android Logcat logging
                .addInterceptor(apiLogging)  // ServerLogger (app_logs) logging
                .connectTimeout(connectionTimeout, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(readTimeout, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(writeTimeout, java.util.concurrent.TimeUnit.SECONDS);
        
        // Trust self-signed certificates (for testing/development)
        // The network_security_config.xml allows user certificates
        // This is needed because Android's default SSL context may not trust self-signed certs
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        // Trust all client certificates
                    }
                    
                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        // Trust all server certificates (including self-signed)
                    }
                    
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[]{};
                    }
                }
            };
            
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            clientBuilder.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0]);
            clientBuilder.hostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            android.util.Log.e("ApiClient", "Failed to configure SSL for self-signed certificates", e);
        }
        
        OkHttpClient client = clientBuilder.build();
        
        android.util.Log.i("ApiClient", "Creating Retrofit with base URL: " + currentBaseUrl);
        
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(currentBaseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        
        apiService = retrofit.create(ApiService.class);
    }
    
    public static synchronized ApiClient getInstance(Context context) {
        // Get current URL from ConfigManager if available, otherwise use hardcoded default
        String currentUrl;
        try {
            ConfigManager configManager = ConfigManager.getInstance(context);
            String configUrl = configManager.getServerUrl();
            currentUrl = (configUrl != null && !configUrl.trim().isEmpty()) 
                ? normalizeBaseUrl(configUrl) 
                : normalizeBaseUrl(DEFAULT_BASE_URL);
        } catch (Exception e) {
            // ConfigManager not ready yet - use hardcoded default
            currentUrl = normalizeBaseUrl(DEFAULT_BASE_URL);
        }
        
        // If instance is null or URL has changed, create new instance
        if (instance == null || !instance.currentBaseUrl.equals(currentUrl)) {
            android.util.Log.i("ApiClient", "Creating new instance - URL changed from " + 
                (instance != null ? instance.currentBaseUrl : "null") + " to " + currentUrl);
            instance = new ApiClient(context);
        }
        return instance;
    }
    
    /**
     * Get timeout value from ConfigManager if available, otherwise use hardcoded default
     */
    private int getTimeoutFromConfig(Context context, String timeoutType) {
        try {
            ConfigManager configManager = ConfigManager.getInstance(context);
            switch (timeoutType) {
                case "connection":
                    return configManager.getConnectionTimeoutSeconds();
                case "read":
                    return configManager.getReadTimeoutSeconds();
                case "write":
                    return configManager.getWriteTimeoutSeconds();
                default:
                    return ApiConstants.CONNECTION_TIMEOUT_SECONDS;
            }
        } catch (Exception e) {
            // ConfigManager not ready yet - use hardcoded defaults
            switch (timeoutType) {
                case "connection":
                    return ApiConstants.CONNECTION_TIMEOUT_SECONDS;
                case "read":
                    return ApiConstants.READ_TIMEOUT_SECONDS;
                case "write":
                    return ApiConstants.WRITE_TIMEOUT_SECONDS;
                default:
                    return ApiConstants.CONNECTION_TIMEOUT_SECONDS;
            }
        }
    }
    
    /**
     * Force recreation of ApiClient instance (useful when config changes)
     */
    public static synchronized void recreateInstance(Context context) {
        instance = null;
        getInstance(context);
    }
    
    public ApiService getApiService() {
        return apiService;
    }
    
    public String getCurrentBaseUrl() {
        return currentBaseUrl;
    }
    
    private static String normalizeBaseUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return "http://132.72.50.53:8080/";
        }
        String trimmed = url.trim();
        if (!trimmed.endsWith("/")) {
            trimmed += "/";
        }
        return trimmed;
    }
    
    /**
     * Sanitize passwords from JSON strings to prevent logging sensitive data.
     * Replaces password values with "***" while preserving JSON structure.
     */
    private static String sanitizePasswords(String message) {
        if (message == null || message.trim().isEmpty()) {
            return message;
        }
        
        // Pattern to match "password":"value" or "password": "value" in JSON
        // Handles escaped quotes and any characters in the password value
        // CASE_INSENSITIVE handles "password", "Password", "PASSWORD", etc.
        java.util.regex.Pattern passwordPattern = java.util.regex.Pattern.compile(
            "\"password\"\\s*:\\s*\"(?:[^\"\\\\]|\\\\.)*\"",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        
        java.util.regex.Matcher matcher = passwordPattern.matcher(message);
        return matcher.replaceAll("\"password\":\"***\"");
    }
}
