package org.example.semscan.data.api;

import com.google.gson.annotations.SerializedName;

import org.example.semscan.data.model.Attendance;
import org.example.semscan.data.model.ManualAttendanceResponse;
import org.example.semscan.data.model.Session;
import org.example.semscan.data.model.User;
import org.example.semscan.utils.ServerLogger;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {

    // =============================
    // Sessions
    // =============================

    @GET("api/v1/sessions/open")
    Call<List<Session>> getOpenSessions();

    @PATCH("api/v1/sessions/{sessionId}/close")
    Call<Session> closeSession(@Path("sessionId") Long sessionId);

    // =============================
    // Attendance
    // =============================

    @POST("api/v1/attendance")
    Call<Attendance> submitAttendance(@Body SubmitAttendanceRequest request);

    @GET("api/v1/attendance")
    Call<List<Attendance>> getAttendance(@Query("sessionId") Long sessionId);

    @GET("api/v1/attendance/has-attended")
    Call<Boolean> hasAttended(@Query("sessionId") Long sessionId,
                              @Query("studentUsername") String studentUsername);

    // =============================
    // Manual attendance workflow
    // =============================

    @POST("api/v1/attendance/manual")
    Call<ManualAttendanceResponse> createManualRequest(@Body CreateManualRequestRequest request);

    @GET("api/v1/attendance/manual/pending-requests")
    Call<List<ManualAttendanceResponse>> getPendingManualRequests(@Query("sessionId") Long sessionId);

    @POST("api/v1/attendance/manual/{attendanceId}/approve")
    Call<ManualAttendanceResponse> approveManualRequest(@Path("attendanceId") Long attendanceId,
                                                        @Query("approvedBy") String presenterUsername);

    @POST("api/v1/attendance/manual/{attendanceId}/reject")
    Call<ManualAttendanceResponse> rejectManualRequest(@Path("attendanceId") Long attendanceId,
                                                       @Query("approvedBy") String presenterUsername);

    // =============================
    // Presenter home + slot catalog
    // =============================

    @GET("api/v1/presenters/{username}/home")
    Call<PresenterHomeResponse> getPresenterHome(@Path("username") String username);

    @POST("api/v1/presenters/{username}/home/slots/{slotId}/register")
    Call<PresenterRegisterResponse> registerForSlot(
            @Path("username") String username,
            @Path("slotId") Long slotId,
            @Body PresenterRegisterRequest body
    );

    @DELETE("api/v1/presenters/{username}/home/slots/{slotId}/register")
    Call<Void> cancelSlotRegistration(
            @Path("username") String username,
            @Path("slotId") Long slotId
    );

    @POST("api/v1/presenters/{username}/home/slots/{slotId}/attendance/open")
    Call<PresenterAttendanceOpenResponse> openPresenterAttendance(
            @Path("username") String username,
            @Path("slotId") Long slotId
    );

    @GET("api/v1/presenters/{username}/home/slots/{slotId}/attendance/qr")
    Call<PresenterAttendanceOpenResponse> getPresenterAttendanceQr(
            @Path("username") String username,
            @Path("slotId") Long slotId
    );

    @GET("api/v1/slots")
    Call<List<SlotCard>> getPublicSlots();

    // =============================
    // Waiting List
    // =============================

    @POST("api/v1/slots/{slotId}/waiting-list")
    Call<WaitingListResponse> joinWaitingList(
            @Path("slotId") Long slotId,
            @Body WaitingListRequest request
    );

    @DELETE("api/v1/slots/{slotId}/waiting-list")
    Call<WaitingListResponse> leaveWaitingList(
            @Path("slotId") Long slotId,
            @Query("username") String username
    );

    // =============================
    // Logging
    // =============================

    @POST("api/v1/logs")
    Call<ServerLogger.LogResponse> sendLogs(@Body ServerLogger.LogRequest request);

    // =============================
    // Authentication
    // =============================

    @POST("api/v1/auth/login")
    Call<LoginResponse> login(@Body LoginRequest request);

    // =============================
    // User profile
    // =============================

    @POST("api/v1/users/exists")
    Call<UserExistsResponse> checkUserExists(@Body UserExistsRequest request);

    @GET("api/v1/users/username/{bguUsername}")
    Call<UserProfileResponse> getUserProfile(@Path("bguUsername") String bguUsername);

    @POST("api/v1/users")
    Call<UserProfileResponse> upsertUser(@Body UserProfileUpdateRequest request);

    // =============================
    // FCM Push Notifications
    // =============================

    @POST("api/v1/users/{username}/fcm-token")
    Call<Void> registerFcmToken(@Path("username") String username, @Body FcmTokenRequest request);

    @DELETE("api/v1/users/{username}/fcm-token")
    Call<Void> removeFcmToken(@Path("username") String username);

    // =============================
    // Test Email
    // =============================

    @POST("api/v1/mail/send")
    Call<TestEmailResponse> sendTestEmail(@Body TestEmailRequest request);

    // =============================
    // Export
    // =============================
    
    @GET("api/v1/export/xlsx")
    Call<ResponseBody> exportXlsx(@Query("sessionId") Long sessionId);
    
    @GET("api/v1/export/csv")
    Call<ResponseBody> exportCsv(@Query("sessionId") Long sessionId);
    
    /**
     * Trigger server-side export + upload flow.
     *
     * The backend will generate the export file and upload it to the configured upload server.
     * The mobile app only triggers this process and displays the result; it does not handle the file bytes.
     *
     * Note: The upload server URL is configured on the backend side. It should typically be
     * the same server (e.g., http://132.72.50.53:8080/api/v1/upload) or the backend's own endpoint.
     *
     * Example: POST /api/v1/export/upload?sessionId=123&format=csv
     */
    @POST("api/v1/export/upload")
    Call<UploadResponse> uploadExport(@Query("sessionId") Long sessionId,
                                      @Query("format") String format);

    // =============================
    // Request/Response DTOs
    // =============================

    class SubmitAttendanceRequest {
        public Long sessionId;
        public String studentUsername;
        public String method;   // QR_SCAN | MANUAL | MANUAL_REQUEST | PROXY
        public long timestampMs;

        public SubmitAttendanceRequest(Long sessionId, String studentUsername, long timestampMs) {
            this(sessionId, studentUsername, "QR_SCAN", timestampMs);
        }

        public SubmitAttendanceRequest(Long sessionId, String studentUsername, String method, long timestampMs) {
            this.sessionId = sessionId;
            this.studentUsername = studentUsername != null ? studentUsername.trim().toLowerCase() : null;
            this.method = method;
            this.timestampMs = timestampMs;
        }
    }

    class CreateManualRequestRequest {
        public Long sessionId;
        public String studentUsername;
        public String reason;
        public String deviceId;

        public CreateManualRequestRequest(Long sessionId, String studentUsername, String reason, String deviceId) {
            this.sessionId = sessionId;
            this.studentUsername = studentUsername != null ? studentUsername.trim().toLowerCase() : null;
            this.reason = reason;
            this.deviceId = deviceId;
        }
    }

    class PresenterHomeResponse {
        public PresenterSummary presenter;
        public MySlotSummary mySlot;
        public WaitingListSlotSummary myWaitingListSlot;
        public List<SlotCard> slotCatalog;
        public AttendancePanel attendance;
    }

    class PresenterSummary {
        public String bguUsername;
        public String name;
        public String degree;
        public boolean alreadyRegistered;
        public String currentCycleId;
    }

    class MySlotSummary {
        public Long slotId;
        public String semesterLabel;
        public String date;
        public String dayOfWeek;
        public String timeRange;
        public String room;
        public String building;
        public List<PresenterCoPresenter> coPresenters;
    }

    class WaitingListSlotSummary {
        public Long slotId;
        public String semesterLabel;
        public String date;
        public String dayOfWeek;
        public String timeRange;
        public String room;
        public String building;
        public int position;
        public int totalInQueue;
        public List<PresenterCoPresenter> registeredPresenters;
    }

    class PresenterCoPresenter {
        public String name;
        public String degree;
        public String topic;
    }

    class SlotCard {
        public Long slotId;
        public String semesterLabel;
        public String date;
        public String dayOfWeek;
        public String timeRange;
        public String room;
        public String building;
        public SlotState state;
        public int capacity;
        public int enrolledCount;
        public int availableCount;
        public boolean canRegister;
        public String disableReason;
        public boolean alreadyRegistered;
        public List<PresenterCoPresenter> registered;
        
        // Session status fields
        @SerializedName("attendanceOpenedAt")
        public String attendanceOpenedAt;
        
        @SerializedName("attendanceClosesAt")
        public String attendanceClosesAt;
        
        @SerializedName("hasClosedSession")
        public Boolean hasClosedSession; // True if slot has a closed attendance session
        
        // Approval status fields
        public int approvedCount;        // Number of approved registrations
        public int pendingCount;          // Number of pending approvals
        public String approvalStatus;     // Current user's approval status: "PENDING_APPROVAL", "APPROVED", null
        public boolean onWaitingList;     // Is current user on waiting list for this slot
        // IMPORTANT: Backend MUST return waitingListCount for ALL slots, not just when current user is on it
        // This field should show the total number of people on the waiting list, visible to everyone
        public int waitingListCount;      // Total number of people on waiting list (defaults to 0 if backend doesn't send it)
        public String waitingListUserName; // Name of user on waiting list (if current user is on it)
        public List<PresenterCoPresenter> pendingPresenters;  // List of pending registrations with names
        public List<PresenterCoPresenter> waitingListEntries; // List of waiting list entries with names
    }

    enum SlotState {
        FREE,
        SEMI,
        FULL
    }

    class AttendancePanel {
        public boolean canOpen;
        public String openQrUrl;
        public String status;
        public String warning;
        public String openedAt;
        public String closesAt;
        public boolean alreadyOpen;
        public Long sessionId;
        public String qrPayload;
    }

    class PresenterRegisterRequest {
        public String topic;
        public String seminarAbstract;
        public String supervisorName;
        public String supervisorEmail;
        public String presenterEmail; // Presenter's email for sending notification

        public PresenterRegisterRequest() {}

        public PresenterRegisterRequest(String topic, String seminarAbstract, String supervisorName, String supervisorEmail, String presenterEmail) {
            this.topic = topic;
            this.seminarAbstract = seminarAbstract;
            this.supervisorName = supervisorName;
            this.supervisorEmail = supervisorEmail;
            this.presenterEmail = presenterEmail;
        }
    }

    class PresenterRegisterResponse {
        public boolean ok;
        public String code;
        public String message;
        public Long registrationId;      // Registration ID for approval/decline links
        public String approvalToken;     // Token for approval/decline links
        public String approvalStatus;     // "PENDING_APPROVAL", "APPROVED", null
    }

    class PresenterAttendanceOpenResponse {
        public boolean success;
        public String message;
        public String code;
        public String qrUrl; // Legacy field - kept for backward compatibility
        public String closesAt;
        public String openedAt;
        public Long sessionId;
        public String qrPayload; // Legacy field - kept for backward compatibility
        public QrContent qrContent; // New nested structure
        public ServerInfo serverInfo;
        public Metadata metadata;
        
        // Helper method to get the recommended QR URL
        public String getQrUrl() {
            if (qrContent != null && qrContent.recommended != null) {
                return qrContent.recommended;
            }
            if (qrContent != null && qrContent.fullUrl != null) {
                return qrContent.fullUrl;
            }
            // Fallback to legacy field
            return qrUrl != null ? qrUrl : qrPayload;
        }
    }
    
    class QrContent {
        public String fullUrl;
        public String relativePath;
        public String sessionIdOnly;
        public String recommended;
    }
    
    class ServerInfo {
        public String serverUrl;
        public String apiBaseUrl;
        public String environment;
    }
    
    class Metadata {
        public String generatedAt;
        public String version;
        public String format;
        public String description;
    }
    
    class ErrorResponse {
        public String error;
        public String code;
    }

    class LoginRequest {
        public String username;
        public String password;

        public LoginRequest(String username, String password) {
            this.username = username != null ? username.trim().toLowerCase() : null;
            this.password = password;
        }
    }

    class LoginResponse {
        public boolean ok;
        public String message;
        public String bguUsername;
        public String email;
        public boolean isFirstTime;
        public boolean isPresenter;
        public boolean isParticipant;
    }

    class UserProfileResponse {
        public String bguUsername;
        public String email;
        public String firstName;
        public String lastName;
        public String degree;
        public String participationPreference;
        public String nationalIdNumber;
        public String seminarAbstract;
    }

    class UserExistsRequest {
        public String username;

        public UserExistsRequest(String username) {
            this.username = username != null ? username.trim() : null;
        }
    }

    class UserExistsResponse {
        public boolean exists;

        public UserExistsResponse(boolean exists) {
            this.exists = exists;
        }
    }

    class UserProfileUpdateRequest {
        public String bguUsername;
        public String email;
        public String firstName;
        public String lastName;
        public String degree;
        public String participationPreference;
        public String nationalIdNumber; // Optional - National ID number

        public UserProfileUpdateRequest(String bguUsername,
                                        String email,
                                        String firstName,
                                        String lastName,
                                        String degree,
                                        String participationPreference,
                                        String nationalIdNumber) {
            this.bguUsername = bguUsername == null ? null : bguUsername.trim().toLowerCase();
            this.email = email;
            this.firstName = firstName;
            this.lastName = lastName;
            this.degree = degree;
            this.participationPreference = participationPreference;
            this.nationalIdNumber = nationalIdNumber != null && !nationalIdNumber.trim().isEmpty() ? nationalIdNumber.trim() : null;
        }
    }

    class TestEmailRequest {
        public String to;
        public String subject;
        public String htmlContent;
        public String plainTextContent;
        // File attachment support (base64 encoded)
        public String attachmentFileName;
        public String attachmentContentType;
        public String attachmentBase64; // Base64 encoded file content

        public TestEmailRequest() {}

        public TestEmailRequest(String to, String subject, String htmlContent) {
            this.to = to;
            this.subject = subject;
            this.htmlContent = htmlContent;
        }
        
        public TestEmailRequest(String to, String subject, String htmlContent, 
                               String attachmentFileName, String attachmentContentType, String attachmentBase64) {
            this.to = to;
            this.subject = subject;
            this.htmlContent = htmlContent;
            this.attachmentFileName = attachmentFileName;
            this.attachmentContentType = attachmentContentType;
            this.attachmentBase64 = attachmentBase64;
        }
    }

    class TestEmailResponse {
        public boolean success;
        public String message;
        public String code;
    }
    
    /**
     * Response for export upload endpoint.
     *
     * Example JSON:
     * {
     *   "success": true,
     *   "message": "Export file uploaded successfully",
     *   "sessionId": 123,
     *   "format": "csv",
     *   "filename": "9_11_2025_john_doe_13-15.csv",
     *   "records": 25,
     *   "fileSize": 2048,
     *   "verified": true,
     *   "uploadUrl": "http://132.72.50.53:8080/api/v1/upload",
     *   "uploadResponse": { ... }
     * }
     * 
     * Note: The uploadUrl is returned by the backend and should point to the same server
     * (or the backend's own upload endpoint). The backend configuration determines where
     * files are actually uploaded.
     */
    class UploadResponse {
        public boolean success;
        public String message;
        public Long sessionId;
        public String format;
        public String filename;
        public Integer records;
        public Long fileSize;
        public Boolean verified;
        public String uploadUrl;
        public Object uploadResponse;
    }

    // =============================
    // Waiting List Models
    // =============================

    class WaitingListRequest {
        public String username;
        public String topic;
        public String supervisorName;
        public String supervisorEmail;

        public WaitingListRequest() {}

        public WaitingListRequest(String username) {
            this.username = username;
        }

        public WaitingListRequest(String username, String supervisorName, String supervisorEmail) {
            this.username = username;
            this.supervisorName = supervisorName;
            this.supervisorEmail = supervisorEmail;
        }

        public WaitingListRequest(String username, String topic, 
                                  String supervisorName, String supervisorEmail) {
            this.username = username;
            this.topic = topic;
            this.supervisorName = supervisorName;
            this.supervisorEmail = supervisorEmail;
        }
    }

    class WaitingListResponse {
        public boolean ok;
        public String message;
    }

    // =============================
    // FCM Token
    // =============================

    class FcmTokenRequest {
        public String fcmToken;
        public String deviceInfo;

        public FcmTokenRequest() {}

        public FcmTokenRequest(String fcmToken, String deviceInfo) {
            this.fcmToken = fcmToken;
            this.deviceInfo = deviceInfo;
        }
    }

    // =============================
    // Mobile Configuration
    // =============================

    @GET("api/v1/config/mobile")
    Call<MobileConfigResponse> getMobileConfig();

    /**
     * Mobile configuration response from backend
     * Contains all configurable values for the mobile app
     * Fetched from app_config table where target_system IN ('MOBILE', 'BOTH')
     */
    class MobileConfigResponse {
        public String serverUrl;
        public String exportEmailRecipients;
        public String supportEmail;
        public String emailDomain;
        public String testEmailRecipient;
        public int connectionTimeoutSeconds;
        public int readTimeoutSeconds;
        public int writeTimeoutSeconds;
        public int manualAttendanceWindowBeforeMinutes;
        public int manualAttendanceWindowAfterMinutes;
        public int maxExportFileSizeMb;
        public int toastDurationError;
        public int toastDurationSuccess;
        public int toastDurationInfo;
        public int presenterSlotOpenWindowBeforeMinutes;
        public int presenterSlotOpenWindowAfterMinutes;
        public int studentAttendanceWindowBeforeMinutes;
        public int studentAttendanceWindowAfterMinutes;
        public int waitingListApprovalWindowHours;
        public int presenterCloseSessionDurationMinutes;
        public String emailFromName;
        public String emailReplyTo;
        public String emailBccList;
        public String appVersion;
        public int waitingListLimitPerSlot;
        public int phdCapacityWeight;
    }

    // =============================
    // Announcements
    // =============================

    @GET("api/announcement")
    Call<AnnouncementResponse> getAnnouncement();

    /**
     * Announcement configuration response
     * Used to show popup messages to users after login
     */
    class AnnouncementResponse {
        @SerializedName("isActive")
        public boolean isActive;

        @SerializedName("version")
        public int version;

        @SerializedName("title")
        public String title;

        @SerializedName("message")
        public String message;

        @SerializedName("isBlocking")
        public boolean isBlocking;
    }
}
