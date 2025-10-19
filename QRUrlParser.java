package com.yourpackage.yourapp.utils;

import android.net.Uri;
import okhttp3.HttpUrl;
import android.util.Log;

public class QRUrlParser {
    
    private static final String TAG = "QRUrlParser";
    
    /**
     * Parses a QR code URL and rebuilds it using the app's base URL
     * This ensures the app always uses the correct server regardless of what's in the QR
     */
    public static String parseAndRebuildUrl(String scannedText, String baseUrl) {
        try {
            Log.d(TAG, "Parsing QR URL: " + scannedText);
            Log.d(TAG, "Using base URL: " + baseUrl);
            
            // Parse the scanned text as a URI
            Uri scannedUri = Uri.parse(scannedText);
            
            // Extract path and query from the scanned URL
            String path = scannedUri.getPath();        // "/api/v1/sessions/060147dc"
            String query = scannedUri.getQuery();      // "x=1" (may be null)
            
            Log.d(TAG, "Extracted path: " + path);
            Log.d(TAG, "Extracted query: " + query);
            
            // Parse the base URL
            HttpUrl base = HttpUrl.parse(baseUrl);
            if (base == null) {
                throw new IllegalArgumentException("Invalid base URL: " + baseUrl);
            }
            
            // Build the new URL using the base URL
            HttpUrl.Builder builder = base.newBuilder();
            
            // Add path segments (remove leading slash if present)
            if (path != null && !path.isEmpty()) {
                String cleanPath = path.startsWith("/") ? path.substring(1) : path;
                builder.addPathSegments(cleanPath);
            }
            
            // Add query parameters if they exist
            if (query != null && !query.isEmpty()) {
                builder.encodedQuery(query);
            }
            
            HttpUrl finalUrl = builder.build();
            String result = finalUrl.toString();
            
            Log.d(TAG, "Rebuilt URL: " + result);
            return result;
            
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse URL, treating as session ID: " + e.getMessage());
            // If parsing fails, treat the scanned text as a session ID and build manually
            return buildSessionUrl(scannedText, baseUrl);
        }
    }
    
    /**
     * Fallback method: treat scanned text as a session ID and build the URL manually
     */
    private static String buildSessionUrl(String sessionId, String baseUrl) {
        try {
            Log.d(TAG, "Building session URL for ID: " + sessionId);
            
            HttpUrl base = HttpUrl.parse(baseUrl);
            if (base == null) {
                throw new IllegalArgumentException("Invalid base URL: " + baseUrl);
            }
            
            HttpUrl finalUrl = base.newBuilder()
                .addPathSegments("api/v1/sessions/" + sessionId)
                .build();
                
            String result = finalUrl.toString();
            Log.d(TAG, "Built session URL: " + result);
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Failed to build session URL: " + e.getMessage());
            // Last resort: return the original scanned text
            return scannedText;
        }
    }
    
    /**
     * Extract session ID from a QR URL
     */
    public static String extractSessionId(String scannedText) {
        try {
            Uri uri = Uri.parse(scannedText);
            String path = uri.getPath();
            
            if (path != null && path.contains("/sessions/")) {
                String[] segments = path.split("/");
                for (int i = 0; i < segments.length - 1; i++) {
                    if ("sessions".equals(segments[i])) {
                        String sessionId = segments[i + 1];
                        Log.d(TAG, "Extracted session ID: " + sessionId);
                        return sessionId;
                    }
                }
            }
            
            // If no session ID found in path, return the whole scanned text
            Log.d(TAG, "No session ID found, using full text: " + scannedText);
            return scannedText;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to extract session ID: " + e.getMessage());
            return scannedText;
        }
    }
    
    /**
     * Test method to verify URL parsing works correctly
     */
    public static void testUrlParsing() {
        String baseUrl = "http://localhost:8080/";
        
        // Test case 1: Full URL with old IP
        String qr1 = "http://132.72.54.104:8080/api/v1/sessions/060147dc?x=1";
        String result1 = parseAndRebuildUrl(qr1, baseUrl);
        Log.d(TAG, "Test 1 - Input: " + qr1);
        Log.d(TAG, "Test 1 - Output: " + result1);
        // Expected: "http://localhost:8080/api/v1/sessions/060147dc?x=1"
        
        // Test case 2: Just session ID
        String qr2 = "060147dc";
        String result2 = parseAndRebuildUrl(qr2, baseUrl);
        Log.d(TAG, "Test 2 - Input: " + qr2);
        Log.d(TAG, "Test 2 - Output: " + result2);
        // Expected: "http://localhost:8080/api/v1/sessions/060147dc"
        
        // Test case 3: Relative path
        String qr3 = "/api/v1/sessions/060147dc";
        String result3 = parseAndRebuildUrl(qr3, baseUrl);
        Log.d(TAG, "Test 3 - Input: " + qr3);
        Log.d(TAG, "Test 3 - Output: " + result3);
        // Expected: "http://localhost:8080/api/v1/sessions/060147dc"
        
        // Test case 4: Different old IP
        String qr4 = "http://132.73.167.231:8080/api/v1/sessions/060147dc";
        String result4 = parseAndRebuildUrl(qr4, baseUrl);
        Log.d(TAG, "Test 4 - Input: " + qr4);
        Log.d(TAG, "Test 4 - Output: " + result4);
        // Expected: "http://localhost:8080/api/v1/sessions/060147dc"
    }
}
