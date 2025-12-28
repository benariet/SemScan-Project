package org.example.semscan.ui.teacher;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;

import org.example.semscan.R;
import org.example.semscan.data.api.ApiClient;
import org.example.semscan.data.api.ApiService;
import org.example.semscan.data.model.Session;
import org.example.semscan.utils.Logger;
import org.example.semscan.utils.PreferencesManager;
import org.example.semscan.utils.ServerLogger;

import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Date;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PresenterStartSessionActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_QR_ACTIVITY = 1001;

    private TextView textSlotTitle;
    private TextView textSlotWindow;
    private TextView textSlotLocation;
    private TextView textSessionStatus;
    private TextView textEmptyState;
    private View cardSlotDetails;
    private View cardSessionSuccess;
    private View cardSessionCanceled;
    private View layoutEmptyState;
    private View layoutBottomButton;
    private View layoutLocation;
    private TextView textSuccessOpenedAt;
    private TextView textSuccessClosedAt;
    private TextView textSuccessAttendees;
    private MaterialButton btnGoHome;
    private MaterialButton btnGoHomeCanceled;
    private ProgressBar progressBar;
    private MaterialButton btnStartSession;

    private PreferencesManager preferencesManager;
    private ApiService apiService;
    private ServerLogger serverLogger;

    private ApiService.MySlotSummary currentSlot;
    private ApiService.AttendancePanel currentAttendance;
    private String normalizedUsername;

    // Track if we should show the success/canceled state
    private boolean showSessionSuccess = false;
    private boolean showSessionCanceled = false;
    private String successOpenedAt;
    private String successClosedAt;
    private int successAttendeeCount = -1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_presenter_start_session);

        preferencesManager = PreferencesManager.getInstance(this);
        apiService = ApiClient.getInstance(this).getApiService();
        serverLogger = ServerLogger.getInstance(this);
        
        // Update user context for logging
        String username = preferencesManager.getUserName();
        String userRole = preferencesManager.getUserRole();
        if (serverLogger != null) {
            serverLogger.updateUserContext(username, userRole);
        }

        setupToolbar();
        initializeViews();
        setupInteractions();

        loadPresenterSlot();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void initializeViews() {
        textSlotTitle = findViewById(R.id.text_slot_title);
        textSlotWindow = findViewById(R.id.text_slot_window);
        textSlotLocation = findViewById(R.id.text_slot_location);
        textSessionStatus = findViewById(R.id.text_session_status);
        textEmptyState = findViewById(R.id.text_empty_state);
        cardSlotDetails = findViewById(R.id.card_slot_details);
        progressBar = findViewById(R.id.progress_bar);
        btnStartSession = findViewById(R.id.btn_start_session);

        // Layout views
        layoutEmptyState = findViewById(R.id.layout_empty_state);
        layoutBottomButton = findViewById(R.id.layout_bottom_button);
        layoutLocation = findViewById(R.id.layout_location);

        // Success card views
        cardSessionSuccess = findViewById(R.id.card_session_success);
        textSuccessOpenedAt = findViewById(R.id.text_success_opened_at);
        textSuccessClosedAt = findViewById(R.id.text_success_closed_at);
        textSuccessAttendees = findViewById(R.id.text_success_attendees);
        btnGoHome = findViewById(R.id.btn_go_home);

        // Canceled card views
        cardSessionCanceled = findViewById(R.id.card_session_canceled);
        btnGoHomeCanceled = findViewById(R.id.btn_go_home_canceled);
    }

    private void setupInteractions() {
        btnStartSession.setOnClickListener(v -> attemptOpenSession());
        btnGoHome.setOnClickListener(v -> finish());
        btnGoHomeCanceled.setOnClickListener(v -> finish());
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnStartSession.setEnabled(!loading && currentSlot != null);
    }

    private void loadPresenterSlot() {
        final String username = preferencesManager.getUserName();
        if (TextUtils.isEmpty(username)) {
            Toast.makeText(this, R.string.presenter_start_session_error_no_user, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        normalizedUsername = username.trim().toLowerCase(Locale.US);

        setLoading(true);
        apiService.getPresenterHome(normalizedUsername).enqueue(new Callback<ApiService.PresenterHomeResponse>() {
            @Override
            public void onResponse(Call<ApiService.PresenterHomeResponse> call, Response<ApiService.PresenterHomeResponse> response) {
                setLoading(false);
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(PresenterStartSessionActivity.this, R.string.presenter_start_session_error_load, Toast.LENGTH_LONG).show();
                    showEmptyState();
                    return;
                }
                currentSlot = response.body().mySlot;
                currentAttendance = response.body().attendance;
                renderSlot();
            }

            @Override
            public void onFailure(Call<ApiService.PresenterHomeResponse> call, Throwable t) {
                setLoading(false);
                Logger.e(Logger.TAG_API, "Failed to load presenter home", t);
                Toast.makeText(PresenterStartSessionActivity.this, R.string.presenter_start_session_error_load, Toast.LENGTH_LONG).show();
                showEmptyState();
            }
        });
    }

    private void renderSlot() {
        if (currentSlot == null || currentSlot.slotId == null) {
            showEmptyState();
            return;
        }

        // Check if we should show success state instead
        if (showSessionSuccess) {
            showSuccessState();
            return;
        }

        // Check if we should show canceled state instead
        if (showSessionCanceled) {
            showCanceledState();
            return;
        }

        cardSlotDetails.setVisibility(View.VISIBLE);
        cardSessionSuccess.setVisibility(View.GONE);
        cardSessionCanceled.setVisibility(View.GONE);
        layoutEmptyState.setVisibility(View.GONE);
        layoutBottomButton.setVisibility(View.VISIBLE);

        textSlotTitle.setText(getString(R.string.presenter_home_slot_title_format,
                safe(currentSlot.dayOfWeek), safe(currentSlot.date)));
        textSlotWindow.setText(safe(currentSlot.timeRange));

        String location = buildLocation();
        textSlotLocation.setText(location);
        layoutLocation.setVisibility(TextUtils.isEmpty(location) ? View.GONE : View.VISIBLE);

        if (currentAttendance != null && !TextUtils.isEmpty(currentAttendance.warning)) {
            textSessionStatus.setText(currentAttendance.warning);
            textSessionStatus.setVisibility(View.VISIBLE);
        } else if (currentAttendance != null && currentAttendance.alreadyOpen) {
            textSessionStatus.setText(R.string.presenter_start_session_status_open);
            textSessionStatus.setVisibility(View.VISIBLE);
        } else {
            textSessionStatus.setVisibility(View.GONE);
        }

        if (currentAttendance != null && currentAttendance.alreadyOpen) {
            btnStartSession.setText(R.string.presenter_start_session_button_resume);
        } else {
            btnStartSession.setText(R.string.presenter_start_session_button);
        }

        btnStartSession.setEnabled(true);
    }

    private void showSuccessState() {
        cardSlotDetails.setVisibility(View.GONE);
        cardSessionSuccess.setVisibility(View.VISIBLE);
        cardSessionCanceled.setVisibility(View.GONE);
        layoutEmptyState.setVisibility(View.GONE);
        layoutBottomButton.setVisibility(View.GONE);

        // Populate success card with session details
        if (successOpenedAt != null) {
            textSuccessOpenedAt.setText(successOpenedAt);
        } else {
            textSuccessOpenedAt.setText("-");
        }

        if (successClosedAt != null) {
            textSuccessClosedAt.setText(successClosedAt);
        } else {
            textSuccessClosedAt.setText("-");
        }

        if (successAttendeeCount >= 0) {
            textSuccessAttendees.setText(String.valueOf(successAttendeeCount));
        } else {
            textSuccessAttendees.setText("-");
        }
    }

    private void showCanceledState() {
        cardSlotDetails.setVisibility(View.GONE);
        cardSessionSuccess.setVisibility(View.GONE);
        cardSessionCanceled.setVisibility(View.VISIBLE);
        layoutEmptyState.setVisibility(View.GONE);
        layoutBottomButton.setVisibility(View.GONE);
    }

    private void showEmptyState() {
        cardSlotDetails.setVisibility(View.GONE);
        cardSessionSuccess.setVisibility(View.GONE);
        cardSessionCanceled.setVisibility(View.GONE);
        layoutEmptyState.setVisibility(View.VISIBLE);
        layoutBottomButton.setVisibility(View.GONE);
    }

    private void attemptOpenSession() {
        if (currentSlot == null || currentSlot.slotId == null) {
            Toast.makeText(this, R.string.presenter_start_session_empty, Toast.LENGTH_LONG).show();
            return;
        }

        // Frontend validation: Check if another presenter has an open session for this slot
        // This is a workaround for backend bug where it doesn't properly check for other presenters' sessions
        Logger.i(Logger.TAG_UI, "═══════════════════════════════════════════════════════════");
        Logger.i(Logger.TAG_UI, "ATTEMPTING TO OPEN SESSION");
        Logger.i(Logger.TAG_UI, "Presenter: " + normalizedUsername);
        Logger.i(Logger.TAG_UI, "Slot ID: " + currentSlot.slotId);
        Logger.i(Logger.TAG_UI, "Slot Time: " + currentSlot.timeRange);
        Logger.i(Logger.TAG_UI, "═══════════════════════════════════════════════════════════");
        
        if (serverLogger != null) {
            serverLogger.i(ServerLogger.TAG_UI, "ATTEMPTING TO OPEN SESSION | Presenter: " + normalizedUsername + 
                    ", Slot ID: " + currentSlot.slotId + ", Slot Time: " + currentSlot.timeRange);
        }

        // Check for other presenters' open sessions before opening
        checkForOtherPresentersSessions();
    }
    
    private void checkForOtherPresentersSessions() {
        // Query all open sessions to check if another presenter has an open session for this slot
        apiService.getOpenSessions().enqueue(new Callback<List<Session>>() {
            @Override
            public void onResponse(Call<List<Session>> call, Response<List<Session>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Session> openSessions = response.body();
                    String currentPresenterName = preferencesManager.getUserName();
                    
                    Logger.i(Logger.TAG_UI, "Checking " + openSessions.size() + " open sessions for conflicts");
                    
                    // Check if any open session has the same time range and different presenter
                    boolean foundConflict = false;
                    Session conflictingSession = null;
                    
                    for (Session session : openSessions) {
                        if (session == null || !"OPEN".equalsIgnoreCase(session.getStatus())) {
                            continue;
                        }
                        
                        // Check if session time matches slot time (same slot)
                        boolean timeMatches = false;
                        if (currentSlot.timeRange != null && session.getStartTime() > 0) {
                            // Parse slot time range (e.g., "00:01-23:59")
                            String[] slotTimes = currentSlot.timeRange.split("-");
                            if (slotTimes.length == 2) {
                                try {
                                    java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
                                    java.text.SimpleDateFormat dateTimeFormat = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());
                                    
                                    // Get session start time
                                    java.util.Date sessionDate = new java.util.Date(session.getStartTime());
                                    String sessionTime = timeFormat.format(sessionDate);
                                    
                                    // Check if session time is within slot time range
                                    String slotStart = slotTimes[0].trim();
                                    String slotEnd = slotTimes[1].trim();
                                    
                                    // Simple time comparison (HH:mm format)
                                    if (sessionTime.compareTo(slotStart) >= 0 && sessionTime.compareTo(slotEnd) <= 0) {
                                        timeMatches = true;
                                    }
                                } catch (Exception e) {
                                    Logger.w(Logger.TAG_UI, "Failed to parse time for conflict check: " + e.getMessage());
                                }
                            }
                        }
                        
                        // Check if presenter is different
                        boolean differentPresenter = false;
                        if (session.getPresenterName() != null && currentPresenterName != null) {
                            String sessionPresenter = session.getPresenterName().trim().toLowerCase();
                            String currentPresenter = currentPresenterName.trim().toLowerCase();
                            differentPresenter = !sessionPresenter.equals(currentPresenter);
                        }
                        
                        if (timeMatches && differentPresenter) {
                            foundConflict = true;
                            conflictingSession = session;
                            Logger.e(Logger.TAG_UI, "⚠️ CONFLICT DETECTED: Another presenter has an open session!");
                            Logger.e(Logger.TAG_UI, "  Conflicting Session ID: " + session.getSessionId());
                            Logger.e(Logger.TAG_UI, "  Conflicting Presenter: " + session.getPresenterName());
                            Logger.e(Logger.TAG_UI, "  Current Presenter: " + currentPresenterName);
                            Logger.e(Logger.TAG_UI, "  Slot ID: " + currentSlot.slotId);
                            
                            if (serverLogger != null) {
                                serverLogger.e(ServerLogger.TAG_UI, "🚨 FRONTEND VALIDATION: CONFLICT DETECTED | " +
                                    "Another presenter (" + session.getPresenterName() + ") has an OPEN session (ID: " + 
                                    session.getSessionId() + ") for the same slot (Slot ID: " + currentSlot.slotId + 
                                    "). Backend should return IN_PROGRESS but may not. Proceeding with backend call for validation.");
                            }
                            break;
                        }
                    }
                    
                    if (foundConflict && conflictingSession != null) {
                        // BLOCK: Another presenter has an open session - don't allow opening
                        final Session conflictSession = conflictingSession; // Make final for lambda
                        final String conflictPresenterName = conflictSession.getPresenterName() != null ? 
                            conflictSession.getPresenterName() : "Unknown";
                        final Long conflictSessionId = conflictSession.getSessionId();
                        
                        Logger.e(Logger.TAG_UI, "❌ BLOCKED: Frontend detected conflict - another presenter has open session");
                        if (serverLogger != null) {
                            serverLogger.e(ServerLogger.TAG_UI, "🚨 FRONTEND BLOCKED SESSION OPEN | " +
                                "Another presenter (" + conflictPresenterName + ") has an OPEN session (ID: " + 
                                conflictSessionId + ") for this slot. Backend should have returned IN_PROGRESS but didn't. " +
                                "Frontend is blocking to prevent duplicate sessions.");
                        }
                        
                        // Show error dialog and block
                        runOnUiThread(() -> {
                            showErrorDialog(
                                getString(R.string.presenter_start_session_error_in_progress),
                                getString(R.string.presenter_start_session_error_in_progress_message) + 
                                    "\n\nDetected open session by: " + conflictPresenterName + 
                                    " (Session ID: " + conflictSessionId + ")"
                            );
                        });
                        return; // Don't proceed to backend
                    }
                } else {
                    Logger.w(Logger.TAG_UI, "Failed to check for conflicting sessions: " + (response.code()));
                }
                
                // No conflict detected - proceed to backend call
                proceedWithOpenSession();
            }
            
            @Override
            public void onFailure(Call<List<Session>> call, Throwable t) {
                Logger.w(Logger.TAG_UI, "Failed to check for conflicting sessions, proceeding anyway: " + t.getMessage());
                // Proceed anyway - backend should validate
                proceedWithOpenSession();
            }
        });
    }
    
    private void proceedWithOpenSession() {
        // Always call the API to open/create session - don't rely on cached attendance data
        // because it might be from another presenter's session in the same slot.
        // The backend will return the correct state (OPENED, ALREADY_OPEN for this presenter, or IN_PROGRESS for another presenter)
        setLoading(true);
        
        Logger.i(Logger.TAG_UI, "Calling backend to open session: POST /api/v1/presenters/" + normalizedUsername + 
            "/home/slots/" + currentSlot.slotId + "/attendance/open");
        
        if (serverLogger != null) {
            serverLogger.i(ServerLogger.TAG_UI, "CALLING BACKEND: POST /api/v1/presenters/" + normalizedUsername + 
                "/home/slots/" + currentSlot.slotId + "/attendance/open | " +
                "Expected: Backend should return IN_PROGRESS if another presenter has open session");
        }
        
        apiService.openPresenterAttendance(normalizedUsername, currentSlot.slotId)
                .enqueue(new Callback<ApiService.PresenterAttendanceOpenResponse>() {
                    @Override
                    public void onResponse(Call<ApiService.PresenterAttendanceOpenResponse> call, Response<ApiService.PresenterAttendanceOpenResponse> response) {
                        setLoading(false);
                        if (!response.isSuccessful()) {
                            // Try to get error message from response body
                            String errorMessage = getString(R.string.presenter_start_session_error_generic_message);
                            String rawErrorMessage = null;
                            if (response.body() != null && response.body().message != null && !response.body().message.trim().isEmpty()) {
                                rawErrorMessage = response.body().message;
                                errorMessage = formatErrorMessage(rawErrorMessage);
                            } else if (response.errorBody() != null) {
                                try {
                                    String errorBodyString = response.errorBody().string();
                                    if (errorBodyString != null && !errorBodyString.trim().isEmpty()) {
                                        // Try to parse JSON and extract message field
                                        try {
                                            JsonParser parser = new JsonParser();
                                            JsonObject jsonObject = parser.parse(errorBodyString).getAsJsonObject();
                                            if (jsonObject.has("message") && jsonObject.get("message").isJsonPrimitive()) {
                                                rawErrorMessage = jsonObject.get("message").getAsString();
                                                errorMessage = formatErrorMessage(rawErrorMessage);
                                            }
                                            // Also check error field for database lock timeout
                                            if (jsonObject.has("error") && jsonObject.get("error").isJsonPrimitive()) {
                                                String errorField = jsonObject.get("error").getAsString();
                                                if (errorField != null && errorField.toLowerCase().contains("lock wait timeout")) {
                                                    rawErrorMessage = errorField;
                                                }
                                            }
                                        } catch (Exception e) {
                                            // If JSON parsing fails, try manual extraction
                                            int messageStart = errorBodyString.indexOf("\"message\":\"");
                                            if (messageStart >= 0) {
                                                messageStart += 11; // Length of "\"message\":\""
                                                int messageEnd = errorBodyString.indexOf("\"", messageStart);
                                                if (messageEnd > messageStart) {
                                                    rawErrorMessage = errorBodyString.substring(messageStart, messageEnd);
                                                    errorMessage = formatErrorMessage(rawErrorMessage);
                                                }
                                            }
                                            // Check for "error" field with lock timeout
                                            int errorStart = errorBodyString.indexOf("\"error\":\"");
                                            if (errorStart >= 0) {
                                                errorStart += 9; // Length of "\"error\":\""
                                                int errorEnd = errorBodyString.indexOf("\"", errorStart);
                                                if (errorEnd > errorStart) {
                                                    String errorField = errorBodyString.substring(errorStart, errorEnd);
                                                    if (errorField.toLowerCase().contains("lock wait timeout")) {
                                                        rawErrorMessage = errorField;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    Logger.e(Logger.TAG_API, "Failed to read error body", e);
                                }
                            }
                            
                            // Check if this is a database lock timeout error
                            boolean isLockTimeout = rawErrorMessage != null && 
                                (rawErrorMessage.toLowerCase().contains("lock wait timeout") ||
                                 rawErrorMessage.toLowerCase().contains("database lock") ||
                                 rawErrorMessage.toLowerCase().contains("deadlock"));
                            
                            if (isLockTimeout) {
                                // Log database lock timeout to app_logs
                                Logger.e(Logger.TAG_API, "🚨 DATABASE LOCK TIMEOUT DETECTED | Response Code: " + response.code() + 
                                    ", Error: " + rawErrorMessage);
                                if (serverLogger != null) {
                                    serverLogger.e(ServerLogger.TAG_API, "🚨 DATABASE LOCK TIMEOUT | " +
                                        "Response Code: " + response.code() + 
                                        ", Error Message: " + rawErrorMessage + 
                                        ", Presenter: " + normalizedUsername + 
                                        ", Slot ID: " + (currentSlot != null ? currentSlot.slotId : "unknown") +
                                        " | This indicates a database-level issue. Check for stuck transactions.");
                                    serverLogger.flushLogs(); // Force send critical error immediately
                                }
                                // Show user-friendly message
                                errorMessage = "Database lock timeout. Please try again in a few moments.";
                            }
                            
                            showErrorDialog(
                                    getString(R.string.presenter_start_session_error_generic),
                                    errorMessage
                            );
                            return;
                        }

                        if (response.body() == null) {
                            showErrorDialog(
                                    getString(R.string.presenter_start_session_error_generic),
                                    getString(R.string.presenter_start_session_error_generic_message)
                            );
                            return;
                        }

                        ApiService.PresenterAttendanceOpenResponse body = response.body();
                        String code = body.code != null ? body.code : "";
                        
                        Logger.i(Logger.TAG_UI, "═══════════════════════════════════════════════════════════");
                        Logger.i(Logger.TAG_UI, "BACKEND RESPONSE RECEIVED");
                        Logger.i(Logger.TAG_UI, "Response Code: " + code);
                        Logger.i(Logger.TAG_UI, "Session ID: " + body.sessionId);
                        Logger.i(Logger.TAG_UI, "Message: " + body.message);
                        Logger.i(Logger.TAG_UI, "═══════════════════════════════════════════════════════════");
                        
                        if (serverLogger != null) {
                            serverLogger.i(ServerLogger.TAG_UI, "BACKEND RESPONSE | Code: " + code + 
                                    ", Session ID: " + body.sessionId + ", Message: " + body.message);
                        }
                        
                        // CRITICAL: If backend returns OPENED when it should return IN_PROGRESS, log it
                        if ("OPENED".equals(code)) {
                            // Check if we detected a conflict earlier - if so, this is a backend bug
                            Logger.w(Logger.TAG_UI, "Backend returned OPENED - checking if this conflicts with another presenter's session");
                            if (serverLogger != null) {
                                serverLogger.w(ServerLogger.TAG_UI, "Backend returned OPENED for session " + body.sessionId + 
                                    ". If another presenter has an open session for this slot, this is a BACKEND BUG.");
                            }
                        }
                        
                        switch (code) {
                            case "OPENED":
                            case "ALREADY_OPEN":
                                // Log session start with timestamp
                                String sessionStartTime = formatTime(System.currentTimeMillis());
                                String sessionStartLog = String.format("Session started at %s | Session ID: %s, Slot ID: %s", 
                                    sessionStartTime, body.sessionId, currentSlot.slotId);
                                Logger.i(Logger.TAG_UI, sessionStartLog);
                                if (serverLogger != null) {
                                    serverLogger.i(ServerLogger.TAG_UI, sessionStartLog);
                                }
                                openAttendanceQr(body.qrUrl, body.qrPayload, body.openedAt, body.closesAt, body.sessionId);
                                break;
                            case "TOO_EARLY":
                                // Always show the server message if available, otherwise show default with time
                                String tooEarlyMessage;
                                if (body.message != null && !body.message.trim().isEmpty()) {
                                    // Check if message contains JSON and extract just the message text
                                    tooEarlyMessage = extractMessageFromJson(body.message);
                                } else {
                                    // Build default message with actual time
                                    String slotTime = "your scheduled time";
                                    if (currentSlot != null && currentSlot.timeRange != null) {
                                        String[] timeParts = currentSlot.timeRange.split("-");
                                        if (timeParts.length > 0) {
                                            slotTime = timeParts[0].trim();
                                        }
                                    }
                                    tooEarlyMessage = getString(R.string.presenter_start_session_error_too_early_message, slotTime);
                                }
                                showErrorDialog(
                                        getString(R.string.presenter_start_session_error_too_early),
                                        formatErrorMessage(tooEarlyMessage)
                                );
                                break;
                            case "IN_PROGRESS":
                                String inProgressMessage = (body.message != null && !body.message.trim().isEmpty()) ? 
                                        extractMessageFromJson(body.message) : getString(R.string.presenter_start_session_error_in_progress_message);
                                showErrorDialog(
                                        getString(R.string.presenter_start_session_error_in_progress),
                                        formatErrorMessage(inProgressMessage)
                                );
                                break;
                            default:
                                // Always show server message if available
                                String defaultMessage = (body.message != null && !body.message.trim().isEmpty()) ? 
                                        extractMessageFromJson(body.message) : getString(R.string.presenter_start_session_error_generic_message);
                                showErrorDialog(
                                        getString(R.string.presenter_start_session_error_generic),
                                        formatErrorMessage(defaultMessage)
                                );
                                break;
                        }
                        loadPresenterSlot();
                    }

                    @Override
                    public void onFailure(Call<ApiService.PresenterAttendanceOpenResponse> call, Throwable t) {
                        setLoading(false);
                        Logger.e(Logger.TAG_API, "Failed to open attendance", t);
                        showErrorDialog(
                                getString(R.string.presenter_start_session_error_network),
                                getString(R.string.presenter_start_session_error_network_message)
                        );
                    }
                });
    }

    private void openAttendanceQr(@Nullable String qrUrl,
                                  @Nullable String qrPayload,
                                  @Nullable String openedAt,
                                  @Nullable String closesAt,
                                  @Nullable Long sessionId) {
        Intent intent = new Intent(this, PresenterAttendanceQrActivity.class);
        intent.putExtra(PresenterAttendanceQrActivity.EXTRA_QR_URL, qrUrl);
        intent.putExtra(PresenterAttendanceQrActivity.EXTRA_QR_PAYLOAD, qrPayload);
        intent.putExtra(PresenterAttendanceQrActivity.EXTRA_OPENED_AT, openedAt);
        intent.putExtra(PresenterAttendanceQrActivity.EXTRA_CLOSES_AT, closesAt);
        intent.putExtra(PresenterAttendanceQrActivity.EXTRA_SLOT_TITLE, textSlotTitle.getText().toString());
        intent.putExtra(PresenterAttendanceQrActivity.EXTRA_SESSION_ID, sessionId);
        intent.putExtra(PresenterAttendanceQrActivity.EXTRA_SLOT_ID, currentSlot != null ? currentSlot.slotId : null);
        intent.putExtra(PresenterAttendanceQrActivity.EXTRA_USERNAME, normalizedUsername);
        startActivityForResult(intent, REQUEST_CODE_QR_ACTIVITY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_QR_ACTIVITY) {
            if (resultCode == PresenterAttendanceQrActivity.RESULT_CODE_SESSION_CLOSED) {
                if (data != null && data.getBooleanExtra(PresenterAttendanceQrActivity.RESULT_EXTRA_SESSION_CLOSED, false)) {
                    // Session was closed successfully - show success state
                    showSessionSuccess = true;
                    showSessionCanceled = false;
                    successOpenedAt = data.getStringExtra(PresenterAttendanceQrActivity.RESULT_EXTRA_OPENED_AT);
                    successClosedAt = data.getStringExtra(PresenterAttendanceQrActivity.RESULT_EXTRA_CLOSED_AT);
                    successAttendeeCount = data.getIntExtra(PresenterAttendanceQrActivity.RESULT_EXTRA_ATTENDEE_COUNT, -1);

                    Logger.i(Logger.TAG_UI, "Session closed successfully - showing success state. " +
                        "Opened: " + successOpenedAt + ", Closed: " + successClosedAt + ", Attendees: " + successAttendeeCount);

                    // Show success state
                    showSuccessState();
                }
            } else if (resultCode == PresenterAttendanceQrActivity.RESULT_CODE_SESSION_CANCELED) {
                if (data != null && data.getBooleanExtra(PresenterAttendanceQrActivity.RESULT_EXTRA_SESSION_CANCELED, false)) {
                    // Session was canceled - show canceled state
                    showSessionSuccess = false;
                    showSessionCanceled = true;

                    Logger.i(Logger.TAG_UI, "Session canceled - showing canceled state");

                    // Show canceled state
                    showCanceledState();
                }
            }
        }
    }

    private String buildLocation() {
        StringBuilder builder = new StringBuilder();
        if (!TextUtils.isEmpty(currentSlot.room)) {
            builder.append(getString(R.string.room_with_label, currentSlot.room));
        }
        if (!TextUtils.isEmpty(currentSlot.building)) {
            if (builder.length() > 0) builder.append(" • ");
            builder.append(getString(R.string.building_with_label, currentSlot.building));
        }
        return builder.toString();
    }

    private String extractMessageFromJson(String message) {
        if (message == null || message.trim().isEmpty()) {
            return message;
        }
        
        // If message looks like JSON, try to parse it
        if (message.trim().startsWith("{") && message.contains("\"message\"")) {
            try {
                JsonParser parser = new JsonParser();
                JsonObject jsonObject = parser.parse(message).getAsJsonObject();
                if (jsonObject.has("message") && jsonObject.get("message").isJsonPrimitive()) {
                    return formatErrorMessage(jsonObject.get("message").getAsString());
                }
            } catch (Exception e) {
                // If parsing fails, try manual extraction
                int messageStart = message.indexOf("\"message\":\"");
                if (messageStart >= 0) {
                    messageStart += 11; // Length of "\"message\":\""
                    int messageEnd = message.indexOf("\"", messageStart);
                    if (messageEnd > messageStart) {
                        return formatErrorMessage(message.substring(messageStart, messageEnd));
                    }
                }
            }
        }
        
        // Return message as-is if it's not JSON, but format it
        return formatErrorMessage(message);
    }
    
    /**
     * Formats error messages to make them more user-friendly.
     * Converts ISO 8601 datetime strings to readable format.
     * Example: "2025-11-16T15:00:00" -> "November 16, 2025 at 3:00 PM"
     */
    private String formatErrorMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return message;
        }
        
        // Pattern to match ISO 8601 datetime: YYYY-MM-DDTHH:mm:ss or YYYY-MM-DDTHH:mm:ss.SSS
        java.util.regex.Pattern isoPattern = java.util.regex.Pattern.compile(
            "(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{3})?)"
        );
        java.util.regex.Matcher matcher = isoPattern.matcher(message);
        
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String isoDateTime = matcher.group(1);
            String formattedDateTime = formatIsoDateTime(isoDateTime);
            matcher.appendReplacement(result, formattedDateTime);
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * Converts ISO 8601 datetime string to readable format.
     * Example: "2025-11-16T15:00:00" -> "November 16, 2025 at 3:00 PM"
     * Note: Server timestamps are in Israel timezone (Asia/Jerusalem)
     */
    private String formatIsoDateTime(String isoDateTime) {
        try {
            // Server sends timestamps in Israel timezone
            java.util.TimeZone israelTz = java.util.TimeZone.getTimeZone("Asia/Jerusalem");

            // Parse ISO 8601 format
            java.text.SimpleDateFormat isoFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US);
            isoFormat.setTimeZone(israelTz);
            // Try with milliseconds if needed
            if (isoDateTime.contains(".")) {
                isoFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", java.util.Locale.US);
                isoFormat.setTimeZone(israelTz);
            }

            java.util.Date date = isoFormat.parse(isoDateTime);

            // Format to readable date and time (display in device's local timezone)
            java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("MMMM d, yyyy", java.util.Locale.getDefault());
            java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault());

            String formattedDate = dateFormat.format(date);
            String formattedTime = timeFormat.format(date);

            return formattedDate + " at " + formattedTime;
        } catch (Exception e) {
            Logger.w(Logger.TAG_UI, "Failed to format datetime: " + isoDateTime + " - " + e.getMessage());
            return isoDateTime; // Return original if parsing fails
        }
    }

    private void showErrorDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .setCancelable(true)
                .show();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    /**
     * Format timestamp to yyyy-MM-dd HH:mm format (e.g., "2025-12-17 13:24")
     */
    private String formatTime(long timestampMs) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestampMs));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
