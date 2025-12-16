package org.example.semscan.data.api;

import android.content.Context;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import org.example.semscan.constants.ApiConstants;

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
        currentBaseUrl = normalizeBaseUrl(DEFAULT_BASE_URL);
        
        // Log the current API URL for debugging
        android.util.Log.i("ApiClient", "Current API Base URL: " + currentBaseUrl);
        
        createApiService();
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
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                .addInterceptor(httpLogging) // Android Logcat logging
                .addInterceptor(apiLogging)  // ServerLogger (app_logs) logging
                .connectTimeout(ApiConstants.CONNECTION_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(ApiConstants.READ_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(ApiConstants.WRITE_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
        
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
        String currentUrl = normalizeBaseUrl(DEFAULT_BASE_URL);
        
        // If instance is null or URL has changed, create new instance
        if (instance == null || !instance.currentBaseUrl.equals(currentUrl)) {
            android.util.Log.i("ApiClient", "Creating new instance - URL changed from " + 
                (instance != null ? instance.currentBaseUrl : "null") + " to " + currentUrl);
            instance = new ApiClient(context);
        }
        return instance;
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
