package org.example.semscan.constants;

/**
 * Example usage of ApiConstants in your activities
 * This file shows how to use the constants in your code
 */
public class ApiUsageExample {
    
    /**
     * Example: How to use constants in API calls
     */
    public void exampleApiUsage() {
        
        // 1. Get seminars
        String seminarsUrl = ApiConstants.SEMINARS_ENDPOINT;
        // GET http://localhost:8080/api/v1/seminars
        
        // 2. Create a session
        String createSessionUrl = ApiConstants.CREATE_SESSION_ENDPOINT;
        // POST http://localhost:8080/api/v1/sessions
        
        // 3. Get attendance for a specific session
        // Note: In real usage, sessionId would come from API response
        long sessionId = 512L; // Example ID - use actual ID from API
        String attendanceUrl = ApiConstants.buildSessionUrl(sessionId);
        // GET http://localhost:8080/api/v1/attendance?sessionId=512
        
        // 4. Get pending manual requests
        String pendingRequestsUrl = ApiConstants.buildPendingRequestsUrl(sessionId);
        // GET http://localhost:8080/api/v1/attendance/pending-requests?sessionId=SESSIN-1-YYYYMMDD
        
        // 5. Approve a manual request
        // Note: In real usage, attendanceId would come from API response
        long attendanceId = 2048L; // Example ID - use actual ID from API
        String approveUrl = ApiConstants.buildApproveRequestUrl(attendanceId);
        // POST http://localhost:8080/api/v1/attendance/2048/approve
        
        // 6. Export CSV
        String exportCsvUrl = ApiConstants.buildExportCsvUrl(sessionId);
        // GET http://localhost:8080/api/v1/export/csv?sessionId=512
        
        // 7. Check response codes
        int responseCode = 200;
        boolean isSuccess = ApiConstants.isSuccessResponse(responseCode); // true
        boolean isClientError = ApiConstants.isClientError(responseCode); // false
        boolean isServerError = ApiConstants.isServerError(responseCode); // false
        
        // 8. No authentication required - all endpoints are public
        // API key authentication has been removed from the API
        
        // 9. Use timeouts
        int connectionTimeout = ApiConstants.CONNECTION_TIMEOUT_SECONDS; // 5
        int readTimeout = ApiConstants.READ_TIMEOUT_SECONDS; // 5
        int writeTimeout = ApiConstants.WRITE_TIMEOUT_SECONDS; // 5
    }
    
    /**
     * Example: How to use constants in Retrofit interface
     */
    public void exampleRetrofitUsage() {
        // In your Retrofit interface, you can use the constants like this:
        // No authentication headers required - all endpoints are public
        
        // @GET(ApiConstants.SEMINARS_ENDPOINT)
        // Call<List<Seminar>> getSeminars();
        
        // @POST(ApiConstants.CREATE_SESSION_ENDPOINT)
        // Call<Session> createSession(@Body CreateSessionRequest request);
        
        // @GET(ApiConstants.PENDING_REQUESTS_ENDPOINT)
        // Call<List<Attendance>> getPendingRequests(@Query("sessionId") String sessionId);
    }
    
    /**
     * Example: How to use constants in HTTP headers
     */
    public void exampleHeaderUsage() {
        // When making HTTP requests, use the constants for headers:
        // No authentication headers required - all endpoints are public
        
        // Headers headers = new Headers.Builder()
        //     .add("Content-Type", ApiConstants.CONTENT_TYPE_JSON)
        //     .build();
    }
    
    /**
     * Example: How to use constants for configuration
     */
    public void exampleConfigurationUsage() {
        // Use constants for app configuration:
        
        String appName = ApiConstants.APPLICATION_NAME; // "SemScan"
        String appVersion = ApiConstants.APPLICATION_VERSION; // "1.0.0"
        String environment = ApiConstants.ENVIRONMENT; // "development"
        
        // Manual attendance configuration
        int windowBefore = ApiConstants.MANUAL_ATTENDANCE_WINDOW_BEFORE_MINUTES; // 10
        int windowAfter = ApiConstants.MANUAL_ATTENDANCE_WINDOW_AFTER_MINUTES; // 15
        int autoApproveCap = ApiConstants.MANUAL_ATTENDANCE_AUTO_APPROVE_MIN_CAP; // 5
    }
}
