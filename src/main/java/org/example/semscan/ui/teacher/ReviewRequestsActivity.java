package org.example.semscan.ui.teacher;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import org.example.semscan.R;
import org.example.semscan.data.api.ApiClient;
import org.example.semscan.data.api.ApiService;
import org.example.semscan.data.model.ManualAttendanceResponse;
import org.example.semscan.utils.Logger;
import org.example.semscan.utils.PreferencesManager;
import org.example.semscan.utils.ToastUtils;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Activity for reviewing manual attendance requests before export.
 * Displays pending requests and allows presenter to approve/reject them.
 */
public class ReviewRequestsActivity extends AppCompatActivity {

    public static final String EXTRA_SESSION_ID = "sessionId";
    public static final int RESULT_CONTINUE_EXPORT = RESULT_OK;
    public static final int RESULT_CANCELLED = RESULT_CANCELED;

    private TextView textPendingCount;
    private RecyclerView recyclerRequests;
    private MaterialButton btnApproveAllSafe;
    private MaterialButton btnRejectDuplicates;
    private MaterialButton btnCancel;
    private MaterialButton btnContinueExport;

    private ManualRequestAdapter requestAdapter;
    private ApiService apiService;
    private PreferencesManager preferencesManager;

    private Long sessionId;
    private List<ManualAttendanceResponse> pendingRequests = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_review_requests);

        Logger.i("ReviewRequestsActivity", "Activity created");

        // Get session ID from intent
        sessionId = getIntent().getLongExtra(EXTRA_SESSION_ID, -1L);
        if (sessionId <= 0) {
            Logger.e("ReviewRequestsActivity", "No valid session ID provided");
            ToastUtils.showError(this, "No session data available");
            finish();
            return;
        }

        preferencesManager = PreferencesManager.getInstance(this);
        apiService = ApiClient.getInstance(this).getApiService();

        initializeViews();
        setupToolbar();
        setupAdapter();
        setupClickListeners();
        loadPendingRequests();
    }

    private void initializeViews() {
        textPendingCount = findViewById(R.id.text_pending_count);
        recyclerRequests = findViewById(R.id.recycler_requests);
        btnApproveAllSafe = findViewById(R.id.btn_approve_all_safe);
        btnRejectDuplicates = findViewById(R.id.btn_reject_duplicates);
        btnCancel = findViewById(R.id.btn_cancel);
        btnContinueExport = findViewById(R.id.btn_continue_export);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        toolbar.setNavigationOnClickListener(v -> onCancelClicked());
    }

    private void setupAdapter() {
        requestAdapter = new ManualRequestAdapter(new ManualRequestAdapter.OnRequestActionListener() {
            @Override
            public void onApprove(ManualAttendanceResponse request) {
                approveRequest(request);
            }

            @Override
            public void onReject(ManualAttendanceResponse request) {
                rejectRequest(request);
            }
        });

        recyclerRequests.setLayoutManager(new LinearLayoutManager(this));
        recyclerRequests.setAdapter(requestAdapter);
    }

    private void setupClickListeners() {
        btnApproveAllSafe.setOnClickListener(v -> approveAllSafe());
        btnRejectDuplicates.setOnClickListener(v -> rejectAllDuplicates());
        btnCancel.setOnClickListener(v -> onCancelClicked());
        btnContinueExport.setOnClickListener(v -> onContinueExportClicked());
    }

    private void loadPendingRequests() {
        Logger.api("GET", "api/v1/attendance/manual/pending-requests", "Session ID: " + sessionId);

        apiService.getPendingManualRequests(sessionId).enqueue(new Callback<List<ManualAttendanceResponse>>() {
            @Override
            public void onResponse(Call<List<ManualAttendanceResponse>> call,
                                   Response<List<ManualAttendanceResponse>> response) {
                if (isFinishing() || isDestroyed()) return;

                if (response.isSuccessful() && response.body() != null) {
                    pendingRequests = response.body();
                    Logger.apiResponse("GET", "api/v1/attendance/manual/pending-requests",
                            response.code(), "Found " + pendingRequests.size() + " pending requests");
                    updateUI();
                } else {
                    Logger.apiError("GET", "api/v1/attendance/manual/pending-requests",
                            response.code(), "Failed to load pending requests");
                    ToastUtils.showError(ReviewRequestsActivity.this, "Failed to load pending requests");
                }
            }

            @Override
            public void onFailure(Call<List<ManualAttendanceResponse>> call, Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                Logger.e("ReviewRequestsActivity", "Failed to load pending requests", t);
                ToastUtils.showError(ReviewRequestsActivity.this, getString(R.string.error_load_failed));
            }
        });
    }

    private void updateUI() {
        requestAdapter.updateRequests(pendingRequests);
        textPendingCount.setText(pendingRequests.size() + " pending requests");

        // Update button states
        boolean hasRequests = !pendingRequests.isEmpty();
        btnApproveAllSafe.setEnabled(hasRequests);
        btnRejectDuplicates.setEnabled(hasRequests);
    }

    private void approveRequest(ManualAttendanceResponse request) {
        if (request.getAttendanceId() == null || request.getAttendanceId() <= 0) {
            Logger.e("ReviewRequestsActivity", "Cannot approve - missing attendance ID");
            ToastUtils.showError(this, "Cannot approve request: Missing attendance ID");
            return;
        }

        Logger.userAction("Approve Request", "Approving manual request for student: " + request.getStudentUsername());
        Logger.api("POST", "api/v1/attendance/" + request.getAttendanceId() + "/approve",
                "Attendance ID: " + request.getAttendanceId());

        apiService.approveManualRequest(request.getAttendanceId(), preferencesManager.getUserName())
                .enqueue(new Callback<ManualAttendanceResponse>() {
                    @Override
                    public void onResponse(Call<ManualAttendanceResponse> call,
                                           Response<ManualAttendanceResponse> response) {
                        if (isFinishing() || isDestroyed()) return;

                        if (response.isSuccessful()) {
                            Logger.apiResponse("POST", "api/v1/attendance/" + request.getAttendanceId() + "/approve",
                                    response.code(), "Request approved successfully");
                            Toast.makeText(ReviewRequestsActivity.this, "Request approved", Toast.LENGTH_SHORT).show();
                            refreshPendingRequests();
                        } else {
                            Logger.apiError("POST", "api/v1/attendance/" + request.getAttendanceId() + "/approve",
                                    response.code(), "Failed to approve request");
                            Toast.makeText(ReviewRequestsActivity.this, "Failed to approve request", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ManualAttendanceResponse> call, Throwable t) {
                        if (isFinishing() || isDestroyed()) return;
                        Logger.e("ReviewRequestsActivity", "Failed to approve request", t);
                        ToastUtils.showError(ReviewRequestsActivity.this, getString(R.string.error_operation_failed));
                    }
                });
    }

    private void rejectRequest(ManualAttendanceResponse request) {
        if (request.getAttendanceId() == null || request.getAttendanceId() <= 0) {
            Logger.e("ReviewRequestsActivity", "Cannot reject - missing attendance ID");
            ToastUtils.showError(this, "Cannot reject request: Missing attendance ID");
            return;
        }

        Logger.userAction("Reject Request", "Rejecting manual request for student: " + request.getStudentUsername());
        Logger.api("POST", "api/v1/attendance/" + request.getAttendanceId() + "/reject",
                "Attendance ID: " + request.getAttendanceId());

        apiService.rejectManualRequest(request.getAttendanceId(), preferencesManager.getUserName())
                .enqueue(new Callback<ManualAttendanceResponse>() {
                    @Override
                    public void onResponse(Call<ManualAttendanceResponse> call,
                                           Response<ManualAttendanceResponse> response) {
                        if (isFinishing() || isDestroyed()) return;

                        if (response.isSuccessful()) {
                            Logger.apiResponse("POST", "api/v1/attendance/" + request.getAttendanceId() + "/reject",
                                    response.code(), "Request rejected successfully");
                            Toast.makeText(ReviewRequestsActivity.this, "Request rejected", Toast.LENGTH_SHORT).show();
                            refreshPendingRequests();
                        } else {
                            Logger.apiError("POST", "api/v1/attendance/" + request.getAttendanceId() + "/reject",
                                    response.code(), "Failed to reject request");
                            Toast.makeText(ReviewRequestsActivity.this, "Failed to reject request", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ManualAttendanceResponse> call, Throwable t) {
                        if (isFinishing() || isDestroyed()) return;
                        Logger.e("ReviewRequestsActivity", "Failed to reject request", t);
                        ToastUtils.showError(ReviewRequestsActivity.this, getString(R.string.error_operation_failed));
                    }
                });
    }

    private void refreshPendingRequests() {
        apiService.getPendingManualRequests(sessionId).enqueue(new Callback<List<ManualAttendanceResponse>>() {
            @Override
            public void onResponse(Call<List<ManualAttendanceResponse>> call,
                                   Response<List<ManualAttendanceResponse>> response) {
                if (isFinishing() || isDestroyed()) return;

                if (response.isSuccessful() && response.body() != null) {
                    pendingRequests = response.body();
                    Logger.i("ReviewRequestsActivity", "Refreshed: " + pendingRequests.size() + " pending requests");
                    updateUI();
                }
            }

            @Override
            public void onFailure(Call<List<ManualAttendanceResponse>> call, Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                Logger.e("ReviewRequestsActivity", "Failed to refresh pending requests", t);
            }
        });
    }

    private void approveAllSafe() {
        // TODO: Implement bulk approve logic based on auto_flags
        Toast.makeText(this, "Approve All Safe - Not implemented yet", Toast.LENGTH_SHORT).show();
    }

    private void rejectAllDuplicates() {
        // TODO: Implement bulk reject duplicates logic
        Toast.makeText(this, "Reject All Duplicates - Not implemented yet", Toast.LENGTH_SHORT).show();
    }

    private void onCancelClicked() {
        Logger.userAction("Cancel Review", "User cancelled review requests");
        setResult(RESULT_CANCELLED);
        finish();
    }

    private void onContinueExportClicked() {
        Logger.userAction("Continue Export", "User clicked continue to export with " + pendingRequests.size() + " pending requests");
        setResult(RESULT_CONTINUE_EXPORT);
        finish();
    }

    @Override
    public void onBackPressed() {
        onCancelClicked();
    }
}
