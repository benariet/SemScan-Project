package org.example.semscan.ui.teacher;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
import org.example.semscan.ui.SettingsActivity;
import org.example.semscan.constants.ApiConstants;
import org.example.semscan.data.api.ApiClient;
import org.example.semscan.data.api.ApiService;
import org.example.semscan.utils.ErrorMessageHelper;
import org.example.semscan.utils.Logger;
import org.example.semscan.utils.PreferencesManager;
import org.example.semscan.utils.ConfigManager;
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
    private View btnReload;

    private PresenterSlotsAdapter slotAdapter;
    private PreferencesManager preferencesManager;
    private ApiService apiService;
    private ServerLogger serverLogger;

    private boolean shouldScrollToMySlot;
    private List<ApiService.SlotCard> currentSlots = Collections.emptyList(); // Store current slots for limit checking
    private Long lastJoinedWaitingListSlotId = null; // Track slot where user just joined waiting list
    private List<SlotStateSnapshot> previousSlotStates = new ArrayList<>(); // Track previous slot states to detect changes

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_presenter_slot_selection);

        preferencesManager = PreferencesManager.getInstance(this);
        apiService = ApiClient.getInstance(this).getApiService();
        serverLogger = ServerLogger.getInstance(this);
        
        // Update user context for logging
        String username = preferencesManager.getUserName();
        String userRole = preferencesManager.getUserRole();
        if (serverLogger != null) {
            serverLogger.updateUserContext(username, userRole);
        }

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
        if (btnReload != null) {
            btnReload.setOnClickListener(v -> loadSlots());
        }
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
        
        // Store current slots for limit checking
        currentSlots = futureSlots;
        
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
                
                // Log waiting list status from backend for debugging - check ALL slots
                if (response.body().slotCatalog != null) {
                    for (ApiService.SlotCard slot : response.body().slotCatalog) {
                        // Log waiting list status for all slots to debug (both local and server)
                        String wlStatus = "Slot " + slot.slotId + " waiting list: onWaitingList=" + 
                            slot.onWaitingList + ", waitingListCount=" + slot.waitingListCount + 
                            ", waitingListUserName=" + slot.waitingListUserName;
                        Logger.i(Logger.TAG_API, wlStatus);
                        if (serverLogger != null) {
                            serverLogger.i(ServerLogger.TAG_API, wlStatus);
                        }
                        
                        // WARNING: If backend doesn't return waitingListCount, it defaults to 0
                        // Backend MUST include waitingListCount in JSON response for ALL slots
                        // This is required so all users can see if someone is on the waiting list
                        if (slot.waitingListCount == 0 && slot.onWaitingList) {
                            // This shouldn't happen - if user is on waiting list, count should be at least 1
                            String warning = "WARNING: Slot " + slot.slotId + " has onWaitingList=true but waitingListCount=0. " +
                                "Backend may not be returning waitingListCount field in JSON response.";
                            Logger.w(Logger.TAG_API, warning);
                            if (serverLogger != null) {
                                serverLogger.w(ServerLogger.TAG_API, warning);
                            }
                        }
                        
                        // If backend didn't return onWaitingList=true but we just joined, fix it locally
                        if (lastJoinedWaitingListSlotId != null && slot.slotId != null && 
                            slot.slotId.equals(lastJoinedWaitingListSlotId) && !slot.onWaitingList) {
                            String fixMsg = "Backend didn't return onWaitingList=true for slot " + slot.slotId + " we just joined, fixing locally";
                            Logger.w(Logger.TAG_API, fixMsg);
                            if (serverLogger != null) {
                                serverLogger.w(ServerLogger.TAG_API, fixMsg);
                            }
                            slot.onWaitingList = true;
                            if (slot.waitingListCount <= 0) {
                                slot.waitingListCount = 1;
                            }
                        }
                        
                        // IMPORTANT: If backend returns waitingListCount=0 but someone is on waiting list,
                        // we need to detect this. The backend should return waitingListCount > 0 for ALL users
                        // when someone is on the waiting list, not just when the current user is on it.
                        // For now, if onWaitingList=true but waitingListCount=0, set it to 1
                        if (slot.onWaitingList && slot.waitingListCount <= 0) {
                            String fixCountMsg = "Slot " + slot.slotId + " has onWaitingList=true but waitingListCount=0, fixing to 1";
                            Logger.w(Logger.TAG_API, fixCountMsg);
                            if (serverLogger != null) {
                                serverLogger.w(ServerLogger.TAG_API, fixCountMsg);
                            }
                            slot.waitingListCount = 1;
                        }
                    }
                    // Clear the tracking after we've processed it
                    lastJoinedWaitingListSlotId = null;
                }
                
                // Check for waiting list approval and cancelled pending registrations
                checkForWaitingListApprovalAndCancellations(response.body());
                
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
        
        // Check registration limits BEFORE showing dialog
        String errorMessage = checkRegistrationLimitsEarly(slot);
        if (errorMessage != null) {
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
            return;
        }
        
        // All checks passed - show registration dialog
        showRegistrationDialog(slot);
    }

    @Override
    public void onJoinWaitingList(ApiService.SlotCard slot) {
        Logger.userAction("Join Waiting List", "User clicked join waiting list for slot=" + (slot != null ? slot.slotId : "null"));
        if (serverLogger != null) {
            serverLogger.userAction("Join Waiting List", "User clicked join waiting list for slot=" + (slot != null ? slot.slotId : "null"));
        }
        
        // Check waiting list limit before showing dialog (user can only be on 1 waiting list at a time)
        int userWaitingListCount = 0;
        for (ApiService.SlotCard s : currentSlots) {
            if (s.onWaitingList) {
                userWaitingListCount++;
            }
        }
        // User can only be on 1 waiting list at a time (business rule, not configurable)
        if (userWaitingListCount >= 1) {
            Toast.makeText(this, 
                "You can only be on 1 waiting list at once. " +
                "Please cancel your current waiting list position first.",
                Toast.LENGTH_LONG).show();
            return;
        }
        
        // Check if this slot's waiting list is full (per-slot limit from config)
        ConfigManager configManager = ConfigManager.getInstance(this);
        int waitingListLimitPerSlot = configManager.getWaitingListLimitPerSlot();
        if (slot.waitingListCount >= waitingListLimitPerSlot) {
            Toast.makeText(this, 
                "The waiting list for this slot is full (max " + waitingListLimitPerSlot + "). " +
                "Please try another slot.",
                Toast.LENGTH_LONG).show();
            return;
        }
        
        joinWaitingList(slot);
    }

    @Override
    public void onCancelWaitingList(ApiService.SlotCard slot) {
        Logger.userAction("Cancel Waiting List", "User clicked cancel waiting list for slot=" + (slot != null ? slot.slotId : "null"));
        if (serverLogger != null) {
            serverLogger.userAction("Cancel Waiting List", "User clicked cancel waiting list for slot=" + (slot != null ? slot.slotId : "null"));
        }
        leaveWaitingList(slot);
    }

    @Override
    public void onSlotClicked(ApiService.SlotCard slot, boolean isFull) {
        String action = isFull ? "Full Slot Clicked" : "Slot Clicked";
        String details = "User clicked slot=" + (slot != null ? slot.slotId : "null") + ", isFull=" + isFull;
        Logger.userAction(action, details);
        if (serverLogger != null) {
            serverLogger.userAction(action, details);
        }
    }

    /**
     * Join waiting list using topic and abstract from Settings.
     * Shows a warning dialog for PhD students about capacity requirements.
     */
    private void joinWaitingList(ApiService.SlotCard slot) {
        // Get topic and abstract from Settings
        String savedTopic = preferencesManager.getPresentationTopic();
        String savedAbstract = preferencesManager.getSeminarAbstract();

        boolean hasTopic = savedTopic != null && !savedTopic.trim().isEmpty();
        boolean hasAbstract = savedAbstract != null && !savedAbstract.trim().isEmpty();

        // Check if both topic and abstract are filled in Settings
        if (hasTopic && hasAbstract) {
            // Check if user is PhD - show warning about capacity requirements
            String degree = preferencesManager.getDegree();
            boolean isPhd = "PhD".equalsIgnoreCase(degree);

            if (isPhd) {
                // Show PhD warning dialog
                new AlertDialog.Builder(this)
                    .setTitle("PhD Waiting List Notice")
                    .setMessage("As a PhD student, your presentation is 40 minutes (MSc is 20 minutes).\n\n" +
                               "This means you will only be promoted from the waiting list when 40 minutes of presentation time become available.\n\n" +
                               "MSc students may be promoted before you if only 20 minutes open up.")
                    .setPositiveButton("OK, Join Waiting List", (dialog, which) -> {
                        Logger.i(Logger.TAG_UI, "PhD user confirmed joining waiting list: topic=" + savedTopic.trim());
                        if (serverLogger != null) {
                            serverLogger.i(ServerLogger.TAG_UI, "PhD user confirmed joining waiting list for slot=" +
                                (slot != null ? slot.slotId : "null"));
                        }
                        performJoinWaitingList(slot, savedTopic.trim());
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> {
                        Logger.i(Logger.TAG_UI, "PhD user cancelled joining waiting list");
                        if (serverLogger != null) {
                            serverLogger.i(ServerLogger.TAG_UI, "PhD user cancelled joining waiting list for slot=" +
                                (slot != null ? slot.slotId : "null"));
                        }
                        // Do nothing - user cancelled
                    })
                    .show();
            } else {
                // MSc - proceed directly without dialog
                Logger.i(Logger.TAG_UI, "Joining waiting list with settings: topic=" + savedTopic.trim() +
                    ", abstractLength=" + savedAbstract.trim().length());
                if (serverLogger != null) {
                    serverLogger.i(ServerLogger.TAG_UI, "Joining waiting list with settings for slot=" +
                        (slot != null ? slot.slotId : "null"));
                }
                performJoinWaitingList(slot, savedTopic.trim());
            }
        } else {
            // Missing topic or abstract - redirect to Settings
            String missing = "";
            if (!hasTopic && !hasAbstract) {
                missing = "topic and abstract";
            } else if (!hasTopic) {
                missing = "topic";
            } else {
                missing = "abstract";
            }

            Logger.w(Logger.TAG_UI, "Cannot join waiting list - missing " + missing + " in Settings");
            if (serverLogger != null) {
                serverLogger.w(ServerLogger.TAG_UI, "Cannot join waiting list - missing " + missing +
                    " in Settings, slot=" + (slot != null ? slot.slotId : "null"));
            }

            Toast.makeText(this,
                "Please fill in your presentation " + missing + " in Settings first.",
                Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, SettingsActivity.class));
        }
    }

    /**
     * Call API to join waiting list with topic and abstract
     */
    private void performJoinWaitingList(ApiService.SlotCard slot, String topic) {
        Logger.userAction("Join Waiting List", "Starting join waiting list for slot=" + (slot != null ? slot.slotId : "null") + 
                ", topic=" + (topic != null ? topic : "null"));
        if (serverLogger != null) {
            serverLogger.userAction("Join Waiting List", "Starting join waiting list for slot=" + (slot != null ? slot.slotId : "null"));
        }
        
        if (slot == null || slot.slotId == null) {
            Logger.e(Logger.TAG_UI, "Join waiting list failed - slot is null");
            if (serverLogger != null) {
                serverLogger.e(ServerLogger.TAG_UI, "Join waiting list failed - slot is null");
            }
            Toast.makeText(this, "Invalid slot", Toast.LENGTH_SHORT).show();
            return;
        }

        String username = preferencesManager.getUserName();
        if (TextUtils.isEmpty(username)) {
            Logger.e(Logger.TAG_UI, "Join waiting list failed - username is empty");
            if (serverLogger != null) {
                serverLogger.e(ServerLogger.TAG_UI, "Join waiting list failed - username is empty");
            }
            Toast.makeText(this, R.string.presenter_start_session_error_no_user, Toast.LENGTH_LONG).show();
            return;
        }

        // Get supervisor info from settings
        String supervisorName = preferencesManager.getSupervisorName();
        String supervisorEmail = preferencesManager.getSupervisorEmail();

        // Validate supervisor info is set in settings
        if (TextUtils.isEmpty(supervisorName) || TextUtils.isEmpty(supervisorEmail)) {
            Logger.e(Logger.TAG_UI, "Join waiting list failed - supervisor info missing. Name=" + supervisorName + ", Email=" + supervisorEmail);
            if (serverLogger != null) {
                serverLogger.e(ServerLogger.TAG_UI, "Join waiting list failed - supervisor info missing. Name=" + supervisorName + ", Email=" + supervisorEmail);
            }
            Toast.makeText(this, R.string.presenter_home_supervisor_info_required, Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }

        final String normalizedUsername = username.trim().toLowerCase(Locale.US);
        // Include topic and supervisor info in request (abstract is stored in user profile)
        ApiService.WaitingListRequest request = new ApiService.WaitingListRequest(
                normalizedUsername, topic, supervisorName.trim(), supervisorEmail.trim());

        String apiEndpoint = "api/v1/slots/" + slot.slotId + "/waiting-list";
        String apiMessage = "Joining waiting list for slot=" + slot.slotId + ", topic=" + topic + ", supervisorName=" + supervisorName + ", supervisorEmail=" + supervisorEmail;
        Logger.api("POST", apiEndpoint, apiMessage);
        if (serverLogger != null) {
            serverLogger.api("POST", apiEndpoint, apiMessage);
        }

        apiService.joinWaitingList(slot.slotId, request)
                .enqueue(new Callback<ApiService.WaitingListResponse>() {
                    @Override
                    public void onResponse(Call<ApiService.WaitingListResponse> call, 
                                         Response<ApiService.WaitingListResponse> response) {
                        Logger.apiResponse("POST", apiEndpoint, response.code(), "Join waiting list response received");
                        if (serverLogger != null) {
                            serverLogger.apiResponse("POST", apiEndpoint, response.code(), "Join waiting list response received");
                        }
                        
                        if (response.isSuccessful() && response.body() != null && response.body().ok) {
                            Logger.i(Logger.TAG_API, "Successfully joined waiting list for slot=" + slot.slotId);
                            if (serverLogger != null) {
                                serverLogger.i(ServerLogger.TAG_API, "Successfully joined waiting list for slot=" + slot.slotId + 
                                        ", user=" + normalizedUsername);
                            }
                            // Remember that we just joined this slot's waiting list
                            lastJoinedWaitingListSlotId = slot.slotId;
                            Toast.makeText(PresenterSlotSelectionActivity.this, 
                                "Added to waiting list. You will be notified when slot becomes available.",
                                Toast.LENGTH_LONG).show();
                            loadSlots(); // Refresh to show waiting list status
                        } else {
                            Logger.apiError("POST", apiEndpoint, response.code(), "Failed to join waiting list");
                            if (serverLogger != null) {
                                serverLogger.apiError("POST", apiEndpoint, response.code(), "Failed to join waiting list");
                            }
                            
                            // Try to extract error message from response body
                            String errorMessage = null;
                            if (response.body() != null && response.body().message != null) {
                                errorMessage = response.body().message;
                            } else if (response.errorBody() != null) {
                                try {
                                    String errorBodyString = response.errorBody().string();
                                    Logger.i(Logger.TAG_API, "Join waiting list error response body: " + errorBodyString);
                                    
                                    // Try to parse JSON error body
                                    try {
                                        JsonObject jsonObject = new JsonParser().parse(errorBodyString).getAsJsonObject();
                                        if (jsonObject.has("message") && jsonObject.get("message").isJsonPrimitive()) {
                                            errorMessage = jsonObject.get("message").getAsString();
                                        } else if (jsonObject.has("error") && jsonObject.get("error").isJsonPrimitive()) {
                                            errorMessage = jsonObject.get("error").getAsString();
                                        }
                                    } catch (Exception e) {
                                        // If JSON parsing fails, try manual extraction
                                        int messageStart = errorBodyString.indexOf("\"message\":\"");
                                        if (messageStart >= 0) {
                                            messageStart += 10;
                                            int messageEnd = errorBodyString.indexOf("\"", messageStart);
                                            if (messageEnd > messageStart) {
                                                errorMessage = errorBodyString.substring(messageStart, messageEnd);
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    Logger.e(Logger.TAG_API, "Failed to read error body", e);
                                }
                            }
                            
                            // Get user-friendly error message
                            String userMessage = ErrorMessageHelper.getWaitingListErrorMessage(
                                PresenterSlotSelectionActivity.this, response.code(), errorMessage);
                            Toast.makeText(PresenterSlotSelectionActivity.this, userMessage, Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.WaitingListResponse> call, Throwable t) {
                        String requestUrl = call.request() != null ? call.request().url().toString() : "unknown";
                        String errorDetails = "Join waiting list network failure - URL: " + requestUrl + 
                                ", Error: " + t.getClass().getSimpleName() + ", Message: " + t.getMessage();
                        Logger.e(Logger.TAG_API, errorDetails, t);
                        if (serverLogger != null) {
                            serverLogger.e(ServerLogger.TAG_API, errorDetails, t);
                        }
                        
                        String errorMessage = ErrorMessageHelper.getNetworkErrorMessage(
                            PresenterSlotSelectionActivity.this, t);
                        Toast.makeText(PresenterSlotSelectionActivity.this, 
                            "Cannot join waiting list: " + errorMessage,
                            Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * Show dialog to confirm leaving waiting list
     */
    private void leaveWaitingList(ApiService.SlotCard slot) {
        new AlertDialog.Builder(this)
                .setTitle("Leave Waiting List")
                .setMessage("Are you sure you want to leave the waiting list for this slot?")
                .setPositiveButton("Leave", (d, which) -> {
                    performLeaveWaitingList(slot);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Call API to leave waiting list
     */
    private void performLeaveWaitingList(ApiService.SlotCard slot) {
        if (slot == null || slot.slotId == null) {
            Toast.makeText(this, "Invalid slot", Toast.LENGTH_SHORT).show();
            return;
        }

        String username = preferencesManager.getUserName();
        if (TextUtils.isEmpty(username)) {
            Toast.makeText(this, R.string.presenter_start_session_error_no_user, Toast.LENGTH_LONG).show();
            return;
        }

        final String normalizedUsername = username.trim().toLowerCase(Locale.US);
        String apiEndpoint = "api/v1/slots/" + slot.slotId + "/waiting-list";
        String apiMessage = "Leaving waiting list for slot=" + slot.slotId;
        Logger.api("DELETE", apiEndpoint, apiMessage);
        if (serverLogger != null) {
            serverLogger.api("DELETE", apiEndpoint, apiMessage);
        }

        apiService.leaveWaitingList(slot.slotId, normalizedUsername)
                .enqueue(new Callback<ApiService.WaitingListResponse>() {
                    @Override
                    public void onResponse(Call<ApiService.WaitingListResponse> call, 
                                         Response<ApiService.WaitingListResponse> response) {
                        Logger.apiResponse("DELETE", apiEndpoint, response.code(), "Leave waiting list response received");
                        if (serverLogger != null) {
                            serverLogger.apiResponse("DELETE", apiEndpoint, response.code(), "Leave waiting list response received");
                        }
                        
                        if (response.isSuccessful() && response.body() != null && response.body().ok) {
                            Logger.i(Logger.TAG_API, "Successfully left waiting list for slot=" + slot.slotId);
                            if (serverLogger != null) {
                                serverLogger.i(ServerLogger.TAG_API, "Successfully left waiting list for slot=" + slot.slotId + 
                                        ", user=" + normalizedUsername);
                            }
                            // Clear tracking if we left the waiting list
                            if (lastJoinedWaitingListSlotId != null && lastJoinedWaitingListSlotId.equals(slot.slotId)) {
                                lastJoinedWaitingListSlotId = null;
                            }
                            Toast.makeText(PresenterSlotSelectionActivity.this, 
                                "Left waiting list successfully.",
                                Toast.LENGTH_SHORT).show();
                            loadSlots(); // Refresh to update waiting list status
                        } else {
                            Logger.apiError("DELETE", apiEndpoint, response.code(), "Failed to leave waiting list");
                            if (serverLogger != null) {
                                serverLogger.apiError("DELETE", apiEndpoint, response.code(), "Failed to leave waiting list");
                            }
                            
                            // Try to extract error message from response body
                            String errorMessage = null;
                            if (response.body() != null && response.body().message != null) {
                                errorMessage = response.body().message;
                            } else if (response.errorBody() != null) {
                                try {
                                    String errorBodyString = response.errorBody().string();
                                    Logger.i(Logger.TAG_API, "Leave waiting list error response body: " + errorBodyString);
                                    
                                    // Try to parse JSON error body
                                    try {
                                        JsonObject jsonObject = new JsonParser().parse(errorBodyString).getAsJsonObject();
                                        if (jsonObject.has("message") && jsonObject.get("message").isJsonPrimitive()) {
                                            errorMessage = jsonObject.get("message").getAsString();
                                        } else if (jsonObject.has("error") && jsonObject.get("error").isJsonPrimitive()) {
                                            errorMessage = jsonObject.get("error").getAsString();
                                        }
                                    } catch (Exception e) {
                                        // If JSON parsing fails, try manual extraction
                                        int messageStart = errorBodyString.indexOf("\"message\":\"");
                                        if (messageStart >= 0) {
                                            messageStart += 10;
                                            int messageEnd = errorBodyString.indexOf("\"", messageStart);
                                            if (messageEnd > messageStart) {
                                                errorMessage = errorBodyString.substring(messageStart, messageEnd);
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    Logger.e(Logger.TAG_API, "Failed to read error body", e);
                                }
                            }
                            
                            // Get user-friendly error message
                            String userMessage = ErrorMessageHelper.getWaitingListErrorMessage(
                                PresenterSlotSelectionActivity.this, response.code(), errorMessage);
                            Toast.makeText(PresenterSlotSelectionActivity.this, userMessage, Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.WaitingListResponse> call, Throwable t) {
                        String requestUrl = call.request() != null ? call.request().url().toString() : "unknown";
                        String errorDetails = "Leave waiting list network failure - URL: " + requestUrl + 
                                ", Error: " + t.getClass().getSimpleName() + ", Message: " + t.getMessage();
                        Logger.e(Logger.TAG_API, errorDetails, t);
                        if (serverLogger != null) {
                            serverLogger.e(ServerLogger.TAG_API, errorDetails, t);
                        }
                        
                        String errorMessage = ErrorMessageHelper.getNetworkErrorMessage(
                            PresenterSlotSelectionActivity.this, t);
                        Toast.makeText(PresenterSlotSelectionActivity.this, 
                            "Cannot leave waiting list: " + errorMessage,
                            Toast.LENGTH_LONG).show();
                    }
                });
    }
    
    private void showRegistrationDialog(ApiService.SlotCard slot) {
        Logger.i(Logger.TAG_UI, "Showing registration confirmation dialog for slot=" + (slot != null ? slot.slotId : "null"));
        if (serverLogger != null) {
            serverLogger.i(ServerLogger.TAG_UI, "Showing registration confirmation dialog for slot=" + (slot != null ? slot.slotId : "null"));
        }

        // Get all presentation details from saved preferences
        String topic = preferencesManager.getPresentationTopic();
        String seminarAbstract = preferencesManager.getSeminarAbstract();
        String supervisorName = preferencesManager.getSupervisorName();
        String supervisorEmail = preferencesManager.getSupervisorEmail();

        // Validate all required fields are filled
        boolean hasTopic = !TextUtils.isEmpty(topic);
        boolean hasAbstract = !TextUtils.isEmpty(seminarAbstract);
        boolean hasSupervisorName = !TextUtils.isEmpty(supervisorName);
        boolean hasSupervisorEmail = !TextUtils.isEmpty(supervisorEmail) &&
                Patterns.EMAIL_ADDRESS.matcher(supervisorEmail).matches();

        if (!hasTopic || !hasAbstract || !hasSupervisorName || !hasSupervisorEmail) {
            // Presentation details not complete - redirect to home to fill them
            Toast.makeText(this, R.string.registration_details_incomplete, Toast.LENGTH_LONG).show();
            Logger.w(Logger.TAG_UI, "Registration blocked - presentation details incomplete: " +
                    "topic=" + hasTopic + ", abstract=" + hasAbstract +
                    ", supervisorName=" + hasSupervisorName + ", supervisorEmail=" + hasSupervisorEmail);
            if (serverLogger != null) {
                serverLogger.w(ServerLogger.TAG_UI, "Registration blocked - presentation details incomplete");
            }
            // Go back to home to fill in details
            finish();
            return;
        }

        // Capture values for use in lambda
        final String finalTopic = topic.trim();
        final String finalAbstract = seminarAbstract.trim();
        final String finalSupervisorName = supervisorName.trim();
        final String finalSupervisorEmail = supervisorEmail.trim();

        // Build confirmation message with HTML formatting
        String messageHtml = getString(R.string.registration_confirm_message,
                finalSupervisorName, finalSupervisorEmail, finalTopic);
        CharSequence message = Html.fromHtml(messageHtml, Html.FROM_HTML_MODE_LEGACY);

        // Show simple confirmation dialog (Register and Cancel only)
        new AlertDialog.Builder(this)
                .setTitle(R.string.registration_confirm_title)
                .setMessage(message)
                .setPositiveButton(R.string.registration_confirm_button, (dialog, which) -> {
                    // Proceed with registration using saved details
                    performRegistration(slot, finalAbstract, finalTopic, finalSupervisorName, finalSupervisorEmail);
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                .show();
    }

    /**
     * Check registration limits early (before showing dialog)
     * Returns error message if limits are exceeded, null if OK
     */
    private String checkRegistrationLimitsEarly(ApiService.SlotCard slot) {
        if (slot == null) {
            return "Invalid slot";
        }
        
        // Check if already registered
        if (slot.alreadyRegistered) {
            return "You are already registered for this slot.";
        }
        
        // Check if slot is full (including pending registrations)
        // NOTE: Backend now properly enforces capacity limits (approved + pending >= capacity)
        // This client-side check provides immediate feedback and prevents unnecessary API calls
        int approved = (slot.enrolledCount > slot.approvedCount) ? slot.enrolledCount : slot.approvedCount;
        int pending = slot.pendingCount;
        int totalOccupied = approved + pending;
        int availableCapacity = slot.capacity - totalOccupied;
        boolean isFull = (totalOccupied >= slot.capacity);

        // Get student degree to check PhD capacity requirement
        String degree = preferencesManager.getDegree();
        boolean isPhd = "PhD".equalsIgnoreCase(degree);
        int requiredCapacity = isPhd ? 2 : 1; // PhD needs 2 slots, MSc needs 1

        // Log capacity check for debugging
        String capacityCheck = String.format("Slot %d capacity check: approved=%d, pending=%d, total=%d, capacity=%d, available=%d, isPhd=%s, isFull=%s",
            slot != null ? slot.slotId : "null", approved, pending, totalOccupied, slot != null ? slot.capacity : 0, availableCapacity, isPhd, isFull);
        Logger.i(Logger.TAG_UI, capacityCheck);
        if (serverLogger != null) {
            serverLogger.i(ServerLogger.TAG_UI, capacityCheck);
        }

        // Check if PhD student doesn't have enough capacity (needs 40 min = 2 spots)
        if (isPhd && availableCapacity > 0 && availableCapacity < requiredCapacity) {
            String phdMsg = "As a PhD student, your presentation requires 40 minutes.\n\n" +
                           "This seminar only has 20 minutes available.\n\n" +
                           "You can join the waiting list instead, and you'll be notified when enough time opens up.";
            Logger.w(Logger.TAG_UI, "Registration blocked - PhD needs 2 slots but only " + availableCapacity + " available: " + capacityCheck);
            if (serverLogger != null) {
                serverLogger.w(ServerLogger.TAG_UI, "Registration blocked - PhD needs 2 slots but only " + availableCapacity + " available: " + capacityCheck);
            }
            return phdMsg;
        }

        if (isFull) {
            String fullMsg = "This slot is full (" + totalOccupied + "/" + slot.capacity + ").\n\nYou can join the waiting list instead.";
            Logger.w(Logger.TAG_UI, "Registration blocked - slot is full: " + capacityCheck);
            if (serverLogger != null) {
                serverLogger.w(ServerLogger.TAG_UI, "Registration blocked - slot is full: " + capacityCheck);
            }
            return fullMsg;
        }
        
        // Count current registrations across all slots
        int approvedCount = 0;
        int pendingCount = 0;
        // NOTE: waitingListCount is NOT counted here - users can register even if on a waiting list
        
        for (ApiService.SlotCard s : currentSlots) {
            if (s.alreadyRegistered) {
                if ("APPROVED".equals(s.approvalStatus)) {
                    approvedCount++;
                } else if ("PENDING_APPROVAL".equals(s.approvalStatus)) {
                    pendingCount++;
                }
            }
            // NOTE: We don't check s.onWaitingList here - being on a waiting list
            // does NOT prevent registration for available slots
        }

        // Check presentation limit (1 approved at a time)
        // Note: Business rule is "once per degree" but not strictly enforced
        // Students typically won't want to repeat this experience
        if (approvedCount >= 1) {
            return "You already have an approved registration. " +
                   "You can only have one approved registration at a time.";
        }
        
        // Check pending limit (1 for PhD, 2 for MSC)
        int maxPending = isPhd ? 1 : 2;
        if (pendingCount >= maxPending) {
            return String.format("You can only have %d pending approval%s at once. " +
                               "Please wait for supervisor responses or cancel a pending registration.",
                               maxPending, maxPending == 1 ? "" : "s");
        }
        
        // NOTE: Waiting list check is NOT here - users CAN register for available slots
        // even if they are on a waiting list for another slot.
        // The waiting list limit (1 waiting list max) is only enforced when trying to JOIN
        // a waiting list, not when registering for an available slot.
        // See: onJoinWaitingList() for the waiting list limit check.
        
        // All checks passed
        return null;
    }

    /**
     * Check registration limits before allowing registration
     * Limits: 1 approved at a time, 2 pending for MSC / 1 pending for PhD
     * Note: Business rule is "once per degree" but not strictly enforced
     * 
     * IMPORTANT: Being on a waiting list does NOT prevent registration for available slots.
     * The waiting list limit (1 waiting list max) is only enforced when trying to JOIN
     * a waiting list, not when registering. See: onJoinWaitingList() for waiting list limit check.
     */
    private void checkRegistrationLimits(ApiService.SlotCard slot, Runnable onSuccess) {
        // Count current registrations
        int approvedCount = 0;
        int pendingCount = 0;
        // NOTE: waitingListCount is NOT counted here - users can register even if on a waiting list
        
        for (ApiService.SlotCard s : currentSlots) {
            if (s.alreadyRegistered) {
                if ("APPROVED".equals(s.approvalStatus)) {
                    approvedCount++;
                } else if ("PENDING_APPROVAL".equals(s.approvalStatus)) {
                    pendingCount++;
                }
            }
            // NOTE: We don't check s.onWaitingList here - being on a waiting list
            // does NOT prevent registration for available slots
        }
        
        // Get student degree to determine limits
        String degree = preferencesManager.getDegree();
        boolean isPhd = "PHD".equals(degree);
        
        // Check presentation limit (1 approved at a time)
        // Note: Business rule is "once per degree" but not strictly enforced
        if (approvedCount >= 1) {
            String limitMsg = "Registration limit reached: Already have 1 approved registration (max 1 at a time)";
            Logger.w(Logger.TAG_UI, limitMsg);
            if (serverLogger != null) {
                serverLogger.w(ServerLogger.TAG_UI, limitMsg + ", slot=" + (slot != null ? slot.slotId : "null"));
            }
            Toast.makeText(this, 
                "You already have an approved registration. " +
                "You can only have one approved registration at a time.",
                Toast.LENGTH_LONG).show();
            return;
        }
        
        // Check pending limit (1 for PhD, 2 for MSC)
        int maxPending = isPhd ? 1 : 2;
        if (pendingCount >= maxPending) {
            String limitMsg = String.format("Registration limit reached: %d pending approvals (max %d for %s)",
                    pendingCount, maxPending, isPhd ? "PhD" : "MSC");
            Logger.w(Logger.TAG_UI, limitMsg);
            if (serverLogger != null) {
                serverLogger.w(ServerLogger.TAG_UI, limitMsg + ", slot=" + (slot != null ? slot.slotId : "null"));
            }
            Toast.makeText(this, 
                String.format("You can only have %d pending approval%s at once. " +
                             "Please wait for supervisor responses or cancel a pending registration.",
                             maxPending, maxPending == 1 ? "" : "s"),
                Toast.LENGTH_LONG).show();
            return;
        }
        
        // NOTE: Waiting list check is NOT here - users CAN register for available slots
        // even if they are on a waiting list for another slot.
        // The waiting list limit (1 waiting list max) is only enforced when trying to JOIN
        // a waiting list, not when registering for an available slot.
        // See: onJoinWaitingList() for the waiting list limit check.
        
        // All checks passed
        Logger.i(Logger.TAG_UI, "Registration limits check passed: approved=" + approvedCount + 
                ", pending=" + pendingCount);
        if (serverLogger != null) {
            serverLogger.i(ServerLogger.TAG_UI, "Registration limits check passed: approved=" + approvedCount + 
                    ", pending=" + pendingCount + ", slot=" + (slot != null ? slot.slotId : "null"));
        }
        onSuccess.run();
    }

    private void performRegistration(ApiService.SlotCard slot,
                                     @Nullable String seminarAbstract,
                                     @Nullable String topic,
                                     @NonNull String supervisorName,
                                     @NonNull String supervisorEmail) {
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
        String presenterEmailRaw = preferencesManager.getEmail();
        if (presenterEmailRaw != null) {
            presenterEmailRaw = presenterEmailRaw.trim();
        }
        final String presenterEmail = presenterEmailRaw;
        
        if (TextUtils.isEmpty(presenterEmail)) {
            String errorMsg = "Registration failed - presenter email is empty or null. User profile may not have email set.";
            Logger.e("EMAIL_" + Logger.TAG_UI, errorMsg);
            if (serverLogger != null) {
                serverLogger.e("EMAIL_" + ServerLogger.TAG_UI, errorMsg + ", username=" + username);
            }
            Toast.makeText(this, R.string.presenter_home_presenter_email_required, Toast.LENGTH_LONG).show();
            return;
        }
        
        // Log presenter email status for debugging
        Logger.i("EMAIL_" + Logger.TAG_UI, "Presenter email retrieved: " + (presenterEmail != null ? presenterEmail : "null"));
        if (serverLogger != null) {
            serverLogger.i("EMAIL_" + ServerLogger.TAG_UI, "Presenter email retrieved: " + (presenterEmail != null ? presenterEmail : "null") + 
                ", username=" + username);
        }

        final String normalizedUsername = username.trim().toLowerCase(Locale.US);
        // Make topic final for use in lambda
        final String finalTopic = topic;
        final String finalAbstract = seminarAbstract;
        
        // Normalize email addresses and names for use in lambda and callbacks
        final String normalizedSupervisorEmail = supervisorEmail != null ? supervisorEmail.trim() : null;
        final String normalizedSupervisorName = supervisorName != null ? supervisorName.trim() : null;
        final String normalizedPresenterEmail = presenterEmail != null ? presenterEmail.trim() : null;
        final String normalizedTopic = finalTopic != null ? finalTopic.trim() : null;
        final String normalizedAbstract = finalAbstract != null ? finalAbstract.trim() : null;
        
        // Double-check slot capacity before proceeding (defense in depth)
        // NOTE: Backend now properly enforces capacity limits, but we check here to provide
        // immediate feedback and prevent unnecessary API calls if data changed since last refresh
        int approved = (slot.enrolledCount > slot.approvedCount) ? slot.enrolledCount : slot.approvedCount;
        int pending = slot.pendingCount;
        int totalOccupied = approved + pending;
        int availableCapacity = slot.capacity - totalOccupied;
        boolean isFull = (totalOccupied >= slot.capacity);

        // Get student degree to check PhD capacity requirement
        String degree = preferencesManager.getDegree();
        boolean isPhd = "PhD".equalsIgnoreCase(degree);
        int requiredCapacity = isPhd ? 2 : 1;

        // Check if PhD student doesn't have enough capacity (needs 40 min = 2 spots)
        if (isPhd && availableCapacity > 0 && availableCapacity < requiredCapacity) {
            String phdMsg = "As a PhD student, your presentation requires 40 minutes.\n\n" +
                           "This seminar only has 20 minutes available.\n\n" +
                           "You can join the waiting list instead.";
            Logger.w(Logger.TAG_UI, "Registration blocked at performRegistration - PhD needs 40 min but only 20 min available");
            if (serverLogger != null) {
                serverLogger.w(ServerLogger.TAG_UI, "Registration blocked at performRegistration - PhD needs 2 slots but only " + availableCapacity + " available");
            }
            Toast.makeText(this, phdMsg, Toast.LENGTH_LONG).show();
            return;
        }

        if (isFull) {
            String fullMsg = "This slot is full (" + totalOccupied + "/" + slot.capacity + ").\n\nYou can join the waiting list instead.";
            Logger.w(Logger.TAG_UI, "Registration blocked at performRegistration - slot is full: slot=" +
                (slot != null ? slot.slotId : "null") + ", approved=" + approved + ", pending=" + pending +
                ", total=" + totalOccupied + ", capacity=" + (slot != null ? slot.capacity : 0));
            if (serverLogger != null) {
                serverLogger.w(ServerLogger.TAG_UI, "Registration blocked at performRegistration - slot is full: slot=" +
                    (slot != null ? slot.slotId : "null") + ", approved=" + approved + ", pending=" + pending +
                    ", total=" + totalOccupied + ", capacity=" + (slot != null ? slot.capacity : 0));
            }
            Toast.makeText(this, fullMsg, Toast.LENGTH_LONG).show();
            return;
        }
        
        // Check registration limits before proceeding
        checkRegistrationLimits(slot, () -> {
            // Supervisor info is now required (using normalized values from outer scope)
            ApiService.PresenterRegisterRequest request = new ApiService.PresenterRegisterRequest(
                normalizedTopic, normalizedAbstract, normalizedSupervisorName, normalizedSupervisorEmail, normalizedPresenterEmail);

            // Log email addresses and registration details for debugging email issues
            String emailLogMsg = String.format("Registration email details - slot=%d, presenterEmail=%s, supervisorEmail=%s, supervisorName=%s, topic=%s",
                slotId, 
                normalizedPresenterEmail != null ? normalizedPresenterEmail : "null",
                normalizedSupervisorEmail != null ? normalizedSupervisorEmail : "null",
                normalizedSupervisorName != null ? normalizedSupervisorName : "null",
                normalizedTopic != null ? normalizedTopic : "null");
            Logger.i("EMAIL_" + Logger.TAG_API, emailLogMsg);
            if (serverLogger != null) {
                serverLogger.i("EMAIL_" + ServerLogger.TAG_API, emailLogMsg);
            }
            
            // Validate email addresses before sending (with detailed error messages)
            if (TextUtils.isEmpty(normalizedSupervisorEmail)) {
                String emailError = "Supervisor email is empty or null";
                Logger.e("EMAIL_" + Logger.TAG_API, emailError);
                if (serverLogger != null) {
                    serverLogger.e("EMAIL_" + ServerLogger.TAG_API, emailError + ", slot=" + slotId);
                }
                Toast.makeText(PresenterSlotSelectionActivity.this, "Supervisor email is required", Toast.LENGTH_LONG).show();
                return;
            }
            
            if (!Patterns.EMAIL_ADDRESS.matcher(normalizedSupervisorEmail).matches()) {
                String emailError = "Invalid supervisor email format: " + normalizedSupervisorEmail;
                Logger.e("EMAIL_" + Logger.TAG_API, emailError);
                if (serverLogger != null) {
                    serverLogger.e("EMAIL_" + ServerLogger.TAG_API, emailError + ", slot=" + slotId);
                }
                Toast.makeText(PresenterSlotSelectionActivity.this, "Invalid supervisor email format", Toast.LENGTH_LONG).show();
                return;
            }
            
            if (TextUtils.isEmpty(normalizedPresenterEmail)) {
                String emailError = "Presenter email is empty or null";
                Logger.e("EMAIL_" + Logger.TAG_API, emailError);
                if (serverLogger != null) {
                    serverLogger.e("EMAIL_" + ServerLogger.TAG_API, emailError + ", slot=" + slotId + ", username=" + normalizedUsername);
                }
                Toast.makeText(PresenterSlotSelectionActivity.this, "Presenter email is required. Please update your profile.", Toast.LENGTH_LONG).show();
                return;
            }
            
            if (!Patterns.EMAIL_ADDRESS.matcher(normalizedPresenterEmail).matches()) {
                String emailError = "Invalid presenter email format: " + normalizedPresenterEmail;
                Logger.e("EMAIL_" + Logger.TAG_API, emailError);
                if (serverLogger != null) {
                    serverLogger.e("EMAIL_" + ServerLogger.TAG_API, emailError + ", slot=" + slotId + ", username=" + normalizedUsername);
                }
                Toast.makeText(PresenterSlotSelectionActivity.this, "Invalid presenter email format. Please update your profile.", Toast.LENGTH_LONG).show();
                return;
            }
            
            // Log request object details to verify serialization
            // This helps diagnose if backend receives the data correctly
            String requestDetails = String.format("Registration request object - topic=%s, supervisorName=%s, supervisorEmail=%s, presenterEmail=%s",
                request.topic != null ? request.topic : "null",
                request.supervisorName != null ? request.supervisorName : "null",
                request.supervisorEmail != null ? request.supervisorEmail : "null",
                request.presenterEmail != null ? request.presenterEmail : "null");
            Logger.i("EMAIL_" + Logger.TAG_API, requestDetails);
            if (serverLogger != null) {
                serverLogger.i("EMAIL_" + ServerLogger.TAG_API, requestDetails + ", slot=" + slotId);
            }
            
            // Additional validation to match backend requirements
            // Backend checks: email must contain "@" and be at least 5 chars
            if (normalizedSupervisorEmail != null && (normalizedSupervisorEmail.length() < 5 || !normalizedSupervisorEmail.contains("@"))) {
                String emailError = "Supervisor email does not meet minimum requirements (must contain @ and be at least 5 chars): " + normalizedSupervisorEmail;
                Logger.e("EMAIL_" + Logger.TAG_API, emailError);
                if (serverLogger != null) {
                    serverLogger.e("EMAIL_" + ServerLogger.TAG_API, emailError + ", slot=" + slotId);
                }
                Toast.makeText(PresenterSlotSelectionActivity.this, "Invalid supervisor email format", Toast.LENGTH_LONG).show();
                return;
            }
            
            if (normalizedPresenterEmail != null && (normalizedPresenterEmail.length() < 5 || !normalizedPresenterEmail.contains("@"))) {
                String emailError = "Presenter email does not meet minimum requirements (must contain @ and be at least 5 chars): " + normalizedPresenterEmail;
                Logger.e("EMAIL_" + Logger.TAG_API, emailError);
                if (serverLogger != null) {
                    serverLogger.e("EMAIL_" + ServerLogger.TAG_API, emailError + ", slot=" + slotId + ", username=" + normalizedUsername);
                }
                Toast.makeText(PresenterSlotSelectionActivity.this, "Invalid presenter email format. Please update your profile.", Toast.LENGTH_LONG).show();
                return;
            }
            
            // Log that all validations passed and request is ready to send
            String validationPassed = String.format("All email validations passed - supervisorEmail=%s (length=%d), presenterEmail=%s (length=%d)",
                normalizedSupervisorEmail, normalizedSupervisorEmail != null ? normalizedSupervisorEmail.length() : 0,
                normalizedPresenterEmail, normalizedPresenterEmail != null ? normalizedPresenterEmail.length() : 0);
            Logger.i("EMAIL_" + Logger.TAG_API, validationPassed);
            if (serverLogger != null) {
                serverLogger.i("EMAIL_" + ServerLogger.TAG_API, validationPassed + ", slot=" + slotId);
            }

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
                        
                        // Log response body for email debugging
                        if (response.body() != null) {
                            ApiService.PresenterRegisterResponse regResponse = response.body();
                            String responseLog = String.format("Registration response - ok=%s, code=%s, message=%s, registrationId=%s, approvalStatus=%s",
                                regResponse.ok,
                                regResponse.code != null ? regResponse.code : "null",
                                regResponse.message != null ? regResponse.message : "null",
                                regResponse.registrationId != null ? regResponse.registrationId.toString() : "null",
                                regResponse.approvalStatus != null ? regResponse.approvalStatus : "null");
                            Logger.i(Logger.TAG_API, responseLog);
                            if (serverLogger != null) {
                                serverLogger.i(ServerLogger.TAG_API, responseLog);
                            }
                            
                            // Log email-related info from response
                            if (regResponse.message != null && (regResponse.message.toLowerCase().contains("email") || 
                                regResponse.message.toLowerCase().contains("mail"))) {
                                String emailMsg = "Email-related message in registration response: " + regResponse.message;
                                Logger.w("EMAIL_" + Logger.TAG_API, emailMsg);
                                if (serverLogger != null) {
                                    serverLogger.w("EMAIL_" + ServerLogger.TAG_API, emailMsg);
                                }
                            }
                            
                            // If registration was successful, log that backend should have sent email
                            // Backend error codes to watch for in app_logs:
                            // - EMAIL_MAILSENDER_NULL: JavaMailSender not configured
                            // - REGISTRATION_NO_SUPERVISOR_EMAIL: Supervisor email missing
                            // - REGISTRATION_INVALID_EMAIL_FORMAT: Invalid email format
                            // - EMAIL_AUTH_FAILED: SMTP authentication failed
                            // - EMAIL_SEND_FAILED: Email send failed
                            // - REGISTRATION_NOT_FOUND_AFTER_SAVE: Registration not persisted
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
                                    Logger.i(Logger.TAG_API, "Registration error response body: " + errorBodyString);
                                    if (serverLogger != null) {
                                        serverLogger.i(ServerLogger.TAG_API, "Registration error response body: " + errorBodyString + 
                                            ", slot=" + slotId + ", supervisorEmail=" + supervisorEmail + ", presenterEmail=" + presenterEmail);
                                    }
                                    
                                    // Check if error mentions email issues
                                    if (errorBodyString.toLowerCase().contains("email") || errorBodyString.toLowerCase().contains("mail")) {
                                        String emailError = "Email-related error in registration: " + errorBodyString;
                                        Logger.e("EMAIL_" + Logger.TAG_API, emailError);
                                        if (serverLogger != null) {
                                            serverLogger.e("EMAIL_" + ServerLogger.TAG_API, emailError + 
                                                ", slot=" + slotId + ", supervisorEmail=" + supervisorEmail + ", presenterEmail=" + presenterEmail);
                                        }
                                    }
                                    
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
                                        
                                        // Check for email-related errors
                                        if (jsonObject.has("error") && jsonObject.get("error").isJsonPrimitive()) {
                                            String errorField = jsonObject.get("error").getAsString();
                                            if (errorField != null && (errorField.toLowerCase().contains("email") || 
                                                errorField.toLowerCase().contains("mail"))) {
                                                String emailError = "Email error in registration response: " + errorField;
                                                Logger.e("EMAIL_" + Logger.TAG_API, emailError);
                                                if (serverLogger != null) {
                                                    serverLogger.e("EMAIL_" + ServerLogger.TAG_API, emailError + 
                                                        ", slot=" + slotId + ", supervisorEmail=" + supervisorEmail);
                                                }
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
                                errorMessage = ErrorMessageHelper.cleanBackendMessage(rawErrorMessage);
                            }
                            
                            // Get user-friendly error message
                            String userMessage = ErrorMessageHelper.getRegistrationErrorMessage(
                                PresenterSlotSelectionActivity.this, errorCode, errorMessage);
                            Toast.makeText(PresenterSlotSelectionActivity.this, userMessage, Toast.LENGTH_LONG).show();
                            
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
                            case "PENDING_APPROVAL":
                                Logger.i(Logger.TAG_API, "Registration pending approval for slot=" + slot.slotId);
                                if (serverLogger != null) {
                                    String pendingLog = String.format("Registration pending approval - slot=%d, presenter=%s, supervisorEmail=%s, presenterEmail=%s, supervisorName=%s",
                                        slot.slotId, normalizedUsername, normalizedSupervisorEmail, normalizedPresenterEmail, normalizedSupervisorName);
                                    serverLogger.i("EMAIL_" + ServerLogger.TAG_API, pendingLog);
                                    // Log that email should have been sent to supervisor
                                    String emailExpectedLog = String.format("Expected email to supervisor - slot=%d, supervisorEmail=%s, supervisorName=%s, presenterEmail=%s",
                                        slot.slotId, normalizedSupervisorEmail, normalizedSupervisorName, normalizedPresenterEmail);
                                    Logger.i("EMAIL_" + Logger.TAG_API, emailExpectedLog);
                                    serverLogger.i("EMAIL_" + ServerLogger.TAG_API, emailExpectedLog);
                                }
                                // Show enhanced message with supervisor name
                                String pendingMessage;
                                if (!TextUtils.isEmpty(normalizedSupervisorName)) {
                                    pendingMessage = getString(R.string.presenter_home_register_pending_with_supervisor, normalizedSupervisorName);
                                } else {
                                    pendingMessage = getString(R.string.presenter_home_register_pending_generic);
                                }
                                Toast.makeText(PresenterSlotSelectionActivity.this, pendingMessage, Toast.LENGTH_LONG).show();
                                loadSlots(); // Refresh to show pending status
                                break;
                            case "REGISTERED":
                                Logger.i(Logger.TAG_API, "Registration successful for slot=" + slot.slotId);
                                if (serverLogger != null) {
                                    String successLog = String.format("Registration successful - slot=%d, presenter=%s, supervisorEmail=%s, presenterEmail=%s, supervisorName=%s",
                                        slot.slotId, normalizedUsername, normalizedSupervisorEmail, normalizedPresenterEmail, normalizedSupervisorName);
                                    serverLogger.i("EMAIL_" + ServerLogger.TAG_API, successLog);
                                    // Log that email should have been sent to supervisor
                                    String emailExpectedLog = String.format("Expected email to supervisor - slot=%d, supervisorEmail=%s, supervisorName=%s, presenterEmail=%s",
                                        slot.slotId, normalizedSupervisorEmail, normalizedSupervisorName, normalizedPresenterEmail);
                                    Logger.i("EMAIL_" + Logger.TAG_API, emailExpectedLog);
                                    serverLogger.i("EMAIL_" + ServerLogger.TAG_API, emailExpectedLog);
                                }
                                Toast.makeText(PresenterSlotSelectionActivity.this, R.string.presenter_home_register_success, Toast.LENGTH_LONG).show();

                                // Supervisor invitation is now handled automatically by backend
                                // No need to show post-registration dialog

                                loadSlots();
                                break;
                            case "ALREADY_IN_SLOT":
                            case "ALREADY_REGISTERED":
                                // Use backend message if available, otherwise use default
                                String alreadyRegisteredMsg = body.message != null && !body.message.trim().isEmpty()
                                    ? body.message
                                    : getString(R.string.presenter_home_register_already);
                                Toast.makeText(PresenterSlotSelectionActivity.this, alreadyRegisteredMsg, Toast.LENGTH_LONG).show();
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
                        String errorDetails = String.format("Slot registration network failure - URL: %s, Error: %s, Message: %s, slot=%d, supervisorEmail=%s, presenterEmail=%s",
                            requestUrl, t.getClass().getSimpleName(), t.getMessage(), slotId, supervisorEmail, presenterEmail);
                        Logger.e(Logger.TAG_API, errorDetails, t);
                        if (serverLogger != null) {
                            serverLogger.e(ServerLogger.TAG_API, errorDetails, t);
                        }
                        
                        // Note: If registration fails due to network error, email won't be sent
                        String emailNote = String.format("Registration failed - email NOT sent due to network error - slot=%d, supervisorEmail=%s",
                            slotId, supervisorEmail);
                        Logger.w("EMAIL_" + Logger.TAG_API, emailNote);
                        if (serverLogger != null) {
                            serverLogger.w("EMAIL_" + ServerLogger.TAG_API, emailNote);
                        }
                        
                        String errorMessage = ErrorMessageHelper.getNetworkErrorMessage(
                            PresenterSlotSelectionActivity.this, t);
                        Toast.makeText(PresenterSlotSelectionActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
        }); // Close checkRegistrationLimits lambda
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
          if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    /**
     * Snapshot of slot state for comparison
     */
    private static class SlotStateSnapshot {
        Long slotId;
        boolean onWaitingList;
        String approvalStatus;
        boolean alreadyRegistered;
        
        SlotStateSnapshot(ApiService.SlotCard slot) {
            this.slotId = slot.slotId;
            this.onWaitingList = slot.onWaitingList;
            this.approvalStatus = slot.approvalStatus;
            this.alreadyRegistered = slot.alreadyRegistered;
        }
    }
    
    /**
     * Check if user was approved from waiting list and if other pending registrations were cancelled
     */
    private void checkForWaitingListApprovalAndCancellations(ApiService.PresenterHomeResponse response) {
        if (response == null || response.slotCatalog == null) {
            return;
        }
        
        // Create snapshot of current states
        List<SlotStateSnapshot> currentStates = new ArrayList<>();
        for (ApiService.SlotCard slot : response.slotCatalog) {
            currentStates.add(new SlotStateSnapshot(slot));
        }
        
        // If we have previous states, compare them
        if (!previousSlotStates.isEmpty()) {
            Long approvedFromWaitingListSlotId = null;
            int cancelledPendingCount = 0;
            List<String> cancelledSlotDetails = new ArrayList<>();
            
            // Find slot where user was on waiting list and is now approved
            for (SlotStateSnapshot previous : previousSlotStates) {
                if (previous.onWaitingList && !"APPROVED".equals(previous.approvalStatus)) {
                    // User was on waiting list before
                    SlotStateSnapshot current = findSlotState(currentStates, previous.slotId);
                    if (current != null && "APPROVED".equals(current.approvalStatus)) {
                        // User is now approved - they were approved from waiting list!
                        approvedFromWaitingListSlotId = previous.slotId;
                        Logger.i(Logger.TAG_UI, "User approved from waiting list for slot=" + previous.slotId);
                        if (serverLogger != null) {
                            serverLogger.i(ServerLogger.TAG_UI, "User approved from waiting list for slot=" + previous.slotId);
                        }
                    }
                }
                
                // Check if user had pending registrations that are now cancelled
                if ("PENDING_APPROVAL".equals(previous.approvalStatus) && previous.alreadyRegistered) {
                    SlotStateSnapshot current = findSlotState(currentStates, previous.slotId);
                    if (current != null && !current.alreadyRegistered && 
                        !"PENDING_APPROVAL".equals(current.approvalStatus) && 
                        !"APPROVED".equals(current.approvalStatus)) {
                        // This pending registration was cancelled
                        cancelledPendingCount++;
                        // Try to get slot details for the message
                        ApiService.SlotCard slot = findSlot(response.slotCatalog, previous.slotId);
                        if (slot != null) {
                            String slotInfo = slot.date != null ? slot.date : "Slot " + previous.slotId;
                            if (slot.timeRange != null) {
                                slotInfo += " " + slot.timeRange;
                            }
                            cancelledSlotDetails.add(slotInfo);
                        }
                        Logger.i(Logger.TAG_UI, "Pending registration cancelled for slot=" + previous.slotId);
                        if (serverLogger != null) {
                            serverLogger.i(ServerLogger.TAG_UI, "Pending registration cancelled for slot=" + previous.slotId);
                        }
                    }
                }
            }
            
            // Show dialog if user was approved from waiting list
            if (approvedFromWaitingListSlotId != null) {
                showWaitingListApprovalDialog(response.slotCatalog, approvedFromWaitingListSlotId, cancelledPendingCount, cancelledSlotDetails);
            }
        }
        
        // Update previous states for next comparison
        previousSlotStates = currentStates;
    }
    
    private SlotStateSnapshot findSlotState(List<SlotStateSnapshot> states, Long slotId) {
        if (slotId == null) return null;
        for (SlotStateSnapshot state : states) {
            if (slotId.equals(state.slotId)) {
                return state;
            }
        }
        return null;
    }
    
    private ApiService.SlotCard findSlot(List<ApiService.SlotCard> slots, Long slotId) {
        if (slotId == null) return null;
        for (ApiService.SlotCard slot : slots) {
            if (slotId.equals(slot.slotId)) {
                return slot;
            }
        }
        return null;
    }
    
    /**
     * Show dialog explaining waiting list approval and cancelled registrations
     */
    private void showWaitingListApprovalDialog(List<ApiService.SlotCard> allSlots, Long approvedSlotId, int cancelledPendingCount, List<String> cancelledSlotDetails) {
        // Get approved slot details from the response slots
        ApiService.SlotCard approvedSlot = findSlot(allSlots, approvedSlotId);
        
        StringBuilder message = new StringBuilder();
        
        // Main approval message
        if (approvedSlot != null) {
            String slotInfo = approvedSlot.date != null ? approvedSlot.date : "Slot " + approvedSlotId;
            if (approvedSlot.timeRange != null) {
                slotInfo += " " + approvedSlot.timeRange;
            }
            message.append(getString(R.string.waiting_list_approved_message, slotInfo));
        } else {
            message.append(getString(R.string.waiting_list_approved_message_generic));
        }
        
        // Add information about cancelled pending registrations
        if (cancelledPendingCount > 0) {
            message.append("\n\n");
            if (cancelledPendingCount == 1) {
                message.append(getString(R.string.pending_registration_cancelled_single));
                if (!cancelledSlotDetails.isEmpty()) {
                    message.append(" ").append(cancelledSlotDetails.get(0));
                }
            } else {
                message.append(getString(R.string.pending_registrations_cancelled_multiple, cancelledPendingCount));
            }
            message.append(" ").append(getString(R.string.pending_cancelled_reason));
        }
        
        new AlertDialog.Builder(this)
            .setTitle(R.string.waiting_list_approved_title)
            .setMessage(message.toString())
            .setPositiveButton(android.R.string.ok, null)
            .setIcon(android.R.drawable.ic_dialog_info)
            .show();
        
        Logger.userAction("Waiting List Approved", "User was approved from waiting list for slot=" + approvedSlotId + 
            ", cancelled pending=" + cancelledPendingCount);
        if (serverLogger != null) {
            serverLogger.userAction("Waiting List Approved", "User was approved from waiting list for slot=" + approvedSlotId + 
                ", cancelled pending=" + cancelledPendingCount);
        }
    }
}