package com.yourpackage.yourapp;

public class Config {

    // Define your environments
    public enum Environment {
        DEVELOPMENT, // For localhost or local IP
        TESTING,     // For a staging server
        PRODUCTION   // For your live production server
    }

    // Set the current environment
    // Change this line to switch between environments
    private static final Environment CURRENT_ENVIRONMENT = Environment.DEVELOPMENT; // <--- CHANGE THIS!

    // Define URLs for each environment
    private static final String DEVELOPMENT_URL = "http://localhost:8080/"; // Use 10.0.2.2 for Android Emulator, or your PC's IP for physical device
    private static final String TESTING_URL = "http://your.testing.server.com:8080/";
    private static final String PRODUCTION_URL = "https://your.production.server.com/";

    /**
     * Returns the base URL for the current environment.
     * This is what will be used for all API calls, overriding any URLs in QR codes.
     *
     * @return The API base URL.
     */
    public static String getServerUrl() {
        switch (CURRENT_ENVIRONMENT) {
            case DEVELOPMENT:
                return DEVELOPMENT_URL;
            case TESTING:
                return TESTING_URL;
            case PRODUCTION:
                return PRODUCTION_URL;
            default:
                // Fallback or throw an error for unhandled environments
                throw new IllegalStateException("Unhandled environment: " + CURRENT_ENVIRONMENT);
        }
    }

    /**
     * Returns the current environment.
     *
     * @return The current Environment enum.
     */
    public static Environment getCurrentEnvironment() {
        return CURRENT_ENVIRONMENT;
    }

    /**
     * Get the base URL without trailing slash for URL building
     */
    public static String getServerUrlClean() {
        String url = getServerUrl();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    // You can add other configuration methods here, e.g., API keys per environment
    public static String getApiKey(Environment env) {
        switch (env) {
            case DEVELOPMENT:
                return "dev-api-key-123";
            case TESTING:
                return "test-api-key-abc";
            case PRODUCTION:
                return "prod-api-key-xyz";
            default:
                return "";
        }
    }

    /**
     * Get the default API key for the current environment
     */
    public static String getDefaultApiKey() {
        return getApiKey(CURRENT_ENVIRONMENT);
    }

    /**
     * Check if we're in development mode
     */
    public static boolean isDevelopment() {
        return CURRENT_ENVIRONMENT == Environment.DEVELOPMENT;
    }

    /**
     * Check if we're in production mode
     */
    public static boolean isProduction() {
        return CURRENT_ENVIRONMENT == Environment.PRODUCTION;
    }
}
