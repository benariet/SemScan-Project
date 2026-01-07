package org.example.semscan.ui.teacher;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.example.semscan.R;
import org.example.semscan.data.api.ApiClient;
import org.example.semscan.data.api.ApiService;
import org.example.semscan.utils.Logger;
import org.example.semscan.utils.PreferencesManager;
import org.example.semscan.utils.ServerLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PresenterMySlotActivity extends AppCompatActivity {

    private View layoutSlotDetails;
    private View layoutWaitingList;
    private View layoutEmpty;
    private ProgressBar progressBar;
    private TextView textSlotTitle;
    private TextView textSlotSchedule;
    private TextView textSlotLocation;
    private TextView textSlotPresenters;
    private Button btnCancel;
    private MaterialButton btnGoToSlots;

    // Waiting list views
    private TextView textWaitingPosition;
    private TextView textWaitingSlotTitle;
    private TextView textWaitingSlotSchedule;
    private TextView textWaitingSlotLocation;
    private TextView textWaitingSlotPresenters;
    private Button btnCancelWaitingList;

    private PreferencesManager preferencesManager;
    private ApiService apiService;
    private ServerLogger serverLogger;

    private ApiService.MySlotSummary currentSlot;
    private ApiService.WaitingListSlotSummary currentWaitingListSlot;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_presenter_my_slot);

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

        loadSlot();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void initializeViews() {
        layoutSlotDetails = findViewById(R.id.container_slot_details);
        layoutWaitingList = findViewById(R.id.container_waiting_list);
        layoutEmpty = findViewById(R.id.container_empty_state);
        progressBar = findViewById(R.id.progress_bar);
        textSlotTitle = findViewById(R.id.text_slot_title);
        textSlotSchedule = findViewById(R.id.text_slot_schedule);
        textSlotLocation = findViewById(R.id.text_slot_location);
        textSlotPresenters = findViewById(R.id.text_slot_presenters);
        btnCancel = findViewById(R.id.btn_cancel_slot);
        btnGoToSlots = findViewById(R.id.btn_go_to_slots);

        // Waiting list views
        textWaitingPosition = findViewById(R.id.text_waiting_position);
        textWaitingSlotTitle = findViewById(R.id.text_waiting_slot_title);
        textWaitingSlotSchedule = findViewById(R.id.text_waiting_slot_schedule);
        textWaitingSlotLocation = findViewById(R.id.text_waiting_slot_location);
        textWaitingSlotPresenters = findViewById(R.id.text_waiting_slot_presenters);
        btnCancelWaitingList = findViewById(R.id.btn_cancel_waiting_list);
    }

    private void setupInteractions() {
        btnCancel.setOnClickListener(v -> cancelRegistration());
        btnGoToSlots.setOnClickListener(v -> openSlotSelection());
        btnCancelWaitingList.setOnClickListener(v -> cancelWaitingList());
    }

    private void openSlotSelection() {
        Intent intent = new Intent(this, PresenterSlotSelectionActivity.class);
        intent.putExtra(PresenterSlotSelectionActivity.EXTRA_SCROLL_TO_MY_SLOT, true);
        startActivity(intent);
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void loadSlot() {
        final String username = preferencesManager.getUserName();
        if (TextUtils.isEmpty(username)) {
            Toast.makeText(this, R.string.presenter_my_slot_no_user_error, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setLoading(true);
        apiService.getPresenterHome(username.trim().toLowerCase(Locale.US)).enqueue(new Callback<ApiService.PresenterHomeResponse>() {
            @Override
            public void onResponse(Call<ApiService.PresenterHomeResponse> call, Response<ApiService.PresenterHomeResponse> response) {
                setLoading(false);
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(PresenterMySlotActivity.this, R.string.error_slot_load_failed, Toast.LENGTH_LONG).show();
                    return;
                }
                currentSlot = response.body().mySlot;
                currentWaitingListSlot = response.body().myWaitingListSlot;
                renderSlot();
                renderWaitingListSlot();
            }

            @Override
            public void onFailure(Call<ApiService.PresenterHomeResponse> call, Throwable t) {
                setLoading(false);
                String errorDetails = "Failed to load my slot - Error: " + t.getClass().getSimpleName() + ", Message: " + t.getMessage();
                Logger.e(Logger.TAG_SLOTS_LOAD, errorDetails, t);
                if (serverLogger != null) {
                    serverLogger.e(ServerLogger.TAG_SLOTS_LOAD, errorDetails, t);
                }
                String errorMessage = getString(R.string.error_slot_load_failed);
                if (t instanceof java.net.SocketTimeoutException || t instanceof java.net.ConnectException) {
                    errorMessage = getString(R.string.error_network_timeout);
                } else if (t instanceof java.net.UnknownHostException) {
                    errorMessage = getString(R.string.error_server_unavailable);
                }
                Toast.makeText(PresenterMySlotActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void renderSlot() {
        boolean hasSlot = currentSlot != null && currentSlot.slotId != null;
        boolean hasWaitingList = currentWaitingListSlot != null && currentWaitingListSlot.slotId != null;

        if (!hasSlot) {
            layoutSlotDetails.setVisibility(View.GONE);
            // Only show empty state if also no waiting list
            layoutEmpty.setVisibility(hasWaitingList ? View.GONE : View.VISIBLE);
            return;
        }

        layoutSlotDetails.setVisibility(View.VISIBLE);
        layoutEmpty.setVisibility(View.GONE);

        textSlotTitle.setText(formatTitle(currentSlot));
        textSlotSchedule.setText(formatSchedule(currentSlot));

        String location = buildLocation(currentSlot);
        textSlotLocation.setText(location);
        textSlotLocation.setVisibility(TextUtils.isEmpty(location) ? View.GONE : View.VISIBLE);

        String presenters = formatPresenters(currentSlot.coPresenters);
        if (TextUtils.isEmpty(presenters)) {
            textSlotPresenters.setVisibility(View.GONE);
        } else {
            textSlotPresenters.setVisibility(View.VISIBLE);
            textSlotPresenters.setText(presenters);
        }
    }

    private void renderWaitingListSlot() {
        if (currentWaitingListSlot == null || currentWaitingListSlot.slotId == null) {
            layoutWaitingList.setVisibility(View.GONE);
            return;
        }

        layoutWaitingList.setVisibility(View.VISIBLE);

        // Show position in queue
        textWaitingPosition.setText(getString(R.string.presenter_my_slot_waiting_position,
                currentWaitingListSlot.position, currentWaitingListSlot.totalInQueue));

        // Format title
        String day = currentWaitingListSlot.dayOfWeek != null ? currentWaitingListSlot.dayOfWeek : "";
        String date = currentWaitingListSlot.date != null ? currentWaitingListSlot.date : "";
        textWaitingSlotTitle.setText(getString(R.string.presenter_home_slot_title_format, day, date));

        // Time range
        textWaitingSlotSchedule.setText(currentWaitingListSlot.timeRange != null ? currentWaitingListSlot.timeRange : "");

        // Location
        List<String> parts = new ArrayList<>();
        if (!TextUtils.isEmpty(currentWaitingListSlot.room)) {
            parts.add(getString(R.string.room_with_label, currentWaitingListSlot.room));
        }
        if (!TextUtils.isEmpty(currentWaitingListSlot.building)) {
            parts.add(getString(R.string.building_with_label, currentWaitingListSlot.building));
        }
        String location = TextUtils.join(" • ", parts);
        textWaitingSlotLocation.setText(location);
        textWaitingSlotLocation.setVisibility(TextUtils.isEmpty(location) ? View.GONE : View.VISIBLE);

        // Registered presenters
        String presenters = formatPresenters(currentWaitingListSlot.registeredPresenters);
        if (TextUtils.isEmpty(presenters)) {
            textWaitingSlotPresenters.setVisibility(View.GONE);
        } else {
            textWaitingSlotPresenters.setVisibility(View.VISIBLE);
            textWaitingSlotPresenters.setText(presenters);
        }
    }

    private String formatTitle(ApiService.MySlotSummary summary) {
        String day = summary.dayOfWeek != null ? summary.dayOfWeek : "";
        String date = summary.date != null ? summary.date : "";
        return getString(R.string.presenter_home_slot_title_format, day, date);
    }

    private String formatSchedule(ApiService.MySlotSummary summary) {
        return summary.timeRange != null ? summary.timeRange : "";
    }

    private String buildLocation(ApiService.MySlotSummary summary) {
        List<String> parts = new ArrayList<>();
        if (!TextUtils.isEmpty(summary.room)) {
            parts.add(getString(R.string.room_with_label, summary.room));
        }
        if (!TextUtils.isEmpty(summary.building)) {
            parts.add(getString(R.string.building_with_label, summary.building));
        }
        return TextUtils.join(" • ", parts);
    }

    private String formatPresenters(List<ApiService.PresenterCoPresenter> presenters) {
        if (presenters == null || presenters.isEmpty()) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        for (ApiService.PresenterCoPresenter presenter : presenters) {
            StringBuilder builder = new StringBuilder();
            if (!TextUtils.isEmpty(presenter.name)) {
                builder.append(presenter.name);
            }
            if (!TextUtils.isEmpty(presenter.topic)) {
                if (builder.length() > 0) {
                    builder.append(" — ");
                }
                builder.append(presenter.topic);
            }
            if (builder.length() > 0) {
                lines.add(builder.toString());
            }
        }
        return lines.isEmpty() ? null : TextUtils.join("\n", lines);
    }

    private void cancelRegistration() {
        Logger.userAction("Cancel Registration", "User clicked cancel registration");
        if (serverLogger != null) {
            serverLogger.userAction("Cancel Registration", "User clicked cancel registration for slot=" + 
                    (currentSlot != null ? currentSlot.slotId : "null"));
        }
        
        if (currentSlot == null || currentSlot.slotId == null) {
            Logger.e(Logger.TAG_CANCEL_REQUEST, "Cancel registration failed - slot is null");
            if (serverLogger != null) {
                serverLogger.e(ServerLogger.TAG_CANCEL_REQUEST, "Cancel registration failed - slot is null");
            }
            Toast.makeText(this, R.string.presenter_my_slot_no_slot_error, Toast.LENGTH_LONG).show();
            return;
        }
        final String username = preferencesManager.getUserName();
        if (TextUtils.isEmpty(username)) {
            Logger.e(Logger.TAG_CANCEL_REQUEST, "Cancel registration failed - username is empty");
            if (serverLogger != null) {
                serverLogger.e(ServerLogger.TAG_CANCEL_REQUEST, "Cancel registration failed - username is empty");
            }
            Toast.makeText(this, R.string.presenter_my_slot_no_user_error, Toast.LENGTH_LONG).show();
            return;
        }

        final String normalizedUsername = username.trim().toLowerCase(Locale.US);
        String apiEndpoint = "api/v1/presenters/" + normalizedUsername + "/home/slots/" + currentSlot.slotId + "/cancel";
        String apiMessage = "Cancelling registration for slot=" + currentSlot.slotId;
        Logger.api("DELETE", apiEndpoint, apiMessage);
        if (serverLogger != null) {
            serverLogger.api("DELETE", apiEndpoint, apiMessage);
        }

        setLoading(true);
        apiService.cancelSlotRegistration(username.trim().toLowerCase(Locale.US), currentSlot.slotId)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        setLoading(false);
                        String apiEndpoint = "api/v1/presenters/" + normalizedUsername + "/home/slots/" + currentSlot.slotId + "/cancel";
                        Logger.apiResponse("DELETE", apiEndpoint, response.code(), "Cancel registration response received");
                        if (serverLogger != null) {
                            serverLogger.apiResponse("DELETE", apiEndpoint, response.code(), "Cancel registration response received");
                        }
                        
                        if (response.isSuccessful()) {
                            Logger.i(Logger.TAG_CANCEL_SUCCESS, "Successfully cancelled registration for slot=" + currentSlot.slotId);
                            if (serverLogger != null) {
                                serverLogger.i(ServerLogger.TAG_CANCEL_SUCCESS, "Successfully cancelled registration for slot=" + currentSlot.slotId +
                                        ", user=" + normalizedUsername);
                            }
                            Toast.makeText(PresenterMySlotActivity.this, R.string.presenter_my_slot_cancel_success, Toast.LENGTH_LONG).show();
                            loadSlot();
                        } else {
                            // Try to read error body to get actual error message
                            String errorMessage = getString(R.string.presenter_my_slot_cancel_error);
                            if (response.errorBody() != null) {
                                try {
                                    String errorBodyString = response.errorBody().string();
                                    Logger.i(Logger.TAG_CANCEL_RESPONSE, "Cancel registration error response body: " + errorBodyString);
                                    
                                    // Try to parse JSON error body
                                    try {
                                        JsonObject jsonObject = new JsonParser().parse(errorBodyString).getAsJsonObject();
                                        
                                        // Extract message
                                        if (jsonObject.has("message") && jsonObject.get("message").isJsonPrimitive()) {
                                            errorMessage = jsonObject.get("message").getAsString();
                                        }
                                    } catch (Exception e) {
                                        // If JSON parsing fails, try manual extraction
                                        int messageStart = errorBodyString.indexOf("\"message\":\"");
                                        if (messageStart >= 0) {
                                            messageStart += 10; // Length of "\"message\":\""
                                            int messageEnd = errorBodyString.indexOf("\"", messageStart);
                                            if (messageEnd > messageStart) {
                                                errorMessage = errorBodyString.substring(messageStart, messageEnd);
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    Logger.e(Logger.TAG_CANCEL_FAILED, "Failed to read error body", e);
                                    if (serverLogger != null) {
                                        serverLogger.e(ServerLogger.TAG_CANCEL_FAILED, "Failed to read error body", e);
                                    }
                                }
                            }
                            Logger.apiError("DELETE", apiEndpoint, response.code(), "Failed to cancel registration");
                            if (serverLogger != null) {
                                serverLogger.apiError("DELETE", apiEndpoint, response.code(), "Failed to cancel registration");
                            }
                            Toast.makeText(PresenterMySlotActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        setLoading(false);
                        String requestUrl = call.request() != null ? call.request().url().toString() : "unknown";
                        String errorDetails = "Cancel registration network failure - URL: " + requestUrl +
                                ", Error: " + t.getClass().getSimpleName() + ", Message: " + t.getMessage();
                        Logger.e(Logger.TAG_CANCEL_FAILED, errorDetails, t);
                        if (serverLogger != null) {
                            serverLogger.e(ServerLogger.TAG_CANCEL_FAILED, errorDetails, t);
                        }
                        Toast.makeText(PresenterMySlotActivity.this, R.string.presenter_my_slot_cancel_error, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void cancelWaitingList() {
        Logger.userAction("Cancel Waiting List", "User clicked cancel waiting list");
        if (serverLogger != null) {
            serverLogger.userAction("Cancel Waiting List", "User clicked cancel waiting list for slot=" +
                    (currentWaitingListSlot != null ? currentWaitingListSlot.slotId : "null"));
        }

        if (currentWaitingListSlot == null || currentWaitingListSlot.slotId == null) {
            Toast.makeText(this, R.string.presenter_my_slot_cancel_waiting_list_error, Toast.LENGTH_LONG).show();
            return;
        }

        final String username = preferencesManager.getUserName();
        if (TextUtils.isEmpty(username)) {
            Toast.makeText(this, R.string.presenter_my_slot_no_user_error, Toast.LENGTH_LONG).show();
            return;
        }

        final String normalizedUsername = username.trim().toLowerCase(Locale.US);
        final Long slotId = currentWaitingListSlot.slotId;

        setLoading(true);
        apiService.leaveWaitingList(slotId, normalizedUsername)
                .enqueue(new Callback<ApiService.WaitingListResponse>() {
                    @Override
                    public void onResponse(Call<ApiService.WaitingListResponse> call, Response<ApiService.WaitingListResponse> response) {
                        setLoading(false);
                        if (response.isSuccessful()) {
                            Logger.i(Logger.TAG_CANCEL_SUCCESS, "Successfully cancelled waiting list for slot=" + slotId);
                            if (serverLogger != null) {
                                serverLogger.i(ServerLogger.TAG_CANCEL_SUCCESS, "Successfully cancelled waiting list for slot=" + slotId +
                                        ", user=" + normalizedUsername);
                            }
                            Toast.makeText(PresenterMySlotActivity.this, R.string.presenter_my_slot_cancel_waiting_list_success, Toast.LENGTH_LONG).show();
                            loadSlot();
                        } else {
                            Toast.makeText(PresenterMySlotActivity.this, R.string.presenter_my_slot_cancel_waiting_list_error, Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.WaitingListResponse> call, Throwable t) {
                        setLoading(false);
                        Logger.e(Logger.TAG_CANCEL_FAILED, "Cancel waiting list network failure", t);
                        if (serverLogger != null) {
                            serverLogger.e(ServerLogger.TAG_CANCEL_FAILED, "Cancel waiting list network failure", t);
                        }
                        Toast.makeText(PresenterMySlotActivity.this, R.string.presenter_my_slot_cancel_waiting_list_error, Toast.LENGTH_LONG).show();
                    }
                });
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
