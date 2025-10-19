# Android App Configuration Files for SemScan API

## 📱 **Required Android Configuration Files**

### **1. Network Security Configuration**

**File:** `app/src/main/res/xml/network_security_config.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">132.72.54.104</domain>
        <domain includeSubdomains="true">localhost</domain>
        <domain includeSubdomains="true">10.0.2.2</domain>
        <domain includeSubdomains="true">172.22.192.1</domain>
        <domain includeSubdomains="true">172.18.160.1</domain>
    </domain-config>
</network-security-config>
```

### **2. AndroidManifest.xml Updates**

**File:** `app/src/main/AndroidManifest.xml`

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    
    <!-- Internet permission -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.VIBRATE" />
    
    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/AppTheme"
        android:networkSecurityConfig="@xml/network_security_config"
        android:usesCleartextTraffic="true">
        
        <!-- Your activities here -->
        <activity
            android:name=".ui.qr.QRScannerActivity"
            android:exported="true"
            android:screenOrientation="portrait" />
            
        <activity
            android:name=".ui.teacher.ExportActivity"
            android:exported="true" />
        
    </application>
</manifest>
```

### **3. API Service Configuration**

**File:** `app/src/main/java/org/example/semscan/data/api/ApiService.java`

```java
package org.example.semscan.data.api;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import java.util.concurrent.TimeUnit;

public class ApiService {
    // Use your computer's IP address instead of localhost
    private static final String BASE_URL = "http://132.72.54.104:8080/";
    
    private static Retrofit retrofit;
    private static ApiInterface apiInterface;
    
    public static Retrofit getRetrofitInstance() {
        if (retrofit == null) {
            // Create logging interceptor
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);
            
            // Create OkHttp client
            OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
            
            retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        }
        return retrofit;
    }
    
    public static ApiInterface getApiInterface() {
        if (apiInterface == null) {
            apiInterface = getRetrofitInstance().create(ApiInterface.class);
        }
        return apiInterface;
    }
}
```

### **4. API Interface**

**File:** `app/src/main/java/org/example/semscan/data/api/ApiInterface.java`

```java
package org.example.semscan.data.api;

import org.example.semscan.data.model.*;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;

public interface ApiInterface {
    
    // =============================================
    // EXISTING ENDPOINTS
    // =============================================
    
    @GET("api/v1/seminars")
    Call<List<Seminar>> getSeminars(@Header("x-api-key") String apiKey);
    
    @POST("api/v1/seminars")
    Call<Seminar> createSeminar(@Header("x-api-key") String apiKey, @Body Seminar seminar);
    
    @GET("api/v1/sessions")
    Call<List<Session>> getSessions(@Header("x-api-key") String apiKey);
    
    @POST("api/v1/sessions")
    Call<Session> createSession(@Header("x-api-key") String apiKey, @Body Session session);
    
    @GET("api/v1/sessions/open")
    Call<List<Session>> getOpenSessions(@Header("x-api-key") String apiKey);
    
    @POST("api/v1/sessions/{id}/close")
    Call<Session> closeSession(@Path("id") String sessionId, @Header("x-api-key") String apiKey);
    
    @POST("api/v1/attendance")
    Call<Attendance> markAttendance(@Header("x-api-key") String apiKey, @Body Attendance attendance);
    
    @GET("api/v1/attendance/session/{sessionId}")
    Call<List<Attendance>> getAttendanceBySession(@Path("sessionId") String sessionId, @Header("x-api-key") String apiKey);
    
    @GET("api/v1/export/csv")
    Call<ResponseBody> exportCsv(@Query("sessionId") String sessionId, @Header("x-api-key") String apiKey);
    
    @GET("api/v1/export/xlsx")
    Call<ResponseBody> exportXlsx(@Query("sessionId") String sessionId, @Header("x-api-key") String apiKey);
    
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
    
    @GET("api/v1/attendance/pending-count")
    Call<Map<String, Long>> getPendingRequestCount(
        @Query("sessionId") String sessionId,
        @Header("x-api-key") String apiKey
    );
}
```

### **5. Data Models**

**File:** `app/src/main/java/org/example/semscan/data/model/ManualAttendanceRequest.java`

```java
package org.example.semscan.data.model;

public class ManualAttendanceRequest {
    private String sessionId;
    private String studentId;
    private String reason;
    private String deviceId;
    
    public ManualAttendanceRequest() {}
    
    public ManualAttendanceRequest(String sessionId, String studentId, String reason, String deviceId) {
        this.sessionId = sessionId;
        this.studentId = studentId;
        this.reason = reason;
        this.deviceId = deviceId;
    }
    
    // Getters and Setters
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
}
```

**File:** `app/src/main/java/org/example/semscan/data/model/ManualAttendanceResponse.java`

```java
package org.example.semscan.data.model;

public class ManualAttendanceResponse {
    private String attendanceId;
    private String sessionId;
    private String studentId;
    private String studentName;
    private String reason;
    private String requestStatus;
    private String requestedAt;
    private String autoFlags;
    private String message;
    
    public ManualAttendanceResponse() {}
    
    // Getters and Setters
    public String getAttendanceId() { return attendanceId; }
    public void setAttendanceId(String attendanceId) { this.attendanceId = attendanceId; }
    
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    
    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    
    public String getRequestStatus() { return requestStatus; }
    public void setRequestStatus(String requestStatus) { this.requestStatus = requestStatus; }
    
    public String getRequestedAt() { return requestedAt; }
    public void setRequestedAt(String requestedAt) { this.requestedAt = requestedAt; }
    
    public String getAutoFlags() { return autoFlags; }
    public void setAutoFlags(String autoFlags) { this.autoFlags = autoFlags; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
```

### **6. Updated Attendance Model**

**File:** `app/src/main/java/org/example/semscan/data/model/Attendance.java`

```java
package org.example.semscan.data.model;

public class Attendance {
    private String attendanceId;
    private String sessionId;
    private String studentId;
    private String attendanceTime;
    private String method;
    private String requestStatus;
    private String manualReason;
    private String requestedAt;
    private String approvedBy;
    private String approvedAt;
    private String deviceId;
    private String autoFlags;
    
    public enum AttendanceMethod {
        QR_SCAN, MANUAL, MANUAL_REQUEST, PROXY
    }
    
    public enum RequestStatus {
        CONFIRMED, PENDING_APPROVAL, REJECTED
    }
    
    // Constructors
    public Attendance() {}
    
    // Getters and Setters
    public String getAttendanceId() { return attendanceId; }
    public void setAttendanceId(String attendanceId) { this.attendanceId = attendanceId; }
    
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    
    public String getAttendanceTime() { return attendanceTime; }
    public void setAttendanceTime(String attendanceTime) { this.attendanceTime = attendanceTime; }
    
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    
    public String getRequestStatus() { return requestStatus; }
    public void setRequestStatus(String requestStatus) { this.requestStatus = requestStatus; }
    
    public String getManualReason() { return manualReason; }
    public void setManualReason(String manualReason) { this.manualReason = manualReason; }
    
    public String getRequestedAt() { return requestedAt; }
    public void setRequestedAt(String requestedAt) { this.requestedAt = requestedAt; }
    
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    
    public String getApprovedAt() { return approvedAt; }
    public void setApprovedAt(String approvedAt) { this.approvedAt = approvedAt; }
    
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    
    public String getAutoFlags() { return autoFlags; }
    public void setAutoFlags(String autoFlags) { this.autoFlags = autoFlags; }
}
```

### **7. Manual Attendance Service**

**File:** `app/src/main/java/org/example/semscan/service/ManualAttendanceService.java`

```java
package org.example.semscan.service;

import org.example.semscan.data.api.ApiInterface;
import org.example.semscan.data.api.ApiService;
import org.example.semscan.data.model.ManualAttendanceRequest;
import org.example.semscan.data.model.ManualAttendanceResponse;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ManualAttendanceService {
    
    private static final String API_KEY = "presenter-001-api-key-12345";
    private ApiInterface apiInterface;
    
    public ManualAttendanceService() {
        apiInterface = ApiService.getApiInterface();
    }
    
    public interface ManualAttendanceCallback {
        void onSuccess(ManualAttendanceResponse response);
        void onError(String error);
    }
    
    public interface PendingRequestsCallback {
        void onSuccess(List<ManualAttendanceResponse> requests);
        void onError(String error);
    }
    
    public interface PendingCountCallback {
        void onSuccess(long count);
        void onError(String error);
    }
    
    // Create manual attendance request
    public void createManualRequest(ManualAttendanceRequest request, ManualAttendanceCallback callback) {
        Call<ManualAttendanceResponse> call = apiInterface.createManualRequest(request);
        call.enqueue(new Callback<ManualAttendanceResponse>() {
            @Override
            public void onResponse(Call<ManualAttendanceResponse> call, Response<ManualAttendanceResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Failed to create manual request: " + response.message());
                }
            }
            
            @Override
            public void onFailure(Call<ManualAttendanceResponse> call, Throwable t) {
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }
    
    // Get pending requests
    public void getPendingRequests(String sessionId, PendingRequestsCallback callback) {
        Call<List<ManualAttendanceResponse>> call = apiInterface.getPendingRequests(sessionId, API_KEY);
        call.enqueue(new Callback<List<ManualAttendanceResponse>>() {
            @Override
            public void onResponse(Call<List<ManualAttendanceResponse>> call, Response<List<ManualAttendanceResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Failed to get pending requests: " + response.message());
                }
            }
            
            @Override
            public void onFailure(Call<List<ManualAttendanceResponse>> call, Throwable t) {
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }
    
    // Approve request
    public void approveRequest(String attendanceId, ManualAttendanceCallback callback) {
        Call<ManualAttendanceResponse> call = apiInterface.approveRequest(attendanceId, API_KEY);
        call.enqueue(new Callback<ManualAttendanceResponse>() {
            @Override
            public void onResponse(Call<ManualAttendanceResponse> call, Response<ManualAttendanceResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Failed to approve request: " + response.message());
                }
            }
            
            @Override
            public void onFailure(Call<ManualAttendanceResponse> call, Throwable t) {
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }
    
    // Reject request
    public void rejectRequest(String attendanceId, ManualAttendanceCallback callback) {
        Call<ManualAttendanceResponse> call = apiInterface.rejectRequest(attendanceId, API_KEY);
        call.enqueue(new Callback<ManualAttendanceResponse>() {
            @Override
            public void onResponse(Call<ManualAttendanceResponse> call, Response<ManualAttendanceResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Failed to reject request: " + response.message());
                }
            }
            
            @Override
            public void onFailure(Call<ManualAttendanceResponse> call, Throwable t) {
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }
    
    // Get pending request count
    public void getPendingRequestCount(String sessionId, PendingCountCallback callback) {
        Call<Map<String, Long>> call = apiInterface.getPendingRequestCount(sessionId, API_KEY);
        call.enqueue(new Callback<Map<String, Long>>() {
            @Override
            public void onResponse(Call<Map<String, Long>> call, Response<Map<String, Long>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    long count = response.body().getOrDefault("pendingCount", 0L);
                    callback.onSuccess(count);
                } else {
                    callback.onError("Failed to get pending count: " + response.message());
                }
            }
            
            @Override
            public void onFailure(Call<Map<String, Long>> call, Throwable t) {
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }
}
```

### **8. Build.gradle Dependencies**

**File:** `app/build.gradle`

```gradle
dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.9.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    
    // Network
    implementation 'com.squareup.retrofit2:retrofit:2.9.0'
    implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
    implementation 'com.squareup.okhttp3:logging-interceptor:4.11.0'
    
    // QR Code
    implementation 'com.journeyapps:zxing-android-embedded:4.3.0'
    implementation 'com.google.zxing:core:3.5.1'
    
    // JSON
    implementation 'com.google.code.gson:gson:2.10.1'
    
    // Testing
    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
}
```

## 🔧 **Setup Instructions**

### **Server Side:**
1. ✅ **Database schema is already updated** with manual attendance fields
2. ✅ **API endpoints are already implemented** and working
3. ✅ **Application is running** on `http://132.72.54.104:8080`

### **Android Side:**
1. **Create the network security config file** in `app/src/main/res/xml/`
2. **Update AndroidManifest.xml** with permissions and network security config
3. **Update your API base URL** to use `http://132.72.54.104:8080/`
4. **Add the new data models** (ManualAttendanceRequest, ManualAttendanceResponse)
5. **Update the Attendance model** with new fields
6. **Add the manual attendance endpoints** to your API interface
7. **Create the ManualAttendanceService** for handling API calls
8. **Update build.gradle** with required dependencies
9. **Rebuild and test** your Android app

## 🎯 **Key Points**

- **API Base URL:** `http://132.72.54.104:8080/`
- **API Key:** `presenter-001-api-key-12345`
- **Network Security:** Allow cleartext traffic for development
- **Manual Attendance:** 4 new endpoints for the complete workflow
- **Error Handling:** Proper error responses and logging

## ✅ **Current Status**

From the logs, I can see that:
- ✅ Your API is running successfully
- ✅ The Android app is connecting and making successful API calls
- ✅ Authentication is working (API key validation successful)
- ✅ The seminars endpoint is returning data (6 seminars retrieved)
- ✅ Manual attendance endpoints are implemented and ready

The main configuration needed is on the Android side to allow HTTP connections and implement the manual attendance workflow.
