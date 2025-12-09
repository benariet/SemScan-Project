package org.example.semscan.ui.teacher;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.example.semscan.R;
import org.example.semscan.constants.ApiConstants;
import org.example.semscan.data.api.ApiClient;
import org.example.semscan.data.api.ApiService;
import org.example.semscan.utils.Logger;
import org.example.semscan.utils.PreferencesManager;
import org.example.semscan.utils.ServerLogger;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PresenterSlotSelectionActivity extends AppCompatActivity implements PresenterSlotsAdapter.SlotActionListener {

    public static final String EXTRA_SCROLL_TO_MY_SLOT = "presenter_slot_selection.extra_scroll_to_my_slot";

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerSlots;
    private View emptyState;
    private ProgressBar progressBar;
    private MaterialButton btnReload;

    private PresenterSlotsAdapter slotAdapter;
    private PreferencesManager preferencesManager;
    private ApiService apiService;
    private ServerLogger serverLogger;

    private boolean shouldScrollToMySlot;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_presenter_slot_selection);

        preferencesManager = PreferencesManager.getInstance(this);
        apiService = ApiClient.getInstance(this).getApiService();
        serverLogger = ServerLogger.getInstance(this);

        if (!preferencesManager.isPresenter()) {
            finish();
            return;
        }

        shouldScrollToMySlot = getIntent().getBooleanExtra(EXTRA_SCROLL_TO_MY_SLOT, false);

        setupToolbar();
        initializeViews();
        setupRecycler();
        setupInteractions();

        loadSlots();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void initializeViews() {
        swipeRefreshLayout = findViewById(R.id.swipe_refresh);
        recyclerSlots = findViewById(R.id.recycler_slots);
        emptyState = findViewById(R.id.layout_empty_state);
        progressBar = findViewById(R.id.progress_bar);
        btnReload = findViewById(R.id.btn_reload);
    }

    private void setupRecycler() {
        slotAdapter = new PresenterSlotsAdapter(this);
        recyclerSlots.setLayoutManager(new LinearLayoutManager(this));
        recyclerSlots.setAdapter(slotAdapter);
    }

    private void setupInteractions() {
        swipeRefreshLayout.setOnRefreshListener(this::loadSlots);
        btnReload.setOnClickListener(v -> loadSlots());
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (!loading) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    private void renderSlots(ApiService.PresenterHomeResponse response) {
        List<ApiService.SlotCard> allSlots = response != null && response.slotCatalog != null
                ? response.slotCatalog : Collections.emptyList();
        
        // Filter out past slots
        List<ApiService.SlotCard> futureSlots = filterPastSlots(allSlots);
        
        slotAdapter.submitList(futureSlots);
        emptyState.setVisibility(futureSlots.isEmpty() ? View.VISIBLE : View.GONE);

        if (shouldScrollToMySlot && response != null && response.mySlot != null) {
            shouldScrollToMySlot = false;
            long targetSlot = response.mySlot.slotId != null ? response.mySlot.slotId : -1L;
            if (targetSlot > 0) {
                int position = findSlotPosition(futureSlots, targetSlot);
                if (position >= 0) {
                    recyclerSlots.post(() -> recyclerSlots.smoothScrollToPosition(position));
                }
            }
        }
    }

    private int findSlotPosition(List<ApiService.SlotCard> slots, long slotId) {
        for (int i = 0; i < slots.size(); i++) {
            ApiService.SlotCard slot = slots.get(i);
            if (slot.slotId != null && slot.slotId == slotId) {
                return i;
            }
        }
        return -1;
    }

    private List<ApiService.SlotCard> filterPastSlots(List<ApiService.SlotCard> slots) {
        if (slots == null || slots.isEmpty()) {
            return Collections.emptyList();
        }

        List<ApiService.SlotCard> futureSlots = new ArrayList<>();
        Date now = new Date();
        SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);

        for (ApiService.SlotCard slot : slots) {
            // NOTE: We do NOT filter out slots based on hasClosedSession or attendanceClosesAt
            // because these are slot-level fields that may reflect OTHER presenters' closed sessions.
            // Each presenter should be able to open their own session independently.
            // The backend will validate if a presenter can open a session when they try to do so.
            
            if (slot.date == null || slot.timeRange == null) {
                // If we can't parse the date/time, include it (better to show than hide)
                futureSlots.add(slot);
                continue;
            }

            try {
                // Parse date and time range
                // Format: date is "yyyy-MM-dd", timeRange is "HH:mm-HH:mm"
                String[] timeParts = slot.timeRange.split("-");
                if (timeParts.length != 2) {
                    // Can't parse time range, include it
                    futureSlots.add(slot);
                    continue;
                }

                String startTimeStr = slot.date + " " + timeParts[0].trim();
                Date slotStartTime = dateTimeFormat.parse(startTimeStr);

                if (slotStartTime != null) {
                    // Include slots that are today or in the future
                    // Compare dates (not times) to include all slots for today
                    Calendar slotCal = Calendar.getInstance();
                    slotCal.setTime(slotStartTime);
                    slotCal.set(Calendar.HOUR_OF_DAY, 0);
                    slotCal.set(Calendar.MINUTE, 0);
                    slotCal.set(Calendar.SECOND, 0);
                    slotCal.set(Calendar.MILLISECOND, 0);
                    
                    Calendar nowCal = Calendar.getInstance();
                    nowCal.setTime(now);
                    nowCal.set(Calendar.HOUR_OF_DAY, 0);
                    nowCal.set(Calendar.MINUTE, 0);
                    nowCal.set(Calendar.SECOND, 0);
                    nowCal.set(Calendar.MILLISECOND, 0);
                    
                    // Include if slot date is today or in the future
                    if (!slotCal.getTime().before(nowCal.getTime())) {
                        futureSlots.add(slot);
                    }
                }
                // If slot is in the past (before today), skip it
            } catch (ParseException e) {
                // If parsing fails, include the slot (better to show than hide)
                Logger.w(Logger.TAG_UI, "Failed to parse slot date/time: " + slot.date + " " + slot.timeRange + " - " + e.getMessage());
                futureSlots.add(slot);
            }
        }

        return futureSlots;
    }

    private void loadSlots() {
        final String username = preferencesManager.getUserName();
        if (TextUtils.isEmpty(username)) {
            Toast.makeText(this, R.string.presenter_start_session_error_no_user, Toast.LENGTH_LONG).show();
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        final String normalizedUsername = username.trim().toLowerCase(Locale.US);
        setLoading(true);
        apiService.getPresenterHome(normalizedUsername).enqueue(new Callback<ApiService.PresenterHomeResponse>() {
            @Override
            public void onResponse(Call<ApiService.PresenterHomeResponse> call, Response<ApiService.PresenterHomeResponse> response) {
                setLoading(false);
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(PresenterSlotSelectionActivity.this, R.string.error_slot_load_failed, Toast.LENGTH_SHORT).show();
                    Logger.apiError("GET", "api/v1/presenters/{username}/home", response.code(), response.message());
                    return;
                }
                renderSlots(response.body());
            }

            @Override
            public void onFailure(Call<ApiService.PresenterHomeResponse> call, Throwable t) {
                setLoading(false);
                String errorMessage = getString(R.string.error_slot_load_failed);
                if (t instanceof java.net.SocketTimeoutException || t instanceof java.net.ConnectException) {
                    errorMessage = getString(R.string.error_network_timeout);
                } else if (t instanceof java.net.UnknownHostException) {
                    errorMessage = getString(R.string.error_server_unavailable);
                }
                Toast.makeText(PresenterSlotSelectionActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                Logger.e(Logger.TAG_API, "Failed to load presenter home", t);
            }
        });
    }

    @Override
    public void onRegisterClicked(ApiService.SlotCard slot) {
        Logger.userAction("Register Slot", "User clicked register for slot=" + (slot != null ? slot.slotId : "null"));
        if (serverLogger != null) {
            serverLogger.userAction("Register Slot", "User clicked register for slot=" + (slot != null ? slot.slotId : "null"));
        }
        // Show registration dialog with topic field
        showRegistrationDialog(slot);
    }
    
    private void showRegistrationDialog(ApiService.SlotCard slot) {
        Logger.d(Logger.TAG_UI, "Showing registration dialog for slot=" + (slot != null ? slot.slotId : "null"));
        if (serverLogger != null) {
            serverLogger.d(ServerLogger.TAG_UI, "Showing registration dialog for slot=" + (slot != null ? slot.slotId : "null"));
        }
        View dialogView = getLayoutInflater().inflate(R.layout.view_register_slot_dialog, null);
        TextInputLayout layoutTopic = dialogView.findViewById(R.id.input_layout_topic);
        TextInputEditText inputTopic = dialogView.findViewById(R.id.input_topic);
        
        // Hide supervisor fields - they're only needed for invitation email
        TextInputLayout layoutSupervisorName = dialogView.findViewById(R.id.input_layout_supervisor_name);
        TextInputLayout layoutSupervisorEmail = dialogView.findViewById(R.id.input_layout_supervisor_email);
        layoutSupervisorName.setVisibility(View.GONE);
        layoutSupervisorEmail.setVisibility(View.GONE);
        
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.presenter_home_register_title)
                .setMessage(R.string.presenter_home_register_description)
                .setView(dialogView)
                .setPositiveButton(R.string.presenter_slot_register_button, null)
                .setNegativeButton(R.string.cancel, (d, which) -> d.dismiss())
                .create();
        
        dialog.setOnShowListener(d -> {
            final Button button = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            button.setOnClickListener(v -> {
                String topic = inputTopic.getText() != null ? inputTopic.getText().toString().trim() : null;
                
                // Clear previous errors
                layoutTopic.setError(null);
                
                // Topic is optional, so we can proceed with null or empty topic
                // But if user entered something, use it
                if (topic != null && topic.isEmpty()) {
                    topic = null; // Convert empty string to null
                }
                
                // All validated - proceed with registration
                dialog.dismiss();
                performRegistration(slot, topic, null, null, null);
            });
        });
        
        dialog.show();
    }

    private void performRegistration(ApiService.SlotCard slot,
                                     @Nullable String topic,
                                     @Nullable String supervisorName,
                                     @Nullable String supervisorEmail,
                                     AlertDialog dialog) {
        Logger.userAction("Register Slot", "Starting registration for slot=" + (slot != null ? slot.slotId : "null"));
        if (serverLogger != null) {
            serverLogger.userAction("Register Slot", "Starting registration for slot=" + (slot != null ? slot.slotId : "null"));
        }
        
        // Null check for slot parameter to prevent NullPointerException
        if (slot == null) {
            Logger.e(Logger.TAG_UI, "Registration failed - slot is null");
            if (serverLogger != null) {
                serverLogger.e(ServerLogger.TAG_UI, "Registration failed - slot is null");
            }
            Toast.makeText(this, R.string.error_registration_failed, Toast.LENGTH_LONG).show();
            return;
        }
        
        // Extract slotId to final variable for safe use in lambda
        final Long slotId = slot.slotId;
        
        final String username = preferencesManager.getUserName();
        if (TextUtils.isEmpty(username)) {
            Logger.e(Logger.TAG_UI, "Registration failed - username is empty");
            if (serverLogger != null) {
                serverLogger.e(ServerLogger.TAG_UI, "Registration failed - username is empty");
            }
            Toast.makeText(this, R.string.presenter_start_session_error_no_user, Toast.LENGTH_LONG).show();
            return;
        }

        // Get presenter's email from profile (required for sending notification)
        final String presenterEmail = preferencesManager.getEmail();
        if (TextUtils.isEmpty(presenterEmail)) {
            Logger.e(Logger.TAG_UI, "Registration failed - presenter email is empty");
            if (serverLogger != null) {
                serverLogger.e(ServerLogger.TAG_UI, "Registration failed - presenter email is empty");
            }
            Toast.makeText(this, R.string.presenter_home_presenter_email_required, Toast.LENGTH_LONG).show();
            return;
        }

        final String normalizedUsername = username.trim().toLowerCase(Locale.US);
        // Make topic final for use in lambda
        final String finalTopic = topic;
        // Supervisor details are NOT needed for registration - they're only used for sending invitation email later
        // So we send null for supervisorName and supervisorEmail during registration
        ApiService.PresenterRegisterRequest request = new ApiService.PresenterRegisterRequest(finalTopic, null, null, presenterEmail);

        String apiEndpoint = "api/v1/presenters/" + normalizedUsername + "/home/slots/" + slotId + "/register";
        String apiMessage = "Registering for slot=" + slotId + ", topic=" + (finalTopic != null ? finalTopic : "null");
        Logger.api("POST", apiEndpoint, apiMessage);
        if (serverLogger != null) {
            serverLogger.api("POST", apiEndpoint, apiMessage);
        }
        
        apiService.registerForSlot(normalizedUsername, slotId, request)
                .enqueue(new Callback<ApiService.PresenterRegisterResponse>() {
                    @Override
                    public void onResponse(Call<ApiService.PresenterRegisterResponse> call, Response<ApiService.PresenterRegisterResponse> response) {
                        String apiEndpoint = "api/v1/presenters/" + normalizedUsername + "/home/slots/" + slotId + "/register";
                        Logger.apiResponse("POST", apiEndpoint, response.code(), "Registration response received");
                        if (serverLogger != null) {
                            serverLogger.apiResponse("POST", apiEndpoint, response.code(), "Registration response received");
                        }
                        
                        // Handle unsuccessful HTTP responses (non-200 status codes)
                        if (!response.isSuccessful()) {
                            Logger.apiError("POST", apiEndpoint, response.code(), "Registration failed with HTTP " + response.code());
                            if (serverLogger != null) {
                                serverLogger.apiError("POST", apiEndpoint, response.code(), "Registration failed with HTTP " + response.code());
                            }
                            String errorMessage = getString(R.string.error_registration_failed);
                            String errorCode = null;
                            
                            // Try to read error body to get actual error message
                            String rawErrorMessage = null;
                            if (response.errorBody() != null) {
                                try {
                                    String errorBodyString = response.errorBody().string();
                                    Logger.d(Logger.TAG_API, "Registration error response body: " + errorBodyString);
                                    
                                    // Try to parse JSON error body
                                    try {
                                        JsonObject jsonObject = new JsonParser().parse(errorBodyString).getAsJsonObject();
                                        
                                        // Extract message
                                        if (jsonObject.has("message") && jsonObject.get("message").isJsonPrimitive()) {
                                            rawErrorMessage = jsonObject.get("message").getAsString();
                                            errorMessage = rawErrorMessage;
                                        }
                                        
                                        // Extract code
                                        if (jsonObject.has("code") && jsonObject.get("code").isJsonPrimitive()) {
                                            errorCode = jsonObject.get("code").getAsString();
                                        }
                                        
                                        // Check error field for database lock timeout
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
                                            messageStart += 10; // Length of "\"message\":\""
                                            int messageEnd = errorBodyString.indexOf("\"", messageStart);
                                            if (messageEnd > messageStart) {
                                                rawErrorMessage = errorBodyString.substring(messageStart, messageEnd);
                                                errorMessage = rawErrorMessage;
                                            }
                                        }
                                        
                                        int codeStart = errorBodyString.indexOf("\"code\":\"");
                                        if (codeStart >= 0) {
                                            codeStart += 8; // Length of "\"code\":\""
                                            int codeEnd = errorBodyString.indexOf("\"", codeStart);
                                            if (codeEnd > codeStart) {
                                                errorCode = errorBodyString.substring(codeStart, codeEnd);
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
                                    String username = preferencesManager.getUserName();
                                    serverLogger.e(ServerLogger.TAG_API, "🚨 DATABASE LOCK TIMEOUT | " +
                                        "Response Code: " + response.code() + 
                                        ", Error Message: " + rawErrorMessage + 
                                        ", Presenter: " + (username != null ? username : "unknown") + 
                                        ", Slot ID: " + (slot != null ? slot.slotId : "unknown") +
                                        " | This indicates a database-level issue. Check for stuck transactions.");
                                    serverLogger.flushLogs(); // Force send critical error immediately
                                }
                                errorMessage = "Database lock timeout. Please try again in a few moments.";
                            }
                            
                            // Show appropriate error message based on code
                            if ("ALREADY_REGISTERED".equals(errorCode) || "ALREADY_IN_SLOT".equals(errorCode)) {
                                // Use the backend message if available, otherwise use default
                                if (errorMessage != null && !errorMessage.trim().isEmpty() && 
                                    !errorMessage.equals(getString(R.string.error_registration_failed))) {
                                    Toast.makeText(PresenterSlotSelectionActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(PresenterSlotSelectionActivity.this, R.string.presenter_home_register_already, Toast.LENGTH_LONG).show();
                                }
                            } else if ("SLOT_FULL".equals(errorCode)) {
                                Toast.makeText(PresenterSlotSelectionActivity.this, R.string.presenter_home_register_full, Toast.LENGTH_LONG).show();
                            } else {
                                // Show the actual error message from backend, or fallback to generic
                                Toast.makeText(PresenterSlotSelectionActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                            }
                            
                            if (dialog != null) {
                                dialog.dismiss();
                            }
                            loadSlots();
                            return;
                        }
                        
                        // Handle successful HTTP response but check response body
                        if (response.body() == null) {
                            Toast.makeText(PresenterSlotSelectionActivity.this, R.string.error_registration_failed, Toast.LENGTH_LONG).show();
                            return;
                        }

                        ApiService.PresenterRegisterResponse body = response.body();
                        String code = body.code != null ? body.code : "";
                        if (serverLogger != null) {
                            serverLogger.i(ServerLogger.TAG_API, "Registration response code: " + code + 
                                    ", message: " + (body.message != null ? body.message : "null"));
                        }
                        switch (code) {
                            case "REGISTERED":
                                Logger.i(Logger.TAG_API, "Registration successful for slot=" + slot.slotId);
                                if (serverLogger != null) {
                                    serverLogger.i(ServerLogger.TAG_API, "Registration successful for slot=" + slot.slotId + 
                                            ", presenter=" + normalizedUsername);
                                }
                                Toast.makeText(PresenterSlotSelectionActivity.this, R.string.presenter_home_register_success, Toast.LENGTH_LONG).show();
                                if (dialog != null) {
                                    dialog.dismiss();
                                }
                                
                                // After successful registration, ask if they want to send invitation to supervisor
                                // Pass the topic that was entered during registration
                                showSupervisorInvitationQuestionDialog(slot, finalTopic);
                                
                                loadSlots();
                                break;
                            case "ALREADY_IN_SLOT":
                            case "ALREADY_REGISTERED":
                                // Use backend message if available, otherwise use default
                                String alreadyRegisteredMsg = body.message != null && !body.message.trim().isEmpty()
                                    ? body.message
                                    : getString(R.string.presenter_home_register_already);
                                Toast.makeText(PresenterSlotSelectionActivity.this, alreadyRegisteredMsg, Toast.LENGTH_LONG).show();
                                if (dialog != null) {
                                    dialog.dismiss();
                                }
                                loadSlots();
                                break;
                            case "SLOT_FULL":
                                Toast.makeText(PresenterSlotSelectionActivity.this, R.string.presenter_home_register_full, Toast.LENGTH_LONG).show();
                                break;
                            default:
                                String errorMsg = body.message != null && !body.message.trim().isEmpty() 
                                    ? body.message 
                                    : getString(R.string.error_registration_failed);
                                Toast.makeText(PresenterSlotSelectionActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                                break;
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.PresenterRegisterResponse> call, Throwable t) {
                        String requestUrl = call.request() != null ? call.request().url().toString() : "unknown";
                        String errorDetails = "Slot registration network failure - URL: " + requestUrl + 
                                ", Error: " + t.getClass().getSimpleName() + ", Message: " + t.getMessage();
                        Logger.e(Logger.TAG_API, errorDetails, t);
                        if (serverLogger != null) {
                            serverLogger.e(ServerLogger.TAG_API, errorDetails, t);
                        }
                        
                        String errorMessage;
                        if (t instanceof java.net.SocketTimeoutException) {
                            errorMessage = "Connection timeout. Please check:\n" +
                                    "1. Backend server is running at " + ApiConstants.SERVER_URL + "\n" +
                                    "2. Network connection is stable\n" +
                                    "3. Server is accessible from your device";
                            Logger.e(Logger.TAG_API, "Slot registration timeout - URL: " + requestUrl, t);
                            if (serverLogger != null) {
                                serverLogger.e(ServerLogger.TAG_API, "Slot registration timeout - URL: " + requestUrl, t);
                            }
                        } else if (t instanceof java.net.ConnectException) {
                            errorMessage = "Cannot connect to server at " + ApiConstants.SERVER_URL + 
                                    ". Please check:\n" +
                                    "1. Server is running\n" +
                                    "2. Device can reach the server IP\n" +
                                    "3. Firewall allows connections";
                            Logger.e(Logger.TAG_API, "Slot registration connection failed - URL: " + requestUrl, t);
                            if (serverLogger != null) {
                                serverLogger.e(ServerLogger.TAG_API, "Slot registration connection failed - URL: " + requestUrl, t);
                            }
                        } else if (t instanceof java.net.UnknownHostException) {
                            errorMessage = "Server unavailable: " + ApiConstants.SERVER_URL + 
                                    "\nPlease check the server address.";
                            Logger.e(Logger.TAG_API, "Slot registration - server unavailable: " + requestUrl, t);
                            if (serverLogger != null) {
                                serverLogger.e(ServerLogger.TAG_API, "Slot registration - server unavailable: " + requestUrl, t);
                            }
                        } else {
                            errorMessage = "Registration failed: " + t.getMessage() + 
                                    "\nURL: " + requestUrl;
                            Logger.e(Logger.TAG_API, "Slot registration failed - Error: " + t.getClass().getSimpleName() + 
                                    ", Message: " + t.getMessage(), t);
                            if (serverLogger != null) {
                                serverLogger.e(ServerLogger.TAG_API, "Slot registration failed - Error: " + t.getClass().getSimpleName() + 
                                        ", Message: " + t.getMessage(), t);
                            }
                        }
                        
                        Toast.makeText(PresenterSlotSelectionActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
    }
    
    /**
     * Show dialog asking if user wants to send supervisor invitation after successful registration
     */
    private void showSupervisorInvitationQuestionDialog(ApiService.SlotCard slot, 
                                                        @Nullable String topic) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.presenter_slot_supervisor_invitation_title)
                .setMessage(R.string.presenter_slot_supervisor_invitation_message)
                .setPositiveButton(R.string.yes, (d, which) -> {
                    // User wants to send invitation - show form to collect supervisor details
                    showSupervisorDetailsDialog(slot, topic);
                })
                .setNegativeButton(R.string.no, (d, which) -> {
                    // User doesn't want to send invitation - just dismiss
                    d.dismiss();
                })
                .setCancelable(true)
                .show();
    }
    
    /**
     * Show dialog to collect supervisor details (name, email, topic) for sending invitation
     */
    private void showSupervisorDetailsDialog(ApiService.SlotCard slot, @Nullable String topic) {
        View dialogView = getLayoutInflater().inflate(R.layout.view_register_slot_dialog, null);
        TextInputLayout layoutTopic = dialogView.findViewById(R.id.input_layout_topic);
        TextInputLayout layoutSupervisorName = dialogView.findViewById(R.id.input_layout_supervisor_name);
        TextInputLayout layoutSupervisorEmail = dialogView.findViewById(R.id.input_layout_supervisor_email);
        TextInputEditText inputTopic = dialogView.findViewById(R.id.input_topic);
        TextInputEditText inputSupervisorName = dialogView.findViewById(R.id.input_supervisor_name);
        TextInputEditText inputSupervisorEmail = dialogView.findViewById(R.id.input_supervisor_email);
        
        // Pre-fill topic if it was entered during registration
        if (topic != null && !topic.trim().isEmpty()) {
            inputTopic.setText(topic);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.presenter_slot_supervisor_invitation_title)
                .setView(dialogView)
                .setPositiveButton(R.string.presenter_slot_send_invitation_button, null)
                .setNegativeButton(R.string.cancel, (d, which) -> d.dismiss())
                .create();

        dialog.setOnShowListener(d -> {
            final Button button = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            button.setOnClickListener(v -> {
                String supervisorTopic = inputTopic.getText() != null ? inputTopic.getText().toString().trim() : null;
                String supervisorName = inputSupervisorName.getText() != null ? inputSupervisorName.getText().toString().trim() : null;
                String supervisorEmail = inputSupervisorEmail.getText() != null ? inputSupervisorEmail.getText().toString().trim() : null;

                // Clear previous errors
                layoutTopic.setError(null);
                layoutSupervisorName.setError(null);
                layoutSupervisorEmail.setError(null);

                // Validate supervisor name (required)
                if (TextUtils.isEmpty(supervisorName)) {
                    layoutSupervisorName.setError(getString(R.string.presenter_home_supervisor_name_required));
                    return;
                }

                // Validate supervisor email (required)
                if (TextUtils.isEmpty(supervisorEmail)) {
                    layoutSupervisorEmail.setError(getString(R.string.presenter_home_supervisor_email_required));
                    return;
                }

                // Validate email format
                if (!Patterns.EMAIL_ADDRESS.matcher(supervisorEmail).matches()) {
                    layoutSupervisorEmail.setError(getString(R.string.presenter_home_supervisor_email_invalid));
                    return;
                }

                // All validated - send invitation email
                dialog.dismiss();
                sendSupervisorInvitationEmail(slot, supervisorTopic, supervisorName, supervisorEmail);
            });
        });

        dialog.show();
    }
    
    private void sendSupervisorInvitationEmail(ApiService.SlotCard slot, 
                                              @Nullable String topic, 
                                              @NonNull String supervisorName, 
                                              @NonNull String supervisorEmail) {
        Logger.i(Logger.TAG_UI, "Sending supervisor invitation email to: " + supervisorEmail);
        
        // Get presenter's name and email
        String presenterFirstName = preferencesManager.getFirstName();
        String presenterLastName = preferencesManager.getLastName();
        String presenterName = (presenterFirstName != null ? presenterFirstName : "") + 
                              (presenterLastName != null ? " " + presenterLastName : "").trim();
        String presenterEmail = preferencesManager.getEmail();
        
        // Build email content
        String subject = "SemScan - Presentation Slot Registration Invitation";
        String htmlContent = buildSupervisorInvitationEmailHtml(slot, topic, supervisorName, presenterName, presenterEmail);
        
        // Send email via backend API
        ApiService.TestEmailRequest request = new ApiService.TestEmailRequest(
            supervisorEmail,
            subject,
            htmlContent
        );
        
        apiService.sendTestEmail(request).enqueue(new Callback<ApiService.TestEmailResponse>() {
            @Override
            public void onResponse(Call<ApiService.TestEmailResponse> call, Response<ApiService.TestEmailResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().success) {
                    Logger.i(Logger.TAG_API, "Supervisor invitation email sent successfully to: " + supervisorEmail);
                    Toast.makeText(PresenterSlotSelectionActivity.this, 
                        getString(R.string.presenter_slot_supervisor_email_sent, supervisorEmail), 
                        Toast.LENGTH_LONG).show();
                } else {
                    Logger.e(Logger.TAG_API, "Failed to send supervisor invitation email to: " + supervisorEmail);
                    Toast.makeText(PresenterSlotSelectionActivity.this, 
                        getString(R.string.presenter_slot_supervisor_email_failed), 
                        Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<ApiService.TestEmailResponse> call, Throwable t) {
                Logger.e(Logger.TAG_API, "Error sending supervisor invitation email to: " + supervisorEmail, t);
                Toast.makeText(PresenterSlotSelectionActivity.this, 
                    getString(R.string.presenter_slot_supervisor_email_failed), 
                    Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private String buildSupervisorInvitationEmailHtml(ApiService.SlotCard slot, 
                                                      @Nullable String topic, 
                                                      @NonNull String supervisorName,
                                                      @NonNull String presenterName,
                                                      @Nullable String presenterEmail) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>");
        html.append("<html>");
        html.append("<head>");
        html.append("<meta charset=\"UTF-8\">");
        html.append("<style>");
        html.append("body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }");
        html.append(".container { max-width: 600px; margin: 0 auto; padding: 20px; }");
        html.append(".header { background-color: #2196F3; color: white; padding: 20px; text-align: center; }");
        html.append(".content { padding: 20px; background-color: #f9f9f9; }");
        html.append(".details { background-color: white; padding: 15px; margin: 15px 0; border-left: 4px solid #2196F3; }");
        html.append(".footer { padding: 20px; text-align: center; color: #666; font-size: 12px; }");
        html.append("</style>");
        html.append("</head>");
        html.append("<body>");
        html.append("<div class=\"container\">");
        html.append("<div class=\"header\">");
        html.append("<h1>SemScan - Presentation Slot Registration</h1>");
        html.append("</div>");
        html.append("<div class=\"content\">");
        html.append("<p>Dear ").append(escapeHtml(supervisorName)).append(",</p>");
        html.append("<p>You have been invited as a supervisor for a presentation slot registration.</p>");
        
        // Presentation details
        html.append("<div class=\"details\">");
        html.append("<h3>Presentation Details:</h3>");
        html.append("<ul>");
        html.append("<li><strong>Presenter:</strong> ").append(escapeHtml(presenterName)).append("</li>");
        if (presenterEmail != null && !presenterEmail.isEmpty()) {
            html.append("<li><strong>Presenter Email:</strong> ").append(escapeHtml(presenterEmail)).append("</li>");
        }
        if (topic != null && !topic.trim().isEmpty()) {
            html.append("<li><strong>Topic:</strong> ").append(escapeHtml(topic)).append("</li>");
        }
        if (slot.date != null) {
            html.append("<li><strong>Date:</strong> ").append(escapeHtml(slot.date)).append("</li>");
        }
        if (slot.timeRange != null) {
            html.append("<li><strong>Time:</strong> ").append(escapeHtml(slot.timeRange)).append("</li>");
        }
        if (slot.room != null && slot.building != null) {
            html.append("<li><strong>Location:</strong> Building ").append(escapeHtml(slot.building))
                .append(", Room ").append(escapeHtml(slot.room)).append("</li>");
        }
        html.append("</ul>");
        html.append("</div>");
        
        html.append("<p>This is an automated notification from the SemScan system.</p>");
        html.append("</div>");
        html.append("<div class=\"footer\">");
        html.append("<p>This is an automated message from SemScan System.</p>");
        html.append("<p>Please do not reply to this email.</p>");
        html.append("</div>");
        html.append("</div>");
        html.append("</body>");
        html.append("</html>");
        
        return html.toString();
    }
    
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
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