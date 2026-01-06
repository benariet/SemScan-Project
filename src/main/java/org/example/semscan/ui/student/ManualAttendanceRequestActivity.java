package org.example.semscan.ui.student;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.text.TextUtils;
import android.text.format.DateFormat;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.function.Consumer;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import org.example.semscan.R;
import org.example.semscan.constants.ApiConstants;
import org.example.semscan.data.api.ApiClient;
import org.example.semscan.data.api.ApiService;
import org.example.semscan.data.model.Session;
import org.example.semscan.data.model.ManualAttendanceResponse;
import org.example.semscan.utils.ErrorMessageHelper;
import org.example.semscan.utils.Logger;
import org.example.semscan.utils.PreferencesManager;
import org.example.semscan.utils.ServerLogger;
import org.example.semscan.utils.ToastUtils;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import android.view.View;

public class ManualAttendanceRequestActivity extends AppCompatActivity {

    private PreferencesManager preferencesManager;
    private ApiService apiService;
    private ServerLogger serverLogger;
    private Long currentSessionId;

    private EditText editReason;
    private Button btnSubmitRequest;
    private ProgressBar progressLoading;
    private TextView textStatus;
    private View formContainer;
    private final List<Session> availableSessions = new ArrayList<>();
    private RecyclerView recyclerSessions;
    private SessionsAdapter sessionsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual_attendance_request);

        Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "ManualAttendanceRequestActivity created");

        preferencesManager = PreferencesManager.getInstance(this);
        ApiClient apiClient = ApiClient.getInstance(this);
        apiService = apiClient.getApiService();
        serverLogger = ServerLogger.getInstance(this);
        
        // Update user context for student logging
        String username = preferencesManager.getUserName();
        String userRole = preferencesManager.getUserRole();
        serverLogger.updateUserContext(username, userRole);

        // Test logging to verify student context
        serverLogger.i(ServerLogger.TAG_MANUAL_ATTENDANCE, "ManualAttendanceRequestActivity created - User: " + username + ", Role: " + userRole);

        // Check if user is actually a participant
        if (!preferencesManager.isParticipant()) {
            Logger.w(Logger.TAG_MANUAL_ATTENDANCE, "User is not a participant, finishing activity");
            finish();
            return;
        }

        setupToolbar();
        initializeViews();
        checkForActiveSessions();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Manual Attendance Request");
        }
    }

    private void initializeViews() {
        editReason = findViewById(R.id.edit_reason);
        btnSubmitRequest = findViewById(R.id.btn_submit_request);
        progressLoading = findViewById(R.id.progress_loading);
        textStatus = findViewById(R.id.text_status);
        formContainer = findViewById(R.id.container_form);
        recyclerSessions = findViewById(R.id.recycler_sessions);
        recyclerSessions.setLayoutManager(new LinearLayoutManager(this));
        sessionsAdapter = new SessionsAdapter(session -> currentSessionId = session.getSessionId());
        recyclerSessions.setAdapter(sessionsAdapter);

        btnSubmitRequest.setOnClickListener(v -> {
            if (validateInput()) {
                String reason = editReason.getText().toString().trim();
                // Truncate to 200 characters if longer (safety measure, maxLength in XML should prevent this)
                if (reason.length() > 200) {
                    reason = reason.substring(0, 200);
                    Logger.w(Logger.TAG_MANUAL_ATTENDANCE, "Reason truncated from " + editReason.getText().toString().trim().length() + " to 200 characters");
                }
                submitManualRequest(reason);
            }
        });
    }

    private void checkForActiveSessions() {
        Logger.userAction("Check Active Sessions", "Checking for active sessions for manual attendance");
        
        // No authentication required
        Logger.i("ManualAttendance", "Checking for active sessions (no authentication required)");
        
        if (serverLogger != null) {
            serverLogger.i(ServerLogger.TAG_MANUAL_ATTENDANCE, "REQUESTING OPEN SESSIONS - Endpoint: GET /api/v1/sessions/open - Expected: Backend should return ALL open sessions");
        }
        
        Call<List<Session>> call = apiService.getOpenSessions();
        call.enqueue(new Callback<List<Session>>() {
            @Override
            public void onResponse(Call<List<Session>> call, Response<List<Session>> response) {
                Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "═══════════════════════════════════════════════════════════");
                Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "OPEN SESSIONS API RESPONSE");
                Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "═══════════════════════════════════════════════════════════");
                Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "Status Code: " + response.code());
                Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "Is Successful: " + response.isSuccessful());
                Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "Has Body: " + (response.body() != null));
                
                if (serverLogger != null) {
                    serverLogger.i(ServerLogger.TAG_MANUAL_ATTENDANCE, "OPEN SESSIONS API RESPONSE - Status Code: " + response.code() + 
                        ", Is Successful: " + response.isSuccessful() + ", Has Body: " + (response.body() != null));
                }
                
                if (response.isSuccessful() && response.body() != null) {
                    List<Session> allSessions = response.body();
                    Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "Total sessions returned by backend: " + allSessions.size());
                    
                    if (allSessions.isEmpty()) {
                        Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "═══════════════════════════════════════════════════════════");
                        Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "🚨 BACKEND BUG DETECTED - EMPTY SESSIONS ARRAY");
                        Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "═══════════════════════════════════════════════════════════");
                        Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "Backend returned EMPTY array []");
                        Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "But presenter has an OPEN session visible!");
                        Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "");
                        Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "⚠️  ROOT CAUSE:");
                        Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "   Backend GET /api/v1/sessions/open is NOT returning");
                        Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "   all open sessions. Possible issues:");
                        Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "   1. Using DISTINCT/GROUP BY on slot_id (only one per slot)");
                        Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "   2. Filtering incorrectly (checking wrong status)");
                        Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "   3. Not checking for second session in same slot");
                        Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "   4. Query may be filtering by presenter instead of status");
                        Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "");
                        Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "   See: docs/BACKEND_FIX_MULTIPLE_PRESENTERS_SESSION.md");
                        Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "═══════════════════════════════════════════════════════════");
                        
                        if (serverLogger != null) {
                            String bugMessage = "🚨 BACKEND BUG DETECTED - EMPTY SESSIONS ARRAY | " +
                                "Backend returned EMPTY array [] but presenter has an OPEN session visible! | " +
                                "ROOT CAUSE: Backend GET /api/v1/sessions/open is NOT returning all open sessions. " +
                                "Possible issues: 1) Using DISTINCT/GROUP BY on slot_id (only one per slot), " +
                                "2) Filtering incorrectly (checking wrong status), " +
                                "3) Not checking for second session in same slot, " +
                                "4) Query may be filtering by presenter instead of status. " +
                                "See: docs/BACKEND_FIX_MULTIPLE_PRESENTERS_SESSION.md";
                            serverLogger.e(ServerLogger.TAG_MANUAL_ATTENDANCE, bugMessage);
                            serverLogger.flushLogs(); // Force send critical error logs immediately
                        }
                    } else {
                        if (serverLogger != null) {
                            serverLogger.i(ServerLogger.TAG_MANUAL_ATTENDANCE, "Total sessions returned by backend: " + allSessions.size());
                        }
                    }
                    
                    // Filter to show only OPEN sessions (backend might return closed sessions)
                    List<Session> openSessions = new java.util.ArrayList<>();
                    StringBuilder sessionDetails = new StringBuilder();
                    for (Session session : allSessions) {
                        if (session != null && session.getStatus() != null) {
                            String status = session.getStatus().toUpperCase();
                            Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "Session ID: " + session.getSessionId() + ", Status: " + status);
                            if ("OPEN".equals(status)) {
                                openSessions.add(session);
                                Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "  ✓ Added to open sessions list");
                                if (sessionDetails.length() > 0) sessionDetails.append(" | ");
                                sessionDetails.append("Session ").append(session.getSessionId()).append(": OPEN");
                            } else {
                                Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "  ✗ Filtered out (status: " + session.getStatus() + ")");
                                if (sessionDetails.length() > 0) sessionDetails.append(" | ");
                                sessionDetails.append("Session ").append(session.getSessionId()).append(": ").append(session.getStatus());
                            }
                        } else {
                            Logger.w(Logger.TAG_MANUAL_ATTENDANCE, "  ⚠ Invalid session (null or missing status): " + session);
                            if (sessionDetails.length() > 0) sessionDetails.append(" | ");
                            sessionDetails.append("Invalid session: ").append(session);
                        }
                    }
                    
                    Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "Open sessions after filtering: " + openSessions.size());
                    Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "═══════════════════════════════════════════════════════════");
                    
                    if (serverLogger != null) {
                        String sessionSummary = "Open sessions after filtering: " + openSessions.size() + 
                            (sessionDetails.length() > 0 ? " | Details: " + sessionDetails.toString() : "");
                        serverLogger.i(ServerLogger.TAG_MANUAL_ATTENDANCE, sessionSummary);
                    }
                    
                    Logger.session("Sessions Retrieved", "Found " + openSessions.size() + " open sessions (filtered from " + allSessions.size() + " total)");
                    
                    // Filter to show only recent sessions (last 10) to avoid overwhelming the user
                    if (openSessions.size() > 10) {
                        openSessions = openSessions.subList(0, 10);
                        Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "Filtered sessions to show only first 10 of " + openSessions.size() + " open sessions");
                    }
                    
                    handleOpenSessions(openSessions);
                } else {
                    Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "═══════════════════════════════════════════════════════════");
                    Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "❌ API ERROR - Failed to get open sessions");
                    Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "═══════════════════════════════════════════════════════════");
                    
                    if (serverLogger != null) {
                        serverLogger.e(ServerLogger.TAG_MANUAL_ATTENDANCE, "❌ API ERROR - Failed to get open sessions | " +
                            "Status Code: " + response.code() + ", Has Body: " + (response.body() != null));
                    }
                    
                    Logger.apiError("GET", "api/v1/sessions/open", response.code(), "Failed to get open sessions");
                    showError("Failed to get active sessions");
                    progressLoading.setVisibility(View.GONE);
                    textStatus.setVisibility(View.VISIBLE);
                    textStatus.setText("Could not load sessions. Please try again later.");
                }
            }
            
            @Override
            public void onFailure(Call<List<Session>> call, Throwable t) {
                Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "Failed to check active sessions", t);
                
                if (serverLogger != null) {
                    serverLogger.e(ServerLogger.TAG_MANUAL_ATTENDANCE, "❌ NETWORK FAILURE - Failed to get open sessions | " +
                        "Exception: " + t.getClass().getSimpleName() + ", Message: " + t.getMessage(), t);
                }
                
                String errorMessage = getString(R.string.error_load_failed);
                if (t instanceof java.net.SocketTimeoutException || t instanceof java.net.ConnectException) {
                    errorMessage = getString(R.string.error_network_timeout);
                } else if (t instanceof java.net.UnknownHostException) {
                    errorMessage = getString(R.string.error_server_unavailable);
                }
                showError(errorMessage);
                progressLoading.setVisibility(View.GONE);
                textStatus.setVisibility(View.VISIBLE);
                textStatus.setText(errorMessage);
            }
        });
    }

    private void handleOpenSessions(List<Session> sessions) {
        availableSessions.clear();
        availableSessions.addAll(sessions);

        if (sessions.isEmpty()) {
            textStatus.setVisibility(View.VISIBLE);
            textStatus.setText("No active sessions available right now.");
            btnSubmitRequest.setEnabled(false);
            progressLoading.setVisibility(View.GONE);
            formContainer.setVisibility(View.GONE);
            currentSessionId = null; // Clear session ID when no sessions available
        } else {
            sessionsAdapter.setSessions(sessions);
            // currentSessionId is automatically set via the selection listener (line 109)
            // No need to set it here as it will be set when the adapter notifies selection
            progressLoading.setVisibility(View.GONE);
            textStatus.setVisibility(View.GONE);
            formContainer.setVisibility(View.VISIBLE);
            btnSubmitRequest.setEnabled(true);
        }
    }

    private boolean validateInput() {
        String reason = editReason.getText().toString().trim();
        if (reason.isEmpty()) {
            ToastUtils.showError(this, "Please provide a reason");
            editReason.requestFocus();
            return false;
        }

        return true;
    }

    private void submitManualRequest(String reason) {
        Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "═══════════════════════════════════════════════════════════");
        Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "MANUAL ATTENDANCE REQUEST - SUBMISSION STARTING");
        Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "═══════════════════════════════════════════════════════════");
        
        String studentUsername = preferencesManager.getUserName();
        if (TextUtils.isEmpty(studentUsername)) {
            Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "Cannot submit manual request - no student username");
            showError("Student username not found. Please check settings.");
            return;
        }
        
        // Get and validate selected session FIRST before any logging or processing
        Session selectedSession = sessionsAdapter.getSelectedSession();
        if (selectedSession == null) {
            Logger.w(Logger.TAG_MANUAL_ATTENDANCE, "No session selected when submitting manual request");
            ToastUtils.showError(this, "Please choose a session");
            return;
        }
        
        // Validate that the selected session is still in the available sessions list
        // This prevents using a stale session that might have been closed
        boolean sessionStillValid = false;
        for (Session session : availableSessions) {
            if (session.getSessionId() != null && session.getSessionId().equals(selectedSession.getSessionId())) {
                // Verify the session is still open
                if ("OPEN".equals(session.getStatus()) || "open".equalsIgnoreCase(session.getStatus())) {
                    sessionStillValid = true;
                    break;
                }
            }
        }
        
        if (!sessionStillValid) {
            Logger.w(Logger.TAG_MANUAL_ATTENDANCE, "Selected session is no longer valid - session may have been closed");
            ToastUtils.showError(this, "The selected session is no longer available. Please refresh and select a different session.");
            // Optionally refresh the session list
            checkForActiveSessions();
            return;
        }
        
        // Set currentSessionId from the validated selected session
        currentSessionId = selectedSession.getSessionId();
        
        Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "Session ID: " + currentSessionId);
        Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "Student Username: " + studentUsername);
        Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "Reason: " + reason);
        Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "Session Status: " + selectedSession.getStatus());
        Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "Session Topic: " + selectedSession.getTopic());
        Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "Session Presenter: " + selectedSession.getPresenterName());
        
        String deviceId = android.provider.Settings.Secure.getString(
            getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
        
        Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "Device ID: " + deviceId);
        
        // Log AFTER validation and setting currentSessionId
        Logger.attendance("Submitting Manual Request", "Session ID: " + currentSessionId + 
            ", Student username: " + studentUsername + ", Reason: " + reason);
        serverLogger.attendance("Submitting Manual Request", "Session ID: " + currentSessionId + 
            ", Student username: " + studentUsername + ", Reason: " + reason);
        
        String apiBaseUrl = ApiClient.getInstance(this).getCurrentBaseUrl();
        Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "API Base URL: " + apiBaseUrl);
        Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "Full Endpoint URL: " + apiBaseUrl + "/attendance/manual");
        
        Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "═══════════════════════════════════════════════════════════");
        Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "REQUEST DETAILS");
        Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "═══════════════════════════════════════════════════════════");
        Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "Method: POST");
        Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "Endpoint: /api/v1/attendance/manual");
        Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "Full URL: " + apiBaseUrl + "/attendance/manual");
        Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "Session ID: " + currentSessionId);
        Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "Student Username: '" + studentUsername + "'");
        Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "Reason: '" + reason + "'");
        Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "Device ID: " + deviceId);
        Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "═══════════════════════════════════════════════════════════");
        
        if (serverLogger != null) {
            serverLogger.i(ServerLogger.TAG_MANUAL_ATTENDANCE, "MANUAL ATTENDANCE REQUEST - SUBMISSION | Session ID: " + currentSessionId + 
                ", Student: " + studentUsername + ", Reason: " + reason);
        }
        
        Logger.api("POST", "api/v1/attendance/manual", 
            "Session ID: " + currentSessionId + ", Student username: " + studentUsername);
        serverLogger.api("POST", "api/v1/attendance/manual", 
            "Session ID: " + currentSessionId + ", Student username: " + studentUsername);
        
        ApiService.CreateManualRequestRequest request = new ApiService.CreateManualRequestRequest(
            currentSessionId, studentUsername, reason, deviceId);
        
        Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "Sending HTTP request to backend...");
        long requestStartTime = System.currentTimeMillis();
        
        Call<ManualAttendanceResponse> call = apiService.createManualRequest(request);
        call.enqueue(new Callback<ManualAttendanceResponse>() {
            @Override
            public void onResponse(Call<ManualAttendanceResponse> call, Response<ManualAttendanceResponse> response) {
                long requestDuration = System.currentTimeMillis() - requestStartTime;
                Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "═══════════════════════════════════════════════════════════");
                Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "RESPONSE RECEIVED");
                Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "═══════════════════════════════════════════════════════════");
                Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "Request Duration: " + requestDuration + "ms");
                Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "Response Code: " + response.code());
                Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "Response Message: " + response.message());
                Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "Is Successful: " + response.isSuccessful());
                Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "Has Body: " + (response.body() != null));
                Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "Has Error Body: " + (response.errorBody() != null));
                Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "Request URL: " + (call.request() != null ? call.request().url() : "NULL"));
                Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "═══════════════════════════════════════════════════════════");
                
                if (serverLogger != null) {
                    serverLogger.i(ServerLogger.TAG_MANUAL_ATTENDANCE, "RESPONSE RECEIVED | Duration: " + requestDuration + "ms, " +
                        "Status Code: " + response.code() + ", Is Successful: " + response.isSuccessful());
                }
                
                if (response.isSuccessful()) {
                    ManualAttendanceResponse attendance = response.body();
                    if (attendance != null) {
                        Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "═══════════════════════════════════════════════════════════");
                        Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "✅ SUCCESS - MANUAL REQUEST SUBMITTED");
                        Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "═══════════════════════════════════════════════════════════");
                        Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "Session ID: " + attendance.getSessionId() + " (Expected: " + currentSessionId + ", Match: " + (attendance.getSessionId() != null && attendance.getSessionId().equals(currentSessionId)) + ")");
                        Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "Student: " + attendance.getStudentUsername() + " (Expected: " + studentUsername + ", Match: " + (attendance.getStudentUsername() != null && attendance.getStudentUsername().equals(studentUsername)) + ")");
                        Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "Response Object: " + attendance.toString());
                        Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "═══════════════════════════════════════════════════════════");
                        
                        Logger.attendance("Manual Request Submitted", "Student: " + studentUsername + 
                            ", Session: " + currentSessionId);
                        serverLogger.attendance("Manual Request Submitted", "Student: " + studentUsername + 
                            ", Session: " + currentSessionId);
                        Logger.apiResponse("POST", "api/v1/attendance/manual", 
                            response.code(), "Manual request submitted successfully");
                        serverLogger.apiResponse("POST", "api/v1/attendance/manual", 
                            response.code(), "Manual request submitted successfully");
                        serverLogger.flushLogs(); // Force send logs after successful submission
                        showSuccess("Manual attendance request submitted. Please wait for approval.");
                    } else {
                        Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "❌ ERROR: Response body is NULL");
                        Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "Response Code: " + response.code());
                        Logger.w(Logger.TAG_MANUAL_ATTENDANCE, "Invalid manual request response from server");
                        showError("Invalid response from server");
                    }
                } else {
                    Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "═══════════════════════════════════════════════════════════");
                    Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "❌ ERROR RESPONSE - MANUAL REQUEST FAILED");
                    Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "═══════════════════════════════════════════════════════════");
                    Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "Session ID Submitted: " + currentSessionId);
                    Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "Student Username: " + studentUsername);
                    Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "Response Code: " + response.code());
                    Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "Response Message: " + response.message());
                    Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "═══════════════════════════════════════════════════════════");
                    
                    Logger.apiError("POST", "api/v1/attendance/manual", 
                        response.code(), "Failed to submit manual request");
                    // Send server-side error as well
                    serverLogger.apiError("POST", "api/v1/attendance/manual", response.code(), "Failed to submit manual request");
                    
                    if (serverLogger != null) {
                        serverLogger.e(ServerLogger.TAG_MANUAL_ATTENDANCE, "❌ ERROR RESPONSE - MANUAL REQUEST FAILED | " +
                            "Session ID: " + currentSessionId + ", Student: " + studentUsername + ", Status Code: " + response.code());
                    }
                    
                    // Try to extract error message from response body
                    String errorBody = null;
                    if (response.errorBody() != null) {
                        try {
                            errorBody = response.errorBody().string();
                            Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "───────────────────────────────────────────────────────────");
                            Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "ERROR RESPONSE BODY");
                            Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "───────────────────────────────────────────────────────────");
                            Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "Full Error Body: " + errorBody);
                            Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "───────────────────────────────────────────────────────────");
                            
                            if (serverLogger != null) {
                                serverLogger.e(ServerLogger.TAG_MANUAL_ATTENDANCE, "ERROR RESPONSE BODY: " + errorBody);
                                
                                // Check for backend validation errors
                                if (errorBody.contains("Session is not open") || 
                                    errorBody.contains("status: CLOSED") || 
                                    errorBody.contains("not open") ||
                                    errorBody.contains("session is closed")) {
                                    Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "═══════════════════════════════════════════════════════════");
                                    Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "🚨 BACKEND VALIDATION ERROR DETECTED");
                                    Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "═══════════════════════════════════════════════════════════");
                                    Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "Session ID Submitted: " + currentSessionId);
                                    Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "Student Username: " + studentUsername);
                                    Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "Error: Session reported as 'not open' or 'CLOSED'");
                                    Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "⚠️  POSSIBLE BACKEND BUG:");
                                    Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "   Backend may be checking by slot_id instead of session_id");
                                    Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "   This prevents attendance to second session in same slot");
                                    Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "   See: docs/BACKEND_FIX_MULTIPLE_PRESENTERS_SESSION.md");
                                    Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "═══════════════════════════════════════════════════════════");
                                    
                                    serverLogger.e(ServerLogger.TAG_MANUAL_ATTENDANCE, "🚨 BACKEND VALIDATION ERROR DETECTED | " +
                                        "Session ID: " + currentSessionId + ", Student: " + studentUsername + 
                                        ", ⚠️ POSSIBLE BACKEND BUG: Checking wrong session (slot_id vs session_id)");
                                }
                            }
                        } catch (Exception e) {
                            Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "Failed to read error body", e);
                        }
                    }
                    
                    handleManualRequestError(response.code(), errorBody);
                }
            }
            
            @Override
            public void onFailure(Call<ManualAttendanceResponse> call, Throwable t) {
                long requestDuration = System.currentTimeMillis() - requestStartTime;
                Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "═══════════════════════════════════════════════════════════");
                Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "❌ NETWORK FAILURE - REQUEST DID NOT REACH SERVER");
                Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "═══════════════════════════════════════════════════════════");
                Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "Request Duration: " + requestDuration + "ms");
                Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "Exception Type: " + t.getClass().getName());
                Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "Exception Message: " + t.getMessage());
                Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "Request URL: " + (call.request() != null ? call.request().url() : "NULL"));
                Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "Session ID: " + currentSessionId);
                Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "Student Username: '" + studentUsername + "'");
                Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "Stack trace:", t);
                Logger.e(Logger.TAG_MANUAL_ATTENDANCE, "═══════════════════════════════════════════════════════════");
                
                // Forward to server error logger
                if (serverLogger != null) {
                    serverLogger.e(ServerLogger.TAG_MANUAL_ATTENDANCE, "❌ NETWORK FAILURE | Exception: " + t.getClass().getSimpleName() + 
                        ", Message: " + t.getMessage() + ", Session ID: " + currentSessionId + ", Student: " + studentUsername, t);
                    serverLogger.flushLogs();
                }
                
                String errorMessage = ErrorMessageHelper.getNetworkErrorMessage(
                    ManualAttendanceRequestActivity.this, t);
                showError(errorMessage);
            }
        });
    }

    private void handleManualRequestError(int responseCode, String errorBody) {
        String errorMessage = ErrorMessageHelper.getAttendanceErrorMessage(
            ManualAttendanceRequestActivity.this, responseCode, errorBody);
        
        // Auto-refresh if session is closed
        if (errorBody != null && (errorBody.contains("Session is not open") || 
            errorBody.contains("status: CLOSED") || errorBody.contains("not open") || 
            responseCode == 422)) {
            checkForActiveSessions();
        }
        
        showError(errorMessage);
    }

    private void showSuccess(String message) {
        ToastUtils.showSuccess(this, message);
        // Finish activity after showing success message
        finish();
    }

    private void showError(String message) {
        ToastUtils.showError(this, message);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Refresh session list when activity resumes to show newly opened sessions
        Logger.i(Logger.TAG_MANUAL_ATTENDANCE, "ManualAttendanceRequestActivity resumed - refreshing session list");
        checkForActiveSessions();
    }
    
    @Override
    protected void onDestroy() {
        if (serverLogger != null) {
            serverLogger.flushLogs();
        }
        super.onDestroy();
    }

    private static class SessionsAdapter extends RecyclerView.Adapter<SessionsAdapter.SessionViewHolder> {
        private final java.util.List<Session> sessions = new java.util.ArrayList<>();
        private int selectedPosition = 0;
        private final Consumer<Session> selectionListener;

        SessionsAdapter(Consumer<Session> selectionListener) {
            this.selectionListener = selectionListener;
        }

        void setSessions(List<Session> newSessions) {
            sessions.clear();
            sessions.addAll(newSessions);
            if (selectedPosition >= sessions.size()) {
                selectedPosition = 0;
            }
            notifyDataSetChanged();
            notifySelection();
        }

        Session getSelectedSession() {
            if (sessions.isEmpty() || selectedPosition < 0 || selectedPosition >= sessions.size()) {
                return null;
            }
            return sessions.get(selectedPosition);
        }

        Long getSelectedSessionId() {
            Session selected = getSelectedSession();
            return selected != null ? selected.getSessionId() : null;
        }

        @NonNull
        @Override
        public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_manual_session, parent, false);
            return new SessionViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
            Session session = sessions.get(position);
            holder.bind(session, position, position == selectedPosition, v -> select(position));
        }

        @Override
        public int getItemCount() {
            return sessions.size();
        }

        private void select(int position) {
            if (position < 0 || position >= sessions.size()) return;
            selectedPosition = position;
            notifyDataSetChanged();
            notifySelection();
        }

        private void notifySelection() {
            if (selectionListener != null) {
                Session selected = getSelectedSession();
                if (selected != null) {
                    selectionListener.accept(selected);
                }
            }
        }

        static class SessionViewHolder extends RecyclerView.ViewHolder {
            private final TextView textTitle;
            private final TextView textDate;
            private final TextView textPresenter;
            private final TextView textTopic;
            private final TextView textTimeRange;
            private final View layoutPresenter;
            private final View layoutTopic;
            private final TextView badgeStatus;

            SessionViewHolder(@NonNull View itemView) {
                super(itemView);
                textTitle = itemView.findViewById(R.id.text_session_title);
                textDate = itemView.findViewById(R.id.text_session_date);
                textPresenter = itemView.findViewById(R.id.text_session_presenter);
                textTopic = itemView.findViewById(R.id.text_session_topic);
                textTimeRange = itemView.findViewById(R.id.text_session_time_range);
                layoutPresenter = itemView.findViewById(R.id.layout_presenter);
                layoutTopic = itemView.findViewById(R.id.layout_topic);
                badgeStatus = itemView.findViewById(R.id.badge_session_status);
            }

            void bind(Session session, int position, boolean selected, View.OnClickListener clickListener) {
                // Format date and time nicely
                String dateStr = "Unknown date";
                String dayOfWeek = "";
                String timeStr = "Unknown time";
                String timeRangeStr = "";

                if (session.getStartTime() > 0) {
                    java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault());
                    java.text.SimpleDateFormat dayFormat = new java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault());
                    java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());

                    java.util.Date startDate = new java.util.Date(session.getStartTime());
                    dateStr = dateFormat.format(startDate);
                    dayOfWeek = dayFormat.format(startDate);
                    timeStr = timeFormat.format(startDate);

                    // Build time range if end time is available
                    if (session.getEndTime() != null && session.getEndTime() > 0) {
                        String endTimeStr = timeFormat.format(new java.util.Date(session.getEndTime()));
                        timeRangeStr = timeStr + " - " + endTimeStr;
                    } else {
                        timeRangeStr = timeStr;
                    }
                }

                // Set title - always "Seminar Session" (topic shown separately)
                textTitle.setText("Seminar Session");

                // Set presenter name
                if (session.getPresenterName() != null && !session.getPresenterName().trim().isEmpty()) {
                    textPresenter.setText(session.getPresenterName());
                    if (layoutPresenter != null) layoutPresenter.setVisibility(View.VISIBLE);
                } else {
                    textPresenter.setText("Unknown Presenter");
                    if (layoutPresenter != null) layoutPresenter.setVisibility(View.VISIBLE);
                }

                // Set topic
                if (textTopic != null) {
                    if (session.getTopic() != null && !session.getTopic().trim().isEmpty()) {
                        textTopic.setText(session.getTopic());
                        if (layoutTopic != null) layoutTopic.setVisibility(View.VISIBLE);
                    } else {
                        textTopic.setText("No topic specified");
                        if (layoutTopic != null) layoutTopic.setVisibility(View.VISIBLE);
                    }
                }

                // Set date with day of week
                StringBuilder dateBuilder = new StringBuilder();
                if (!dayOfWeek.isEmpty()) {
                    dateBuilder.append(dayOfWeek).append(", ");
                }
                dateBuilder.append(dateStr);
                textDate.setText(dateBuilder.toString());

                // Set time range
                textTimeRange.setText(timeRangeStr);

                // Set status badge
                String status = session.getStatus() != null ? session.getStatus() : "OPEN";
                badgeStatus.setText(status);
                badgeStatus.setBackground(ContextCompat.getDrawable(itemView.getContext(), R.drawable.bg_badge_open));

                // Set card selection state
                itemView.setOnClickListener(clickListener);
                itemView.setSelected(selected);

                // Change card elevation/stroke based on selection
                if (itemView instanceof com.google.android.material.card.MaterialCardView) {
                    com.google.android.material.card.MaterialCardView card = (com.google.android.material.card.MaterialCardView) itemView;
                    if (selected) {
                        card.setCardElevation(8f);
                        card.setStrokeWidth(3);
                        card.setStrokeColor(ContextCompat.getColor(itemView.getContext(), R.color.primary_blue));
                    } else {
                        card.setCardElevation(4f);
                        card.setStrokeWidth(1);
                        card.setStrokeColor(0xFFFFE0B2); // Light orange stroke
                    }
                }
            }
        }
    }
}
