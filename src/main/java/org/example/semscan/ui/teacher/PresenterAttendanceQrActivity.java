package org.example.semscan.ui.teacher;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.example.semscan.R;
import org.example.semscan.data.api.ApiClient;
import org.example.semscan.data.api.ApiService;
import org.example.semscan.data.model.ManualAttendanceResponse;
import org.example.semscan.utils.PreferencesManager;
import org.example.semscan.utils.Logger;
import org.example.semscan.utils.QRUtils;
import org.example.semscan.utils.ServerLogger;
import org.example.semscan.utils.ConfigManager;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.List;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Date;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PresenterAttendanceQrActivity extends AppCompatActivity {

    public static final String EXTRA_QR_URL = "presenter_attendance_qr_url";
    public static final String EXTRA_QR_PAYLOAD = "presenter_attendance_qr_payload";
    public static final String EXTRA_OPENED_AT = "presenter_attendance_opened_at";
    public static final String EXTRA_CLOSES_AT = "presenter_attendance_closes_at";
    public static final String EXTRA_SLOT_TITLE = "presenter_attendance_slot_title";
    public static final String EXTRA_SLOT_ID = "presenter_attendance_slot_id";
    public static final String EXTRA_USERNAME = "presenter_attendance_username";
    public static final String EXTRA_SESSION_ID = "presenter_attendance_session_id";

    // Result extras for passing back session close info
    public static final String RESULT_EXTRA_SESSION_CLOSED = "session_closed";
    public static final String RESULT_EXTRA_SESSION_CANCELED = "session_canceled";
    public static final String RESULT_EXTRA_OPENED_AT = "opened_at";
    public static final String RESULT_EXTRA_CLOSED_AT = "closed_at";
    public static final String RESULT_EXTRA_ATTENDEE_COUNT = "attendee_count";
    public static final int RESULT_CODE_SESSION_CLOSED = 100;
    public static final int RESULT_CODE_SESSION_CANCELED = 101;

    private static final long AUTO_CLOSE_CHECK_INTERVAL_MS = 30000L; // Check every 30 seconds
    // AUTO_CLOSE_DURATION_MS is now retrieved from ConfigManager (presenterCloseSessionDurationMinutes)

    private ImageView imageQr;
    private TextView textSlotTitle;
    private TextView textOpenedAt;
    private TextView textValidUntil;
    private ProgressBar progressRefresh;
    private com.google.android.material.button.MaterialButton btnEndSession;
    private com.google.android.material.button.MaterialButton btnCancelSession;

    private ApiService apiService;
    private ServerLogger serverLogger;
    private Handler autoCloseHandler;
    private Runnable autoCloseRunnable;

    private Long slotId;
    private Long sessionId;
    private String username;
    private String lastPayload;
    
    // Slot details for export filename
    private String slotDate;
    private String slotTimeRange;
    private String presenterName;
    
    // Auto-close tracking
    private long sessionOpenedAtMs; // Timestamp when session was opened (in milliseconds)
    private long sessionClosesAtMs; // Timestamp when session should close (in milliseconds)
    private boolean isAutoClosing = false; // Flag to prevent multiple auto-close attempts
    private String openedAtFormatted; // Formatted opened timestamp for display

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_presenter_attendance_qr);

        // Keep screen on so QR code remains visible for scanning
        // This prevents the screen from dimming or locking during the session
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        apiService = ApiClient.getInstance(this).getApiService();
        serverLogger = ServerLogger.getInstance(this);
        
        // Update user context for logging
        PreferencesManager preferencesManager = PreferencesManager.getInstance(this);
        String username = preferencesManager.getUserName();
        String userRole = preferencesManager.getUserRole();
        if (serverLogger != null) {
            serverLogger.updateUserContext(username, userRole);
        }
        
        autoCloseHandler = new Handler(Looper.getMainLooper());

        setupToolbar();
        initializeViews();
        readExtras();
        startAutoCloseTimer();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void initializeViews() {
        imageQr = findViewById(R.id.image_qr_code);
        textSlotTitle = findViewById(R.id.text_slot_title);
        textOpenedAt = findViewById(R.id.text_opened_at);
        textValidUntil = findViewById(R.id.text_valid_until);
        progressRefresh = findViewById(R.id.progress_refresh);
        btnEndSession = findViewById(R.id.btn_end_session);
        btnCancelSession = findViewById(R.id.btn_cancel_session);
        
        btnEndSession.setOnClickListener(v -> showEndSessionDialog());
        btnCancelSession.setOnClickListener(v -> showCancelSessionDialog());
    }

    private void readExtras() {
        Intent intent = getIntent();
        String qrUrl = intent.getStringExtra(EXTRA_QR_URL);
        String qrPayload = intent.getStringExtra(EXTRA_QR_PAYLOAD);
        String openedAt = intent.getStringExtra(EXTRA_OPENED_AT);
        String closesAt = intent.getStringExtra(EXTRA_CLOSES_AT);
        String slotTitle = intent.getStringExtra(EXTRA_SLOT_TITLE);
        slotId = (Long) intent.getSerializableExtra(EXTRA_SLOT_ID);
        sessionId = (Long) intent.getSerializableExtra(EXTRA_SESSION_ID);
        username = intent.getStringExtra(EXTRA_USERNAME);

        if (!TextUtils.isEmpty(slotTitle)) {
            textSlotTitle.setText(slotTitle);
            textSlotTitle.setVisibility(View.VISIBLE);
        }

        if (!TextUtils.isEmpty(openedAt)) {
            // Parse openedAt timestamp to calculate auto-close time
            sessionOpenedAtMs = parseTimestamp(openedAt);
            if (sessionOpenedAtMs <= 0) {
                // If parsing fails, use current time as fallback
                sessionOpenedAtMs = System.currentTimeMillis();
            }
            // Format for display in human-readable format
            openedAtFormatted = formatTimeHumanReadable(sessionOpenedAtMs);
            textOpenedAt.setText(getString(R.string.presenter_attendance_qr_opened_at, openedAtFormatted));
        } else {
            // If no openedAt provided, use current time
            sessionOpenedAtMs = System.currentTimeMillis();
            openedAtFormatted = formatTimeHumanReadable(sessionOpenedAtMs);
            textOpenedAt.setText(getString(R.string.presenter_attendance_qr_opened_at, openedAtFormatted));
        }
        if (!TextUtils.isEmpty(closesAt)) {
            // Parse closesAt timestamp to determine when to auto-close
            sessionClosesAtMs = parseTimestamp(closesAt);
            if (sessionClosesAtMs <= 0) {
                // If parsing fails, calculate from openedAt + configured duration
                long durationMs = getAutoCloseDurationMs();
                if (sessionOpenedAtMs > 0) {
                    sessionClosesAtMs = sessionOpenedAtMs + durationMs;
                } else {
                    sessionClosesAtMs = System.currentTimeMillis() + durationMs;
                }
            }
            // Format for display in human-readable format
            textValidUntil.setText(getString(R.string.presenter_attendance_qr_valid_until, formatTimeHumanReadable(sessionClosesAtMs)));
        } else {
            // If no closesAt provided, calculate from openedAt + configured duration
            long durationMs = getAutoCloseDurationMs();
            if (sessionOpenedAtMs > 0) {
                sessionClosesAtMs = sessionOpenedAtMs + durationMs;
            } else {
                sessionClosesAtMs = System.currentTimeMillis() + durationMs;
            }
            textValidUntil.setText(getString(R.string.presenter_attendance_qr_valid_until, formatTimeHumanReadable(sessionClosesAtMs)));
        }

        String normalizedContent = normalizeQrContent(!TextUtils.isEmpty(qrPayload) ? qrPayload : qrUrl);
        if (normalizedContent != null) {
            lastPayload = normalizedContent;
            generateQr(normalizedContent);
        }
    }
    
    /**
     * Parse timestamp string to milliseconds.
     * Supports formats like "2025-11-09 14:30:00" or ISO 8601 format.
     * Note: Timestamps without timezone are assumed to be in Israel timezone (Asia/Jerusalem) to match server time.
     */
    private long parseTimestamp(String timestamp) {
        if (TextUtils.isEmpty(timestamp)) {
            return 0;
        }
        try {
            // Server sends timestamps in Israel timezone (Asia/Jerusalem)
            java.util.TimeZone israelTz = java.util.TimeZone.getTimeZone("Asia/Jerusalem");

            // Try ISO 8601 format first (e.g., "2025-11-09T14:30:00")
            java.text.SimpleDateFormat isoFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US);
            isoFormat.setTimeZone(israelTz);
            try {
                String cleanedTimestamp = timestamp.replace("Z", "").replaceAll("\\+\\d{2}:\\d{2}$", "");
                return isoFormat.parse(cleanedTimestamp).getTime();
            } catch (Exception e) {
                // Try without timezone
            }

            // Try standard format (e.g., "2025-11-09 14:30:00")
            java.text.SimpleDateFormat standardFormat = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US);
            standardFormat.setTimeZone(israelTz);
            return standardFormat.parse(timestamp).getTime();
        } catch (Exception e) {
            Logger.w(Logger.TAG_UI, "Failed to parse timestamp: " + timestamp + " - " + e.getMessage());
            return 0;
        }
    }

    /**
     * Format timestamp to yyyy-MM-dd HH:mm format (e.g., "2025-12-17 13:24")
     */
    private String formatTime(long timestampMs) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestampMs));
    }

    /**
     * Format timestamp to human-readable format (e.g., "December 28, 2025 at 4:43 PM")
     */
    private String formatTimeHumanReadable(long timestampMs) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
        Date date = new Date(timestampMs);
        return dateFormat.format(date) + " at " + timeFormat.format(date);
    }


    private String normalizeQrContent(@Nullable String rawContent) {
        if (TextUtils.isEmpty(rawContent)) {
            return null;
        }
        if (QRUtils.isValidQRContent(rawContent)) {
            return rawContent;
        }
        try {
            Uri uri = Uri.parse(rawContent);
            String sessionQuery = uri.getQueryParameter("sessionId");
            Long parsed = parseSessionId(sessionQuery);
            if (parsed == null) {
                String lastSegment = uri.getLastPathSegment();
                parsed = parseSessionId(lastSegment);
            }
            if (parsed != null) {
                sessionId = parsed;
                return QRUtils.generateQRContent(parsed);
            }
        } catch (Exception ignored) {
        }
        if (sessionId != null) {
            return QRUtils.generateQRContent(sessionId);
        }
        return null;
    }

    private Long parseSessionId(@Nullable String value) {
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void generateQr(String content) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512);
            Bitmap bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565);
            for (int x = 0; x < 512; x++) {
                for (int y = 0; y < 512; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
            imageQr.setImageBitmap(bitmap);
        } catch (WriterException e) {
            Logger.e(Logger.TAG_UI, "Failed to generate session QR", e);
            Toast.makeText(this, R.string.presenter_start_session_error_load, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Get auto-close duration in milliseconds from ConfigManager
     */
    private long getAutoCloseDurationMs() {
        int minutes = ConfigManager.getInstance(this).getPresenterCloseSessionDurationMinutes();
        return minutes * 60 * 1000L; // Convert minutes to milliseconds
    }

    /**
     * Start the auto-close timer that checks if the session close time has been reached.
     * Uses the server's closesAt timestamp to avoid timezone issues.
     */
    private void startAutoCloseTimer() {
        if (sessionClosesAtMs <= 0) {
            // Fallback: if no close time, calculate from openedAt + configured duration
            if (sessionOpenedAtMs <= 0) {
                sessionOpenedAtMs = System.currentTimeMillis();
            }
            sessionClosesAtMs = sessionOpenedAtMs + getAutoCloseDurationMs();
        }
        
        autoCloseRunnable = new Runnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                
                // Check if current time has reached or passed the session close time
                if (currentTime >= sessionClosesAtMs && !isAutoClosing) {
                    // Session close time has been reached - auto-close the session
                    long durationMs = getAutoCloseDurationMs();
                    long elapsedTime = currentTime - (sessionClosesAtMs - durationMs);
                    Logger.i(Logger.TAG_UI, "Auto-closing session. Close time reached. Elapsed: " + (elapsedTime / 1000) + " seconds");
                    isAutoClosing = true;
                    
                    // Show a toast notification
                    runOnUiThread(() -> {
                        Toast.makeText(PresenterAttendanceQrActivity.this, 
                            getString(R.string.presenter_attendance_qr_auto_close_message), 
                            Toast.LENGTH_LONG).show();
                    });
                    
                    // Auto-close the session (but keep the QR page open)
                    closeSessionOnly(false);
                } else if (currentTime < sessionClosesAtMs) {
                    // Calculate time until close and schedule next check
                    long timeUntilClose = sessionClosesAtMs - currentTime;
                    long nextCheckDelay = Math.min(timeUntilClose, AUTO_CLOSE_CHECK_INTERVAL_MS);
                    autoCloseHandler.postDelayed(this, nextCheckDelay);
                }
            }
        };
        
        // Calculate initial delay - check at the close time or every 30 seconds, whichever comes first
        long currentTime = System.currentTimeMillis();
        long timeUntilClose = sessionClosesAtMs - currentTime;
        long initialDelay = Math.min(Math.max(timeUntilClose, 0), AUTO_CLOSE_CHECK_INTERVAL_MS);
        autoCloseHandler.postDelayed(autoCloseRunnable, initialDelay);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clear screen wake lock when activity is destroyed
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        if (autoCloseHandler != null && autoCloseRunnable != null) {
            autoCloseHandler.removeCallbacks(autoCloseRunnable);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showEndSessionDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.presenter_attendance_qr_end_confirm_title)
                .setMessage(R.string.presenter_attendance_qr_end_confirm_message)
                .setPositiveButton(R.string.presenter_attendance_qr_end_session, (dialog, which) -> endSession())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showCancelSessionDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.presenter_attendance_qr_cancel_confirm_title)
                .setMessage(R.string.presenter_attendance_qr_cancel_confirm_message)
                .setPositiveButton(R.string.presenter_attendance_qr_cancel_session, (dialog, which) -> cancelSession())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
    
    /**
     * Cancel session - closes the session and returns to home (does not navigate to export)
     * This is different from endSession() which navigates to export page
     */
    private void cancelSession() {
        if (sessionId == null) {
            Toast.makeText(this, R.string.presenter_attendance_qr_end_error, Toast.LENGTH_SHORT).show();
            finish(); // Still finish even if no session ID
            return;
        }

        btnEndSession.setEnabled(false);
        btnCancelSession.setEnabled(false);
        progressRefresh.setVisibility(View.VISIBLE);

        apiService.closeSession(sessionId).enqueue(new Callback<org.example.semscan.data.model.Session>() {
            @Override
            public void onResponse(Call<org.example.semscan.data.model.Session> call, Response<org.example.semscan.data.model.Session> response) {
                progressRefresh.setVisibility(View.GONE);
                btnEndSession.setEnabled(true);
                btnCancelSession.setEnabled(true);
                
                if (response.isSuccessful()) {
                    Toast.makeText(PresenterAttendanceQrActivity.this, R.string.presenter_attendance_qr_end_success, Toast.LENGTH_SHORT).show();
                    Logger.userAction("Cancel Session", "Session " + sessionId + " cancelled successfully");
                    // Set result to indicate session was canceled (not completed)
                    setSessionCanceledResult();
                    // For cancel, just go back to home - don't navigate to export
                    finish();
                } else {
                    Toast.makeText(PresenterAttendanceQrActivity.this, R.string.presenter_attendance_qr_end_error, Toast.LENGTH_SHORT).show();
                    Logger.apiError("PATCH", "/api/v1/sessions/" + sessionId + "/close", response.code(), "Failed to cancel session");
                    // Even on error, allow user to go back
                    finish();
                }
            }

            @Override
            public void onFailure(Call<org.example.semscan.data.model.Session> call, Throwable t) {
                progressRefresh.setVisibility(View.GONE);
                btnEndSession.setEnabled(true);
                btnCancelSession.setEnabled(true);
                Toast.makeText(PresenterAttendanceQrActivity.this, R.string.presenter_attendance_qr_end_error, Toast.LENGTH_SHORT).show();
                Logger.e(Logger.TAG_API, "Failed to cancel session", t);
                // Even on failure, allow user to go back
                finish();
            }
        });
    }

    /**
     * Close session. Checks for pending manual requests:
     * - If pending requests exist → navigate to Export page for approval
     * - If no pending requests → auto-upload and show success screen
     */
    private void closeSessionOnly(boolean navigateToExport) {
        if (sessionId == null) {
            Toast.makeText(this, R.string.presenter_attendance_qr_end_error, Toast.LENGTH_SHORT).show();
            return;
        }

        btnEndSession.setEnabled(false);
        btnCancelSession.setEnabled(false);
        progressRefresh.setVisibility(View.VISIBLE);

        apiService.closeSession(sessionId).enqueue(new Callback<org.example.semscan.data.model.Session>() {
            @Override
            public void onResponse(Call<org.example.semscan.data.model.Session> call, Response<org.example.semscan.data.model.Session> response) {
                if (response.isSuccessful()) {
                    // Log session close with timestamp
                    String sessionCloseTime = formatTime(System.currentTimeMillis());
                    String sessionCloseLog = String.format("Session closed at %s | Session ID: %s, Slot ID: %s",
                        sessionCloseTime, sessionId, slotId != null ? slotId : "unknown");
                    Logger.i(Logger.TAG_UI, sessionCloseLog);
                    if (serverLogger != null) {
                        serverLogger.i(ServerLogger.TAG_UI, sessionCloseLog);
                    }

                    Logger.userAction("End Session", "Session " + sessionId + " ended successfully");
                    Toast.makeText(PresenterAttendanceQrActivity.this,
                        R.string.presenter_attendance_qr_end_success, Toast.LENGTH_SHORT).show();

                    // Check for pending manual requests before deciding where to go
                    checkPendingRequestsAndProceed();
                } else {
                    progressRefresh.setVisibility(View.GONE);
                    btnEndSession.setEnabled(true);
                    btnCancelSession.setEnabled(true);
                    Toast.makeText(PresenterAttendanceQrActivity.this, R.string.presenter_attendance_qr_end_error, Toast.LENGTH_SHORT).show();
                    Logger.apiError("PATCH", "/api/v1/sessions/" + sessionId + "/close", response.code(), "Failed to end session");
                }
            }

            @Override
            public void onFailure(Call<org.example.semscan.data.model.Session> call, Throwable t) {
                progressRefresh.setVisibility(View.GONE);
                btnEndSession.setEnabled(true);
                btnCancelSession.setEnabled(true);
                Toast.makeText(PresenterAttendanceQrActivity.this, R.string.presenter_attendance_qr_end_error, Toast.LENGTH_SHORT).show();
                Logger.e(Logger.TAG_API, "Failed to end session", t);
            }
        });
    }

    /**
     * Check if there are pending manual requests. If yes, go to Export page. If no, auto-upload.
     */
    private void checkPendingRequestsAndProceed() {
        apiService.getPendingManualRequests(sessionId).enqueue(new Callback<List<ManualAttendanceResponse>>() {
            @Override
            public void onResponse(Call<List<ManualAttendanceResponse>> call,
                                   Response<List<ManualAttendanceResponse>> response) {
                progressRefresh.setVisibility(View.GONE);
                btnEndSession.setEnabled(true);
                btnCancelSession.setEnabled(true);

                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    // There are pending requests - go to Export page for approval
                    int pendingCount = response.body().size();
                    Logger.i(Logger.TAG_UI, "Found " + pendingCount + " pending manual requests - navigating to Export page");
                    Toast.makeText(PresenterAttendanceQrActivity.this,
                        "You have " + pendingCount + " pending attendance request(s) to review",
                        Toast.LENGTH_LONG).show();
                    fetchSlotDetailsForExport();
                } else {
                    // No pending requests - auto-upload and show success screen
                    Logger.i(Logger.TAG_UI, "No pending manual requests - auto-uploading export");
                    autoUploadAndFinish();
                }
            }

            @Override
            public void onFailure(Call<List<ManualAttendanceResponse>> call, Throwable t) {
                progressRefresh.setVisibility(View.GONE);
                btnEndSession.setEnabled(true);
                btnCancelSession.setEnabled(true);
                // On failure to check, just go to success screen
                Logger.w(Logger.TAG_API, "Failed to check pending requests, proceeding to success screen: " + t.getMessage());
                autoUploadAndFinish();
            }
        });
    }

    /**
     * Auto-upload export and finish with success result.
     * Used for both manual and auto close.
     */
    private void autoUploadAndFinish() {
        Logger.i(Logger.TAG_UI, "Auto-uploading export for session " + sessionId);

        apiService.uploadExport(sessionId, "xlsx").enqueue(new Callback<ApiService.UploadResponse>() {
            @Override
            public void onResponse(Call<ApiService.UploadResponse> call,
                                   Response<ApiService.UploadResponse> response) {
                if (response.isSuccessful() && response.body() != null && Boolean.TRUE.equals(response.body().success)) {
                    int attendeeCount = 0;
                    if (response.body().records != null && response.body().records > 0) {
                        attendeeCount = response.body().records;
                    }
                    Logger.i(Logger.TAG_UI, "Export uploaded successfully - " + attendeeCount + " records");
                    setSessionClosedResultWithCount(attendeeCount);
                    finish();
                } else {
                    // Export failed (e.g., 409 due to pending manual requests)
                    // Fetch actual attendance count from API
                    Logger.w(Logger.TAG_UI, "Export upload failed - " +
                        (response.body() != null ? response.body().message : "Unknown error") +
                        ". Fetching attendance count separately.");
                    fetchAttendanceCountAndFinish();
                }
            }

            @Override
            public void onFailure(Call<ApiService.UploadResponse> call, Throwable t) {
                Logger.e(Logger.TAG_UI, "Export upload network error", t);
                // Network error - still try to get attendance count
                fetchAttendanceCountAndFinish();
            }
        });
    }

    /**
     * Fetch actual attendance count for the session and finish with result.
     * Called when export fails to ensure we show correct attendee count.
     */
    private void fetchAttendanceCountAndFinish() {
        if (sessionId == null) {
            setSessionClosedResult();
            finish();
            return;
        }

        apiService.getAttendance(sessionId).enqueue(new Callback<List<org.example.semscan.data.model.Attendance>>() {
            @Override
            public void onResponse(Call<List<org.example.semscan.data.model.Attendance>> call,
                                   Response<List<org.example.semscan.data.model.Attendance>> response) {
                int attendeeCount = 0;
                if (response.isSuccessful() && response.body() != null) {
                    attendeeCount = response.body().size();
                    Logger.i(Logger.TAG_UI, "Fetched attendance count: " + attendeeCount + " records");
                } else {
                    Logger.w(Logger.TAG_UI, "Failed to fetch attendance count");
                }
                setSessionClosedResultWithCount(attendeeCount);
                finish();
            }

            @Override
            public void onFailure(Call<List<org.example.semscan.data.model.Attendance>> call, Throwable t) {
                Logger.e(Logger.TAG_UI, "Failed to fetch attendance count", t);
                setSessionClosedResult();
                finish();
            }
        });
    }
    
    /**
     * End session - closes the session and navigates to export page
     * Called when user manually clicks "End Session" button
     */
    private void endSession() {
        closeSessionOnly(true);
    }
    
    private void fetchSlotDetailsForExport() {
        if (slotId == null || TextUtils.isEmpty(username)) {
            // If we don't have slot details, navigate without them
            navigateToExport();
            return;
        }
        
        // Fetch presenter home to get slot details
        apiService.getPresenterHome(username.trim().toLowerCase(Locale.US))
                .enqueue(new Callback<ApiService.PresenterHomeResponse>() {
                    @Override
                    public void onResponse(Call<ApiService.PresenterHomeResponse> call, Response<ApiService.PresenterHomeResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            ApiService.PresenterHomeResponse body = response.body();
                            
                            // Get slot details from mySlot
                            if (body.mySlot != null) {
                                slotDate = body.mySlot.date;
                                slotTimeRange = body.mySlot.timeRange;
                                
                                // Get presenter name from presenter summary
                                if (body.presenter != null) {
                                    presenterName = body.presenter.name;
                                }
                            }
                            
                            // Also check slot catalog for this slot
                            if (body.slotCatalog != null) {
                                for (ApiService.SlotCard slot : body.slotCatalog) {
                                    if (slot.slotId != null && slot.slotId.equals(slotId)) {
                                        if (slotDate == null) slotDate = slot.date;
                                        if (slotTimeRange == null) slotTimeRange = slot.timeRange;
                                        
                                        // Get presenter name from registered presenters
                                        if (slot.registered != null && !slot.registered.isEmpty()) {
                                            // Use the first registered presenter's name
                                            ApiService.PresenterCoPresenter firstPresenter = slot.registered.get(0);
                                            if (firstPresenter != null && firstPresenter.name != null) {
                                                presenterName = firstPresenter.name;
                                            }
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                        
                        // Navigate to export with slot details
                        navigateToExport();
                    }
                    
                    @Override
                    public void onFailure(Call<ApiService.PresenterHomeResponse> call, Throwable t) {
                        Logger.e(Logger.TAG_API, "Failed to fetch slot details for export", t);
                        // Navigate anyway without slot details
                        navigateToExport();
                    }
                });
    }
    
    private void navigateToExport() {
        Intent intent = new Intent(PresenterAttendanceQrActivity.this, org.example.semscan.ui.teacher.ExportActivity.class);
        intent.putExtra("sessionId", sessionId);

        // Pass slot details for filename generation
        if (slotDate != null) {
            intent.putExtra("sessionDate", slotDate);
        }
        if (slotTimeRange != null && slotDate != null) {
            intent.putExtra("sessionTimeSlot", slotDate + " " + slotTimeRange); // Format: "2025-11-09 14:00-15:00"
        }
        if (presenterName != null) {
            intent.putExtra("sessionPresenter", presenterName);
        }

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Handle auto-close - just upload export and finish.
     */
    private void handleAutoCloseExport() {
        if (sessionId == null) {
            Logger.w(Logger.TAG_UI, "Cannot handle auto-close export - sessionId is null");
            setSessionClosedResult();
            finish();
            return;
        }

        Toast.makeText(this, R.string.presenter_attendance_qr_auto_close_message, Toast.LENGTH_SHORT).show();
        autoUploadAndFinish();
    }

    /**
     * Set result to indicate session was closed successfully
     */
    private void setSessionClosedResult() {
        setSessionClosedResultWithCount(-1); // -1 indicates unknown count
    }

    /**
     * Set result to indicate session was closed successfully with attendee count
     */
    private void setSessionClosedResultWithCount(int attendeeCount) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(RESULT_EXTRA_SESSION_CLOSED, true);
        resultIntent.putExtra(RESULT_EXTRA_OPENED_AT, openedAtFormatted != null ? openedAtFormatted : formatTimeHumanReadable(sessionOpenedAtMs));
        resultIntent.putExtra(RESULT_EXTRA_CLOSED_AT, formatTimeHumanReadable(System.currentTimeMillis()));
        resultIntent.putExtra(RESULT_EXTRA_ATTENDEE_COUNT, attendeeCount);
        setResult(RESULT_CODE_SESSION_CLOSED, resultIntent);
    }

    /**
     * Set result to indicate session was canceled (not completed)
     */
    private void setSessionCanceledResult() {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(RESULT_EXTRA_SESSION_CANCELED, true);
        resultIntent.putExtra(RESULT_EXTRA_OPENED_AT, openedAtFormatted != null ? openedAtFormatted : formatTimeHumanReadable(sessionOpenedAtMs));
        resultIntent.putExtra(RESULT_EXTRA_CLOSED_AT, formatTimeHumanReadable(System.currentTimeMillis()));
        setResult(RESULT_CODE_SESSION_CANCELED, resultIntent);
    }
}


