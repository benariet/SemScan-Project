# Android IP Configuration Solution
## Replace Hardcoded IP Addresses with Centralized Configuration

### 🎯 **Problem**
Your Android app has hardcoded IP addresses (`132.73.167.231`) scattered throughout the code, making it difficult to change server URLs and maintain the app.

### ✅ **Solution**
Implement a centralized configuration system that allows you to easily change the server URL from one place.

---

## 📁 **Step 1: Create Configuration Files**

### 1.1 Create `Config.java` (Main Configuration Class)
```java
package org.example.semscan.config;

public class Config {
    // =============================================
    // SERVER CONFIGURATION
    // =============================================
    
    // Default server URL - change this ONE place to update entire app
    private static final String DEFAULT_SERVER_URL = "http://localhost:8080/";
    
    // Alternative URLs for different environments
    private static final String DEVELOPMENT_URL = "http://localhost:8080/";
    private static final String PRODUCTION_URL = "http://your-production-server.com:8080/";
    private static final String TESTING_URL = "http://132.72.54.104:8080/";
    
    // Current environment (change this to switch environments)
    private static final Environment CURRENT_ENVIRONMENT = Environment.DEVELOPMENT;
    
    // =============================================
    // ENVIRONMENT ENUM
    // =============================================
    public enum Environment {
        DEVELOPMENT,
        PRODUCTION,
        TESTING
    }
    
    // =============================================
    // PUBLIC METHODS
    // =============================================
    
    /**
     * Get the current server URL based on environment
     */
    public static String getServerUrl() {
        switch (CURRENT_ENVIRONMENT) {
            case DEVELOPMENT:
                return DEVELOPMENT_URL;
            case PRODUCTION:
                return PRODUCTION_URL;
            case TESTING:
                return TESTING_URL;
            default:
                return DEFAULT_SERVER_URL;
        }
    }
    
    /**
     * Get the current environment
     */
    public static Environment getCurrentEnvironment() {
        return CURRENT_ENVIRONMENT;
    }
    
    /**
     * Check if running in development mode
     */
    public static boolean isDevelopment() {
        return CURRENT_ENVIRONMENT == Environment.DEVELOPMENT;
    }
    
    /**
     * Check if running in production mode
     */
    public static boolean isProduction() {
        return CURRENT_ENVIRONMENT == Environment.PRODUCTION;
    }
    
    /**
     * Check if running in testing mode
     */
    public static boolean isTesting() {
        return CURRENT_ENVIRONMENT == Environment.TESTING;
    }
    
    // =============================================
    // API ENDPOINTS
    // =============================================
    
    public static String getSeminarsEndpoint() {
        return getServerUrl() + "api/v1/seminars";
    }
    
    public static String getSessionsEndpoint() {
        return getServerUrl() + "api/v1/sessions";
    }
    
    public static String getOpenSessionsEndpoint() {
        return getServerUrl() + "api/v1/sessions/open";
    }
    
    public static String getAttendanceEndpoint() {
        return getServerUrl() + "api/v1/attendance";
    }
    
    public static String getManualAttendanceEndpoint() {
        return getServerUrl() + "api/v1/attendance/manual-request";
    }
    
    public static String getPendingRequestsEndpoint() {
        return getServerUrl() + "api/v1/attendance/pending-requests";
    }
    
    public static String getApproveRequestEndpoint(String attendanceId) {
        return getServerUrl() + "api/v1/attendance/" + attendanceId + "/approve";
    }
    
    public static String getRejectRequestEndpoint(String attendanceId) {
        return getServerUrl() + "api/v1/attendance/" + attendanceId + "/reject";
    }
    
    public static String getExportCsvEndpoint() {
        return getServerUrl() + "api/v1/export/csv";
    }
    
    public static String getExportXlsxEndpoint() {
        return getServerUrl() + "api/v1/export/xlsx";
    }
}
```

### 1.2 Create `strings.xml` (Alternative Configuration)
```xml
<!-- res/values/strings.xml -->
<resources>
    <!-- Server Configuration -->
    <string name="server_url_development">http://localhost:8080/</string>
    <string name="server_url_production">http://your-production-server.com:8080/</string>
    <string name="server_url_testing">http://132.72.54.104:8080/</string>
    
    <!-- Current Environment -->
    <string name="current_environment">development</string>
    
    <!-- API Endpoints -->
    <string name="api_seminars">api/v1/seminars</string>
    <string name="api_sessions">api/v1/sessions</string>
    <string name="api_sessions_open">api/v1/sessions/open</string>
    <string name="api_attendance">api/v1/attendance</string>
    <string name="api_manual_attendance">api/v1/attendance/manual-request</string>
    <string name="api_pending_requests">api/v1/attendance/pending-requests</string>
    <string name="api_export_csv">api/v1/export/csv</string>
    <string name="api_export_xlsx">api/v1/export/xlsx</string>
</resources>
```

---

## 📁 **Step 2: Update Your API Service Classes**

### 2.1 Update `ApiService.java`
```java
package org.example.semscan.data.api;

import org.example.semscan.config.Config;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiService {
    
    // Use Config class instead of hardcoded URL
    private static final String BASE_URL = Config.getServerUrl();
    
    private static Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build();
    
    public static ApiInterface getApiInterface() {
        return retrofit.create(ApiInterface.class);
    }
    
    // Method to get current server URL (for debugging)
    public static String getCurrentServerUrl() {
        return BASE_URL;
    }
}
```

### 2.2 Update `ApiInterface.java`
```java
package org.example.semscan.data.api;

import org.example.semscan.data.model.Seminar;
import org.example.semscan.data.model.Session;
import org.example.semscan.data.model.Attendance;
import org.example.semscan.data.model.ManualAttendanceRequest;
import org.example.semscan.data.model.ManualAttendanceResponse;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;

public interface ApiInterface {
    
    // =============================================
    // SEMINAR ENDPOINTS
    // =============================================
    
    @GET("api/v1/seminars")
    Call<List<Seminar>> getSeminars(@Header("x-api-key") String apiKey);
    
    @POST("api/v1/seminars")
    Call<Seminar> createSeminar(@Header("x-api-key") String apiKey, @Body Seminar seminar);
    
    // =============================================
    // SESSION ENDPOINTS
    // =============================================
    
    @GET("api/v1/sessions")
    Call<List<Session>> getSessions(@Header("x-api-key") String apiKey);
    
    @GET("api/v1/sessions/open")
    Call<List<Session>> getOpenSessions(@Header("x-api-key") String apiKey);
    
    @POST("api/v1/sessions")
    Call<Session> createSession(@Header("x-api-key") String apiKey, @Body Session session);
    
    // =============================================
    // ATTENDANCE ENDPOINTS
    // =============================================
    
    @GET("api/v1/attendance/session/{sessionId}")
    Call<List<Attendance>> getAttendanceBySession(@Path("sessionId") String sessionId);
    
    @POST("api/v1/attendance")
    Call<Attendance> markAttendance(@Header("x-api-key") String apiKey, @Body Attendance attendance);
    
    // =============================================
    // MANUAL ATTENDANCE ENDPOINTS
    // =============================================
    
    @POST("api/v1/attendance/manual-request")
    Call<ManualAttendanceResponse> createManualRequest(@Body ManualAttendanceRequest request);
    
    @GET("api/v1/attendance/pending-requests")
    Call<List<ManualAttendanceResponse>> getPendingRequests(
            @Query("sessionId") String sessionId,
            @Header("x-api-key") String apiKey
    );
    
    @POST("api/v1/attendance/{id}/approve")
    Call<ManualAttendanceResponse> approveRequest(
            @Path("id") String attendanceId,
            @Header("x-api-key") String apiKey
    );
    
    @POST("api/v1/attendance/{id}/reject")
    Call<ManualAttendanceResponse> rejectRequest(
            @Path("id") String attendanceId,
            @Header("x-api-key") String apiKey
    );
    
    // =============================================
    // EXPORT ENDPOINTS
    // =============================================
    
    @GET("api/v1/export/csv")
    Call<ResponseBody> exportCsv(
            @Query("sessionId") String sessionId,
            @Header("x-api-key") String apiKey
    );
    
    @GET("api/v1/export/xlsx")
    Call<ResponseBody> exportXlsx(
            @Query("sessionId") String sessionId,
            @Header("x-api-key") String apiKey
    );
}
```

---

## 📁 **Step 3: Update All Files with Hardcoded IPs**

### 3.1 Search and Replace Commands
In Android Studio, use these search and replace operations:

**Search for:** `132.73.167.231`
**Replace with:** `Config.getServerUrl()`

**Search for:** `http://132.73.167.231:8080`
**Replace with:** `Config.getServerUrl()`

**Search for:** `"http://132.73.167.231:8080"`
**Replace with:** `Config.getServerUrl()`

### 3.2 Common Files to Update
- `ApiService.java`
- `ApiInterface.java`
- `NetworkModule.java` (if using Dagger/Hilt)
- `Constants.java`
- `Config.java` (if you have one)
- Any activity or fragment that makes API calls
- `strings.xml` (if using string resources)

---

## 📁 **Step 4: Environment Switching**

### 4.1 Quick Environment Switch
To change environments, simply modify the `CURRENT_ENVIRONMENT` in `Config.java`:

```java
// For development (localhost)
private static final Environment CURRENT_ENVIRONMENT = Environment.DEVELOPMENT;

// For testing (your current server)
private static final Environment CURRENT_ENVIRONMENT = Environment.TESTING;

// For production
private static final Environment CURRENT_ENVIRONMENT = Environment.PRODUCTION;
```

### 4.2 Build Variants (Advanced)
Create different build variants for different environments:

**build.gradle (Module: app)**
```gradle
android {
    buildTypes {
        debug {
            buildConfigField "String", "SERVER_URL", '"http://localhost:8080/"'
            buildConfigField "String", "ENVIRONMENT", '"development"'
        }
        release {
            buildConfigField "String", "SERVER_URL", '"http://your-production-server.com:8080/"'
            buildConfigField "String", "ENVIRONMENT", '"production"'
        }
        testing {
            buildConfigField "String", "SERVER_URL", '"http://132.72.54.104:8080/"'
            buildConfigField "String", "ENVIRONMENT", '"testing"'
        }
    }
}
```

Then use in your code:
```java
String serverUrl = BuildConfig.SERVER_URL;
String environment = BuildConfig.ENVIRONMENT;
```

---

## 📁 **Step 5: Implementation Steps**

### 5.1 Immediate Actions
1. **Create the `Config.java` file** with the code above
2. **Update `ApiService.java`** to use `Config.getServerUrl()`
3. **Search your entire project** for `132.73.167.231` and replace with `Config.getServerUrl()`
4. **Test the app** to ensure it still works

### 5.2 Clean Up
1. **Remove any hardcoded URLs** from activities/fragments
2. **Update any constants files** to use the Config class
3. **Test all API endpoints** to ensure they work

### 5.3 Future Maintenance
1. **To change server URL:** Only modify `Config.java`
2. **To add new endpoints:** Add them to the Config class
3. **To switch environments:** Change the `CURRENT_ENVIRONMENT` enum

---

## 🎯 **Benefits of This Approach**

✅ **Single Source of Truth:** All URLs defined in one place
✅ **Easy Environment Switching:** Change one line to switch environments
✅ **Maintainable:** No more hunting for hardcoded URLs
✅ **Scalable:** Easy to add new endpoints and environments
✅ **Debug Friendly:** Can easily see which environment you're using
✅ **Team Friendly:** Other developers can easily understand the configuration

---

## 🚀 **Quick Start**

1. **Copy the `Config.java` code** above into your Android project
2. **Update your `ApiService.java`** to use `Config.getServerUrl()`
3. **Search and replace** all occurrences of `132.73.167.231` with `Config.getServerUrl()`
4. **Test your app** - it should work exactly the same but be much more maintainable!

This solution will make your Android app much more professional and maintainable! 🎉
