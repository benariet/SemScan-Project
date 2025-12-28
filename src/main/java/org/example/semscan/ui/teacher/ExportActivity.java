package org.example.semscan.ui.teacher;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.core.content.FileProvider;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import org.example.semscan.R;
import org.example.semscan.constants.ApiConstants;
import org.example.semscan.data.api.ApiClient;
import org.example.semscan.data.api.ApiService;
import org.example.semscan.data.model.ManualAttendanceResponse;
import org.example.semscan.data.model.Session;
import org.example.semscan.utils.ConfigManager;
import org.example.semscan.utils.Logger;
import org.example.semscan.utils.PreferencesManager;
import org.example.semscan.utils.ToastUtils;

import com.google.gson.JsonParser;

import java.util.List;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ExportActivity extends AppCompatActivity {
    
    private Button btnExport;
    private TextView textSessionId;
    
    private PreferencesManager preferencesManager;
    private ApiService apiService;
    
    private Long currentSessionId;
    private ManualRequestAdapter requestAdapter;
    
    // Session details for filename generation
    private String sessionDate;
    private String sessionTimeSlot;
    private String sessionPresenter;
    private String sessionTopic;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_export);

        Logger.i(Logger.TAG_UI, "ExportActivity created");

        preferencesManager = PreferencesManager.getInstance(this);
        apiService = ApiClient.getInstance(this).getApiService();

        initializeViews();
        setupToolbar();
        setupClickListeners();
        setupRequestAdapter();

        // Auto-check for pending requests when page opens
        checkPendingRequestsOnOpen();
    }

    /**
     * Check for pending requests when the Export page opens.
     * If there are pending requests, show the review modal immediately.
     */
    private void checkPendingRequestsOnOpen() {
        if (currentSessionId == null || currentSessionId <= 0) {
            return;
        }

        Logger.i(Logger.TAG_UI, "Auto-checking for pending requests on Export page open");

        apiService.getPendingManualRequests(currentSessionId).enqueue(new Callback<List<ManualAttendanceResponse>>() {
            @Override
            public void onResponse(Call<List<ManualAttendanceResponse>> call,
                                   Response<List<ManualAttendanceResponse>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    List<ManualAttendanceResponse> pendingRequests = response.body();
                    Logger.i(Logger.TAG_UI, "Found " + pendingRequests.size() + " pending requests - showing review modal");
                    showReviewModal(pendingRequests);
                } else {
                    Logger.i(Logger.TAG_UI, "No pending requests found on page open");
                }
            }

            @Override
            public void onFailure(Call<List<ManualAttendanceResponse>> call, Throwable t) {
                Logger.w(Logger.TAG_UI, "Failed to check pending requests on open: " + t.getMessage());
            }
        });
    }
    
    private void initializeViews() {
        // RadioGroup removed - only Excel export is available
        btnExport = findViewById(R.id.btn_export);
        textSessionId = findViewById(R.id.text_session_id);
        
        // Get current session ID from intent (passed from QR display)
        currentSessionId = getIntent().getLongExtra("sessionId", -1L);
        
        // Get session details for filename generation
        sessionDate = getIntent().getStringExtra("sessionDate");
        sessionTimeSlot = getIntent().getStringExtra("sessionTimeSlot");
        sessionPresenter = getIntent().getStringExtra("sessionPresenter");
        sessionTopic = getIntent().getStringExtra("sessionTopic");
        
        // Display session details (without Session ID)
        StringBuilder sessionDetails = new StringBuilder();
        if (sessionDate != null && !sessionDate.isEmpty()) {
            sessionDetails.append("Date: ").append(sessionDate);
        }
        if (sessionTimeSlot != null && !sessionTimeSlot.isEmpty()) {
            if (sessionDetails.length() > 0) {
                sessionDetails.append("\n");
            }
            // Extract just the time part if it contains date
            String timePart = sessionTimeSlot;
            if (sessionTimeSlot.contains(" ")) {
                timePart = sessionTimeSlot.split(" ")[1]; // Get time part after space
            }
            sessionDetails.append("Time: ").append(timePart);
        }
        if (sessionPresenter != null && !sessionPresenter.isEmpty()) {
            if (sessionDetails.length() > 0) {
                sessionDetails.append("\n");
            }
            sessionDetails.append("Presenter: ").append(sessionPresenter);
        }
        if (sessionTopic != null && !sessionTopic.isEmpty()) {
            if (sessionDetails.length() > 0) {
                sessionDetails.append("\n");
            }
            sessionDetails.append("Topic: ").append(sessionTopic);
        }
        
        if (sessionDetails.length() > 0) {
            textSessionId.setText(sessionDetails.toString());
            Logger.i(Logger.TAG_UI, "Export activity initialized with session details");
        } else {
            textSessionId.setText("No session data available");
            Logger.e(Logger.TAG_UI, "No session details provided in intent");
        }
    }
    
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }
    
    private void setupClickListeners() {
        btnExport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkPendingRequests();
            }
        });
    }
    
    private void setupRequestAdapter() {
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
    }
    
    private void checkPendingRequests() {
        Logger.userAction("Check Pending Requests", "Checking for pending manual requests before export");
        
        // API key no longer required - removed authentication
        
        if (currentSessionId == null || currentSessionId <= 0) {
            Logger.e(Logger.TAG_UI, "Export failed - no session ID available");
            ToastUtils.showError(this, "No session data available");
            return;
        }
        
        Logger.api("GET", "api/v1/attendance/manual/pending-requests", "Session ID: " + currentSessionId);
        
        Call<List<ManualAttendanceResponse>> call = apiService.getPendingManualRequests(currentSessionId);
        call.enqueue(new Callback<List<ManualAttendanceResponse>>() {
            @Override
            public void onResponse(Call<List<ManualAttendanceResponse>> call, Response<List<ManualAttendanceResponse>> response) {
                Logger.i("ExportActivity", "=== API RESPONSE DEBUG ===");
                Logger.i("ExportActivity", "Response successful: " + response.isSuccessful());
                Logger.i("ExportActivity", "Response code: " + response.code());
                Logger.i("ExportActivity", "Response body null: " + (response.body() == null));
                
                if (response.isSuccessful() && response.body() != null) {
                    List<ManualAttendanceResponse> pendingRequests = response.body();
                    Logger.apiResponse("GET", "api/v1/attendance/manual/pending-requests", 
                        response.code(), "Found " + pendingRequests.size() + " pending requests");
                    
                    // Debug logging for pending requests
                    Logger.i("ExportActivity", "=== PENDING REQUESTS DEBUG ===");
                    Logger.i("ExportActivity", "Total pending requests: " + pendingRequests.size());
                    for (int i = 0; i < pendingRequests.size(); i++) {
                        ManualAttendanceResponse req = pendingRequests.get(i);
                        Logger.i("ExportActivity", "Request " + i + ":");
                        Logger.i("ExportActivity", "  - Attendance ID: '" + req.getAttendanceId() + "'");
                        Logger.i("ExportActivity", "  - Session ID: '" + req.getSessionId() + "'");
                        Logger.i("ExportActivity", "  - Student Username: '" + req.getStudentUsername() + "'");
                        Logger.i("ExportActivity", "  - Request Status: '" + req.getRequestStatus() + "'");
                        Logger.i("ExportActivity", "  - Manual Reason: '" + req.getReason() + "'");
                        Logger.i("ExportActivity", "  - Full object: " + req.toString());
                    }
                    
                    if (pendingRequests.isEmpty()) {
                        // No pending requests, proceed with export
                        Logger.i("ExportActivity", "No pending requests found, proceeding with export");
                        exportData();
                    } else {
                        // Show review modal
                        Logger.i("ExportActivity", "Found " + pendingRequests.size() + " pending requests, showing review modal");
                        showReviewModal(pendingRequests);
                    }
                } else {
                    // Log detailed error information
                    String errorBody = null;
                    if (response.errorBody() != null) {
                        try {
                            errorBody = response.errorBody().string();
                        } catch (Exception e) {
                            Logger.e(Logger.TAG_UI, "Error reading pending requests response body", e);
                        }
                    }
                    
                    Logger.apiError("GET", "api/v1/attendance/manual/pending-requests", 
                        response.code(), errorBody != null ? errorBody : "Failed to get pending requests");
                    Logger.i("ExportActivity", "API Error - Code: " + response.code() + ", Body: " + errorBody);
                    ToastUtils.showError(ExportActivity.this, "Failed to check pending requests");
                }
            }
            
            @Override
            public void onFailure(Call<List<ManualAttendanceResponse>> call, Throwable t) {
                Logger.e(Logger.TAG_UI, "Failed to check pending requests", t);
                String errorMessage = getString(R.string.error_load_failed);
                if (t instanceof java.net.SocketTimeoutException || t instanceof java.net.ConnectException) {
                    errorMessage = getString(R.string.error_network_timeout);
                } else if (t instanceof java.net.UnknownHostException) {
                    errorMessage = getString(R.string.error_server_unavailable);
                }
                ToastUtils.showError(ExportActivity.this, errorMessage);
            }
        });
    }
    
    /**
     * Refresh pending requests list without triggering export
     * This is used when approving/rejecting requests - we just want to update the UI,
     * not automatically proceed with export
     */
    private void refreshPendingRequestsOnly() {
        if (currentSessionId == null || currentSessionId <= 0) {
            return;
        }
        
        Logger.api("GET", "api/v1/attendance/manual/pending-requests", "Session ID: " + currentSessionId + " (refresh only)");
        
        Call<List<ManualAttendanceResponse>> call = apiService.getPendingManualRequests(currentSessionId);
        call.enqueue(new Callback<List<ManualAttendanceResponse>>() {
            @Override
            public void onResponse(Call<List<ManualAttendanceResponse>> call, Response<List<ManualAttendanceResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<ManualAttendanceResponse> pendingRequests = response.body();
                    Logger.i("ExportActivity", "Refreshed pending requests: " + pendingRequests.size() + " remaining");
                    
                    // Update the adapter with the new list
                    requestAdapter.updateRequests(pendingRequests);
                    
                    // If there are no more pending requests, we could optionally show a message
                    // but we should NOT automatically trigger export
                    if (pendingRequests.isEmpty()) {
                        Logger.i("ExportActivity", "All pending requests have been resolved");
                        // Don't auto-export - user must click Export button explicitly
                    }
                }
            }
            
            @Override
            public void onFailure(Call<List<ManualAttendanceResponse>> call, Throwable t) {
                Logger.e(Logger.TAG_UI, "Failed to refresh pending requests", t);
                // Don't show error toast for refresh - it's just a background update
            }
        });
    }
    
    private void showReviewModal(List<ManualAttendanceResponse> pendingRequests) {
        Logger.i("ExportActivity", "=== SHOW REVIEW MODAL DEBUG ===");
        Logger.i("ExportActivity", "Creating review modal for " + pendingRequests.size() + " requests");
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_review_requests, null);
        
        TextView textPendingCount = dialogView.findViewById(R.id.text_pending_count);
        RecyclerView recyclerRequests = dialogView.findViewById(R.id.recycler_requests);
        Button btnApproveAllSafe = dialogView.findViewById(R.id.btn_approve_all_safe);
        Button btnRejectAllDuplicates = dialogView.findViewById(R.id.btn_reject_all_duplicates);
        Button btnCancelReview = dialogView.findViewById(R.id.btn_cancel_review);
        Button btnContinueExport = dialogView.findViewById(R.id.btn_continue_export);
        
        Logger.i("ExportActivity", "Dialog view components found:");
        Logger.i("ExportActivity", "  - textPendingCount: " + (textPendingCount != null));
        Logger.i("ExportActivity", "  - recyclerRequests: " + (recyclerRequests != null));
        Logger.i("ExportActivity", "  - btnApproveAllSafe: " + (btnApproveAllSafe != null));
        Logger.i("ExportActivity", "  - btnRejectAllDuplicates: " + (btnRejectAllDuplicates != null));
        Logger.i("ExportActivity", "  - btnCancelReview: " + (btnCancelReview != null));
        Logger.i("ExportActivity", "  - btnContinueExport: " + (btnContinueExport != null));
        
        // Set up recycler view
        recyclerRequests.setLayoutManager(new LinearLayoutManager(this));
        recyclerRequests.setAdapter(requestAdapter);
        requestAdapter.updateRequests(pendingRequests);
        
        // Update pending count
        textPendingCount.setText(pendingRequests.size() + " pending requests");
        
        AlertDialog dialog = builder.setView(dialogView).create();
        Logger.i("ExportActivity", "Dialog created successfully");
        
        // Set up button listeners
        btnApproveAllSafe.setOnClickListener(v -> {
            approveAllSafe(pendingRequests);
            dialog.dismiss();
        });
        
        btnRejectAllDuplicates.setOnClickListener(v -> {
            rejectAllDuplicates(pendingRequests);
            dialog.dismiss();
        });
        
        btnCancelReview.setOnClickListener(v -> dialog.dismiss());
        
        btnContinueExport.setOnClickListener(v -> {
            dialog.dismiss();
            exportData();
        });
        
        Logger.i("ExportActivity", "About to show dialog");
        try {
            dialog.show();
            Logger.i("ExportActivity", "Dialog show() called successfully");
        } catch (Exception e) {
            Logger.e("ExportActivity", "Failed to show dialog", e);
            // Fallback: handle pending requests directly
            handlePendingRequestsDirectly(pendingRequests);
        }
    }
    
    private void approveRequest(ManualAttendanceResponse request) {
        // API key no longer required - removed authentication
        
        // Debug logging to see what's in the request object
        Logger.i("ExportActivity", "=== ATTENDANCE REQUEST DEBUG ===");
        Logger.i("ExportActivity", "Attendance ID: '" + request.getAttendanceId() + "'");
        Logger.i("ExportActivity", "Session ID: '" + request.getSessionId() + "'");
        Logger.i("ExportActivity", "Student Username: '" + request.getStudentUsername() + "'");
        Logger.i("ExportActivity", "Request Status: '" + request.getRequestStatus() + "'");
        Logger.i("ExportActivity", "Manual Reason: '" + request.getReason() + "'");
        Logger.i("ExportActivity", "Attendance object: " + request.toString());
        
        // Check if attendanceId is null
        if (request.getAttendanceId() == null || request.getAttendanceId() <= 0) {
            Logger.e("ExportActivity", "Attendance ID is null or empty - cannot approve request");
            ToastUtils.showError(this, "Cannot approve request: Missing attendance ID");
            return;
        }
        
        Logger.userAction("Approve Request", "Approving manual request for student: " + request.getStudentUsername());
        Logger.api("POST", "api/v1/attendance/" + request.getAttendanceId() + "/approve",
                "Attendance ID: " + request.getAttendanceId());

        Call<ManualAttendanceResponse> call = apiService.approveManualRequest(
                request.getAttendanceId(), preferencesManager.getUserName());
        call.enqueue(new Callback<ManualAttendanceResponse>() {
            @Override
            public void onResponse(Call<ManualAttendanceResponse> call, Response<ManualAttendanceResponse> response) {
                if (response.isSuccessful()) {
                    Logger.apiResponse("POST", "api/v1/attendance/" + request.getAttendanceId() + "/approve", 
                        response.code(), "Request approved successfully");
                    Toast.makeText(ExportActivity.this, "Request approved", Toast.LENGTH_SHORT).show();
                    // Refresh the list WITHOUT triggering export
                    // Export should only happen when user clicks the Export button, not when approving requests
                    refreshPendingRequestsOnly();
                } else {
                    Logger.apiError("POST", "api/v1/attendance/" + request.getAttendanceId() + "/approve", 
                        response.code(), "Failed to approve request");
                    Toast.makeText(ExportActivity.this, "Failed to approve request", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<ManualAttendanceResponse> call, Throwable t) {
                Logger.e(Logger.TAG_UI, "Failed to approve request", t);
                String errorMessage = getString(R.string.error_operation_failed);
                if (t instanceof java.net.SocketTimeoutException || t instanceof java.net.ConnectException) {
                    errorMessage = getString(R.string.error_network_timeout);
                } else if (t instanceof java.net.UnknownHostException) {
                    errorMessage = getString(R.string.error_server_unavailable);
                }
                ToastUtils.showError(ExportActivity.this, errorMessage);
            }
        });
    }
    
    private void rejectRequest(ManualAttendanceResponse request) {
        // API key no longer required - removed authentication
        
        Logger.userAction("Reject Request", "Rejecting manual request for student: " + request.getStudentUsername());
        Logger.api("POST", "api/v1/attendance/" + request.getAttendanceId() + "/reject",
                "Attendance ID: " + request.getAttendanceId());

        Call<ManualAttendanceResponse> call = apiService.rejectManualRequest(
                request.getAttendanceId(), preferencesManager.getUserName());
        call.enqueue(new Callback<ManualAttendanceResponse>() {
            @Override
            public void onResponse(Call<ManualAttendanceResponse> call, Response<ManualAttendanceResponse> response) {
                if (response.isSuccessful()) {
                    Logger.apiResponse("POST", "api/v1/attendance/" + request.getAttendanceId() + "/reject", 
                        response.code(), "Request rejected successfully");
                    Toast.makeText(ExportActivity.this, "Request rejected", Toast.LENGTH_SHORT).show();
                    // Refresh the list WITHOUT triggering export
                    // Export should only happen when user clicks the Export button, not when rejecting requests
                    refreshPendingRequestsOnly();
                } else {
                    Logger.apiError("POST", "api/v1/attendance/" + request.getAttendanceId() + "/reject", 
                        response.code(), "Failed to reject request");
                    Toast.makeText(ExportActivity.this, "Failed to reject request", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<ManualAttendanceResponse> call, Throwable t) {
                Logger.e(Logger.TAG_UI, "Failed to reject request", t);
                String errorMessage = getString(R.string.error_operation_failed);
                if (t instanceof java.net.SocketTimeoutException || t instanceof java.net.ConnectException) {
                    errorMessage = getString(R.string.error_network_timeout);
                } else if (t instanceof java.net.UnknownHostException) {
                    errorMessage = getString(R.string.error_server_unavailable);
                }
                ToastUtils.showError(ExportActivity.this, errorMessage);
            }
        });
    }
    
    private void approveAllSafe(List<ManualAttendanceResponse> requests) {
        // TODO: Implement bulk approve logic based on auto_flags
        Toast.makeText(this, "Approve All Safe - Not implemented yet", Toast.LENGTH_SHORT).show();
    }
    
    private void rejectAllDuplicates(List<ManualAttendanceResponse> requests) {
        // TODO: Implement bulk reject duplicates logic
        Toast.makeText(this, "Reject All Duplicates - Not implemented yet", Toast.LENGTH_SHORT).show();
    }
    
    private void exportData() {
        Logger.userAction("Export Data", "User clicked export button");
        
        // API key no longer required - removed authentication
        
        if (currentSessionId == null) {
            Logger.e(Logger.TAG_UI, "Export failed - no session ID available");
            ToastUtils.showError(this, "No session data available");
            return;
        }
        
        // Only Excel export is available (CSV option removed)
        boolean isExcel = true;
        String formatLabel = "Excel (.xlsx)";
        
        Logger.i(Logger.TAG_UI, "Starting export upload - Session ID: " + currentSessionId + ", Format: " + formatLabel);
        uploadSessionData(currentSessionId, isExcel);
    }
    
    /**
     * New flow: trigger server-side export + upload.
     *
     * The backend will generate the export file and upload it to the configured upload server.
     * The mobile app only triggers this process and shows the result; it no longer handles raw file bytes here.
     */
    private void uploadSessionData(Long sessionId, boolean isExcel) {
        String formatParam = isExcel ? "xlsx" : "csv";
        String endpoint = "api/v1/export/upload";
        
        Logger.api("POST", endpoint, "Session ID: " + sessionId + ", format=" + formatParam);
        
        Call<ApiService.UploadResponse> call = apiService.uploadExport(sessionId, formatParam);
        call.enqueue(new Callback<ApiService.UploadResponse>() {
            @Override
            public void onResponse(Call<ApiService.UploadResponse> call, Response<ApiService.UploadResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiService.UploadResponse body = response.body();
                    Logger.apiResponse("POST", endpoint, response.code(),
                            "Upload success=" + body.success + ", message=" + body.message);
                    
                    if (Boolean.TRUE.equals(body.success)) {
                        String msg = body.message;
                        if (msg == null || msg.trim().isEmpty()) {
                            msg = "Export uploaded successfully";
                        }
                        // Include record count if available
                        if (body.records != null && body.records > 0) {
                            msg += " (" + body.records + " records)";
                        }
                        Toast.makeText(ExportActivity.this, msg, Toast.LENGTH_LONG).show();
                    } else {
                        String errorMessage = body.message != null && !body.message.trim().isEmpty()
                                ? body.message
                                : getString(R.string.error_export_failed);
                        ToastUtils.showError(ExportActivity.this, errorMessage);
                    }
                } else {
                    String errorBody = null;
                    if (response.errorBody() != null) {
                        try {
                            errorBody = response.errorBody().string();
                        } catch (Exception e) {
                            Logger.e(Logger.TAG_UI, "Error reading export upload response body", e);
                        }
                    }
                    
                    Logger.apiError("POST", endpoint, response.code(),
                            errorBody != null ? errorBody : "Export upload request failed");
                    
                    String errorMessage;
                    if (response.code() == ApiConstants.HTTP_CONFLICT) {
                        // Should normally be prevented by pending-requests check, but handle defensively
                        errorMessage = "Cannot upload while manual attendance requests are pending. Please review and approve/reject them first.";
                    } else if (response.code() == ApiConstants.HTTP_BAD_REQUEST) {
                        errorMessage = "Export format is invalid. Please try again.";
                    } else if (response.code() >= 500) {
                        errorMessage = "Server error while uploading export. Please try again later.";
                    } else {
                        errorMessage = getString(R.string.error_export_failed);
                    }
                    
                    ToastUtils.showError(ExportActivity.this, errorMessage);
                }
            }
            
            @Override
            public void onFailure(Call<ApiService.UploadResponse> call, Throwable t) {
                Logger.e(Logger.TAG_UI, "Export upload network failure", t);
                String errorMessage = getString(R.string.error_export_failed);
                if (t instanceof java.net.SocketTimeoutException || t instanceof java.net.ConnectException) {
                    errorMessage = getString(R.string.error_network_timeout);
                } else if (t instanceof java.net.UnknownHostException) {
                    errorMessage = getString(R.string.error_server_unavailable);
                }
                ToastUtils.showError(ExportActivity.this, errorMessage);
            }
        });
    }
    
    // Legacy local-file export kept for reference, no longer used by the main flow.
    private void exportSessionData(Long sessionId, boolean isExcel) {
        Call<ResponseBody> call;
        String filename;
        String mimeType;
        String endpoint;
        
        if (isExcel) {
            call = apiService.exportXlsx(sessionId);
            filename = generateExportFilename(sessionId, ".xlsx");
            mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            endpoint = "api/v1/export/xlsx";
        } else {
            call = apiService.exportCsv(sessionId);
            filename = generateExportFilename(sessionId, ".csv");
            mimeType = "text/csv";
            endpoint = "api/v1/export/csv";
        }
        
        Logger.api("GET", endpoint, "Session ID: " + sessionId);
        
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        Logger.apiResponse("GET", endpoint, response.code(), "Export data received successfully");
                        
                        // Save file to external storage
                        File file = new File(getExternalFilesDir(null), filename);
                        FileOutputStream fos = new FileOutputStream(file);
                        fos.write(response.body().bytes());
                        fos.close();
                        
                        Logger.i(Logger.TAG_UI, "Export file saved: " + filename + " (" + file.length() + " bytes)");
                        
                        // Share the file
                        shareFile(file, mimeType);
                        
                        Logger.i(Logger.TAG_UI, "Export completed successfully - Session: " + sessionId);
                        Toast.makeText(ExportActivity.this, "Export successful", Toast.LENGTH_SHORT).show();
                        
                        // Note: Navigation to home happens after email sending completes
                    } catch (IOException e) {
                        Logger.e(Logger.TAG_UI, "Failed to save export file", e);
                        Toast.makeText(ExportActivity.this, R.string.error_file_save_failed, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // Parse error response body for detailed error message
                    String errorBody = null;
                    if (response.errorBody() != null) {
                        try {
                            errorBody = response.errorBody().string();
                        } catch (Exception e) {
                            Logger.e(Logger.TAG_UI, "Error reading export response body", e);
                        }
                    }
                    
                    Logger.apiError("GET", endpoint, response.code(), errorBody != null ? errorBody : "Export request failed");
                    
                    // Show specific error message based on response code
                    String errorMessage = getExportErrorMessage(response.code(), errorBody);
                    ToastUtils.showError(ExportActivity.this, errorMessage);
                }
            }
            
            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Logger.e(Logger.TAG_UI, "Export network failure", t);
                String errorMessage = getString(R.string.error_export_failed);
                if (t instanceof java.net.SocketTimeoutException || t instanceof java.net.ConnectException) {
                    errorMessage = getString(R.string.error_network_timeout);
                } else if (t instanceof java.net.UnknownHostException) {
                    errorMessage = getString(R.string.error_server_unavailable);
                }
                ToastUtils.showError(ExportActivity.this, errorMessage);
            }
        });
    }
    
    private void shareFile(File file, String mimeType) {
        Logger.i(Logger.TAG_UI, "Sending export file via email automatically: " + file.getName() + " (" + mimeType + ")");
        
        try {
            // Read file content using try-with-resources to ensure FileInputStream is always closed
            byte[] fileBytes = new byte[(int) file.length()];
            try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                fis.read(fileBytes);
            }
            
            // Build email content
            String subject = "SemScan Attendance Export - Session " + currentSessionId;
            String htmlContent = buildExportEmailHtml(file, fileBytes, mimeType);
            
            // Parse multiple email recipients (comma-separated) from ConfigManager
            String exportRecipients = ConfigManager.getInstance(this).getExportEmailRecipients();
            String[] recipients = exportRecipients.split(",");
            
            // Send email to each recipient via backend API with file attachment
            sendExportEmailToRecipients(recipients, subject, htmlContent, file, fileBytes, mimeType);
            
        } catch (Exception e) {
            Logger.e(Logger.TAG_UI, "Failed to send file via email", e);
            Toast.makeText(this, R.string.error_email_failed, Toast.LENGTH_SHORT).show();
        }
    }
    
    private void sendExportEmailToRecipients(String[] recipients, String subject, String htmlContent, 
                                            File file, byte[] fileBytes, String mimeType) {
        ApiService apiService = ApiClient.getInstance(this).getApiService();
        
        // Filter out empty recipients
        java.util.List<String> validRecipients = new java.util.ArrayList<>();
        for (String recipient : recipients) {
            String email = recipient.trim();
            if (!email.isEmpty()) {
                validRecipients.add(email);
            }
        }
        
        if (validRecipients.isEmpty()) {
            Toast.makeText(this, "No email recipients configured", Toast.LENGTH_LONG).show();
            Logger.w(Logger.TAG_UI, "No valid email recipients found in config");
            return;
        }
        
        // Show progress
        Toast.makeText(this, "Sending export email...", Toast.LENGTH_SHORT).show();
        
        // Encode file as base64 for attachment (do this once, outside the loop)
        final String base64File = android.util.Base64.encodeToString(fileBytes, android.util.Base64.NO_WRAP);
        final String fileName = file.getName();
        final String contentType = mimeType;
        
        // Track results
        final int[] successCount = {0};
        final int[] failCount = {0};
        final int totalRecipients = validRecipients.size();
        final int[] completedCount = {0};
        
        // Send to each recipient asynchronously
        for (String recipient : validRecipients) {
            final String email = recipient;
            
            ApiService.TestEmailRequest request = new ApiService.TestEmailRequest(
                email,
                subject,
                htmlContent,
                fileName,      // attachmentFileName
                contentType,   // attachmentContentType
                base64File    // attachmentBase64
            );
            
            apiService.sendTestEmail(request).enqueue(new Callback<ApiService.TestEmailResponse>() {
                @Override
                public void onResponse(Call<ApiService.TestEmailResponse> call, Response<ApiService.TestEmailResponse> response) {
                    completedCount[0]++;
                    
                    if (response.isSuccessful() && response.body() != null && response.body().success) {
                        successCount[0]++;
                        Logger.i(Logger.TAG_API, "Export email sent successfully to: " + email);
                    } else {
                        failCount[0]++;
                        String errorMessage = "Email sending failed";
                        
                        // Try to extract error message from response
                        if (response.body() != null && response.body().message != null) {
                            errorMessage = response.body().message;
                        } else if (response.errorBody() != null) {
                            try {
                                String errorBody = response.errorBody().string();
                                if (errorBody != null && !errorBody.trim().isEmpty()) {
                                    // Try to parse JSON error response
                                    try {
                                        JsonParser parser = new JsonParser();
                                        com.google.gson.JsonObject jsonObject = parser.parse(errorBody).getAsJsonObject();
                                        if (jsonObject.has("message") && jsonObject.get("message").isJsonPrimitive()) {
                                            errorMessage = jsonObject.get("message").getAsString();
                                        }
                                    } catch (Exception e) {
                                        // If JSON parsing fails, use raw error body (truncated)
                                        errorMessage = errorBody.length() > 150 ? errorBody.substring(0, 150) + "..." : errorBody;
                                    }
                                }
                            } catch (Exception e) {
                                Logger.e(Logger.TAG_API, "Failed to read error body", e);
                            }
                        }
                        
                        // Check for authentication errors specifically
                        if (errorMessage.toLowerCase().contains("authentication") || 
                            errorMessage.toLowerCase().contains("username and password not accepted") ||
                            errorMessage.toLowerCase().contains("badcredentials")) {
                            errorMessage = "Email server authentication failed. Please check SMTP credentials in backend configuration.";
                        }
                        
                        Logger.e(Logger.TAG_API, "Failed to send export email to: " + email + ", code: " + response.code() + ", error: " + errorMessage);
                        
                        // Store error message for final toast
                        final String finalErrorMessage = errorMessage;
                        if (completedCount[0] >= totalRecipients) {
                            runOnUiThread(() -> {
                                if (failCount[0] == totalRecipients) {
                                    // All failed - show specific error
                                    Toast.makeText(ExportActivity.this, 
                                        "Email sending failed: " + finalErrorMessage, 
                                        Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }
                    
                    // Show result when all emails are sent
                    if (completedCount[0] >= totalRecipients) {
                        runOnUiThread(() -> {
                            if (successCount[0] > 0 && failCount[0] == 0) {
                                Toast.makeText(ExportActivity.this, 
                                    "Export email sent successfully to " + successCount[0] + " recipient(s)", 
                                    Toast.LENGTH_LONG).show();
                            } else if (successCount[0] > 0) {
                                Toast.makeText(ExportActivity.this, 
                                    "Export email sent to " + successCount[0] + " recipient(s), " + failCount[0] + " failed", 
                                    Toast.LENGTH_LONG).show();
                            }
                            // If all failed, error message was already shown above
                            
                            // Navigate to home after email sending completes (regardless of success/failure)
                            navigateToHome();
                        });
                    }
                }
                
                @Override
                public void onFailure(Call<ApiService.TestEmailResponse> call, Throwable t) {
                    completedCount[0]++;
                    failCount[0]++;
                    Logger.e(Logger.TAG_API, "Error sending export email to: " + email, t);
                    
                    // Show result when all emails are sent
                    if (completedCount[0] >= totalRecipients) {
                        runOnUiThread(() -> {
                            if (successCount[0] > 0 && failCount[0] == 0) {
                                Toast.makeText(ExportActivity.this, 
                                    "Export email sent successfully to " + successCount[0] + " recipient(s)", 
                                    Toast.LENGTH_LONG).show();
                            } else if (successCount[0] > 0) {
                                Toast.makeText(ExportActivity.this, 
                                    "Export email sent to " + successCount[0] + " recipient(s), " + failCount[0] + " failed", 
                                    Toast.LENGTH_LONG).show();
                            } else {
                                String errorMsg = getString(R.string.error_email_failed);
                                if (t instanceof java.net.SocketTimeoutException || t instanceof java.net.ConnectException) {
                                    errorMsg = getString(R.string.error_network_timeout);
                                } else if (t instanceof java.net.UnknownHostException) {
                                    errorMsg = getString(R.string.error_server_unavailable);
                                }
                                Toast.makeText(ExportActivity.this, 
                                    "Failed to send export email: " + errorMsg, 
                                    Toast.LENGTH_LONG).show();
                            }
                            
                            // Navigate to home after email sending completes (regardless of success/failure)
                            navigateToHome();
                        });
                    }
                }
            });
        }
    }
    
    private String buildExportEmailHtml(File file, byte[] fileBytes, String mimeType) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>");
        html.append("<html>");
        html.append("<head>");
        html.append("<meta charset=\"UTF-8\">");
        html.append("<style>");
        html.append("body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }");
        html.append(".container { max-width: 800px; margin: 0 auto; padding: 20px; }");
        html.append(".header { background-color: #2196F3; color: white; padding: 20px; text-align: center; }");
        html.append(".content { padding: 20px; background-color: #f9f9f9; }");
        html.append(".details { background-color: white; padding: 15px; margin: 15px 0; border-left: 4px solid #2196F3; }");
        html.append(".data-table { width: 100%; border-collapse: collapse; margin: 20px 0; background-color: white; }");
        html.append(".data-table th, .data-table td { border: 1px solid #ddd; padding: 8px; text-align: left; }");
        html.append(".data-table th { background-color: #2196F3; color: white; }");
        html.append(".data-table tr:nth-child(even) { background-color: #f2f2f2; }");
        html.append(".footer { padding: 20px; text-align: center; color: #666; font-size: 12px; }");
        html.append("</style>");
        html.append("</head>");
        html.append("<body>");
        html.append("<div class=\"container\">");
        html.append("<div class=\"header\">");
        html.append("<h1>SemScan Attendance Export</h1>");
        html.append("</div>");
        html.append("<div class=\"content\">");
        html.append("<p>Hello,</p>");
        html.append("<p>Please find the attendance data export below.</p>");
        
        // Session details
        html.append("<div class=\"details\">");
        html.append("<h3>Session Details:</h3>");
        html.append("<ul>");
        html.append("<li><strong>Session ID:</strong> ").append(currentSessionId).append("</li>");
        if (sessionDate != null) {
            html.append("<li><strong>Date:</strong> ").append(sessionDate).append("</li>");
        }
        if (sessionTimeSlot != null) {
            html.append("<li><strong>Time Slot:</strong> ").append(sessionTimeSlot).append("</li>");
        }
        if (sessionPresenter != null) {
            html.append("<li><strong>Presenter:</strong> ").append(sessionPresenter).append("</li>");
        }
        html.append("<li><strong>File:</strong> ").append(file.getName()).append("</li>");
        html.append("<li><strong>Format:</strong> ").append(mimeType.contains("csv") ? "CSV" : "Excel (XLSX)").append("</li>");
        html.append("</ul>");
        html.append("</div>");
        
        // File attachment note
        html.append("<div class=\"details\">");
        html.append("<h3>File Attachment:</h3>");
        html.append("<p>The attendance export file is attached to this email.</p>");
        html.append("<p style=\"color: #666; font-size: 12px;\">File: ").append(escapeHtml(file.getName())).append(" (").append(formatFileSize(fileBytes.length)).append(")</p>");
        html.append("</div>");
        
        // File content preview (for CSV only)
        if (mimeType.contains("csv")) {
            // Convert CSV to HTML table for preview
            String csvContent = new String(fileBytes, java.nio.charset.StandardCharsets.UTF_8);
            html.append("<h3>Attendance Data Preview:</h3>");
            html.append("<p style=\"color: #666; font-size: 12px;\">(Full data available in attached file)</p>");
            html.append(convertCsvToHtmlTable(csvContent));
        } else {
            // For XLSX, just show a note
            html.append("<div class=\"details\">");
            html.append("<p><strong>Note:</strong> Excel file (XLSX) export is available. The file contains ").append(formatFileSize(fileBytes.length)).append(" of data.</p>");
            html.append("<p>Please download the attached file to view the complete attendance data.</p>");
            html.append("</div>");
        }
        
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
    
    private String convertCsvToHtmlTable(String csvContent) {
        if (csvContent == null || csvContent.trim().isEmpty()) {
            return "<p>No data available.</p>";
        }
        
        StringBuilder table = new StringBuilder();
        table.append("<table class=\"data-table\">");
        
        String[] lines = csvContent.split("\n");
        boolean isFirstLine = true;
        
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            
            table.append("<tr>");
            
            // Split by comma (handle quoted values)
            String[] cells = parseCsvLine(line);
            
            for (String cell : cells) {
                String tag = isFirstLine ? "th" : "td";
                // Escape HTML and trim
                String cellContent = escapeHtml(cell.trim());
                table.append("<").append(tag).append(">").append(cellContent).append("</").append(tag).append(">");
            }
            
            table.append("</tr>");
            isFirstLine = false;
        }
        
        table.append("</table>");
        return table.toString();
    }
    
    private String[] parseCsvLine(String line) {
        java.util.List<String> cells = new java.util.ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentCell = new StringBuilder();
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                cells.add(currentCell.toString());
                currentCell = new StringBuilder();
            } else {
                currentCell.append(c);
            }
        }
        cells.add(currentCell.toString());
        
        return cells.toArray(new String[0]);
    }
    
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
    
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " bytes";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }
    
    /**
     * Provide a simple way to handle pending requests without dialog
     * This can be called as a fallback if the dialog fails
     */
    private void handlePendingRequestsDirectly(List<ManualAttendanceResponse> pendingRequests) {
        Logger.i("ExportActivity", "Handling pending requests directly - count: " + pendingRequests.size());
        
        // For now, just show a message and allow user to continue
        // In a real implementation, you might want to show a simpler dialog or list
        String message = "Found " + pendingRequests.size() + " pending manual attendance requests. " +
                        "You can either:\n" +
                        "1. Approve/reject them individually, or\n" +
                        "2. Continue with export (requests will remain pending)";
        
        ToastUtils.showError(this, message);
        
        // For debugging, let's also log the details
        for (ManualAttendanceResponse req : pendingRequests) {
            Logger.i("ExportActivity", "Pending request - ID: " + req.getAttendanceId() + 
                      ", Student: " + req.getStudentUsername() + ", Reason: " + req.getReason());
        }
    }
    
    /**
     * Navigate to presenter home page
     */
    private void navigateToHome() {
        Intent intent = new Intent(ExportActivity.this, PresenterHomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    /**
     * Get user-friendly error message for export failures
     */
    private String getExportErrorMessage(int responseCode, String errorBody) {
        switch (responseCode) {
            case 409:
                // Parse the specific error message from the server
                if (errorBody != null && errorBody.contains("manual attendance requests are pending approval")) {
                    return "Cannot export while manual attendance requests are pending approval. Please review and resolve all pending requests before exporting.";
                }
                return "Export conflict: " + (errorBody != null ? errorBody : "Please resolve pending requests");
            case 404:
                return "Session not found or no data available for export";
            case 400:
                return "Invalid export request: " + (errorBody != null ? errorBody : "Please check session data");
            case 500:
                return "Server error during export: " + (errorBody != null ? errorBody : "Please try again later");
            default:
                return "Export failed (Code: " + responseCode + ")" + 
                       (errorBody != null ? " - " + errorBody : "");
        }
    }
    
    /**
     * Generate a descriptive filename for the export file
     * Format: day_month_year_presenter_time.ext
     * Example: 9_11_2025_john_doe_13-15.csv
     *         9_11_2025_13-15.csv (if presenter not available)
     */
    private String generateExportFilename(Long sessionId, String extension) {
        StringBuilder filename = new StringBuilder();
        
        // Parse date if available (format: "2025-11-09" or "09/11/2025")
        String datePart = "";
        if (sessionDate != null && !sessionDate.isEmpty()) {
            try {
                // Try to parse different date formats
                if (sessionDate.contains("-")) {
                    // Format: "2025-11-09" -> "9_11_2025"
                    String[] parts = sessionDate.split("-");
                    if (parts.length == 3) {
                        int day = Integer.parseInt(parts[2]);
                        int month = Integer.parseInt(parts[1]);
                        int year = Integer.parseInt(parts[0]);
                        datePart = String.format("%d_%d_%d", day, month, year);
                    }
                } else if (sessionDate.contains("/")) {
                    // Format: "09/11/2025" -> "9_11_2025"
                    String[] parts = sessionDate.split("/");
                    if (parts.length == 3) {
                        int day = Integer.parseInt(parts[0]);
                        int month = Integer.parseInt(parts[1]);
                        int year = Integer.parseInt(parts[2]);
                        datePart = String.format("%d_%d_%d", day, month, year);
                    }
                }
            } catch (Exception e) {
                Logger.w(Logger.TAG_UI, "Failed to parse session date: " + sessionDate + " - " + e.getMessage());
            }
        }
        
        // If date parsing failed, try to extract from timeSlot
        if (datePart.isEmpty() && sessionTimeSlot != null && sessionTimeSlot.contains(" ")) {
            try {
                String dateStr = sessionTimeSlot.split(" ")[0]; // Get date part
                if (dateStr.contains("-")) {
                    String[] parts = dateStr.split("-");
                    if (parts.length == 3) {
                        int day = Integer.parseInt(parts[2]);
                        int month = Integer.parseInt(parts[1]);
                        int year = Integer.parseInt(parts[0]);
                        datePart = String.format("%d_%d_%d", day, month, year);
                    }
                }
            } catch (Exception e) {
                Logger.w(Logger.TAG_UI, "Failed to extract date from timeSlot: " + sessionTimeSlot + " - " + e.getMessage());
            }
        }
        
        // If still no date, use current date
        if (datePart.isEmpty()) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            int day = cal.get(java.util.Calendar.DAY_OF_MONTH);
            int month = cal.get(java.util.Calendar.MONTH) + 1; // Calendar.MONTH is 0-based
            int year = cal.get(java.util.Calendar.YEAR);
            datePart = String.format("%d_%d_%d", day, month, year);
        }
        
        filename.append(datePart);
        
        // Extract time from timeSlot (format: "2025-11-09 14:00-15:00" -> "13-15" or "14-15")
        String timePart = "";
        if (sessionTimeSlot != null && !sessionTimeSlot.isEmpty()) {
            try {
                // Extract time range (e.g., "14:00-15:00" -> "14-15")
                if (sessionTimeSlot.contains(" ")) {
                    String timeRange = sessionTimeSlot.split(" ")[1]; // Get time part
                    if (timeRange.contains("-")) {
                        String[] times = timeRange.split("-");
                        if (times.length == 2) {
                            String startTime = times[0].trim(); // "14:00"
                            String endTime = times[1].trim();   // "15:00"
                            
                            // Extract hour from "14:00" -> "14"
                            if (startTime.contains(":")) {
                                String startHour = startTime.split(":")[0];
                                String endHour = endTime.contains(":") ? endTime.split(":")[0] : endTime;
                                timePart = startHour + "-" + endHour;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Logger.w(Logger.TAG_UI, "Failed to parse timeSlot: " + sessionTimeSlot + " - " + e.getMessage());
            }
        }
        
        // Add presenter name
        String presenterPart = "";
        
        if (sessionPresenter != null && !sessionPresenter.trim().isEmpty()) {
            // Use presenter name, sanitize for filename
            presenterPart = sanitizeForFilename(sessionPresenter.trim());
        }
        
        // Build filename: date_presenter_time.ext or date_time.ext
        if (!presenterPart.isEmpty()) {
            filename.append("_").append(presenterPart);
        }
        
        if (!timePart.isEmpty()) {
            filename.append("_").append(timePart);
        }
        
        // Fallback to sessionId if we don't have enough info
        if (datePart.isEmpty() && presenterPart.isEmpty() && timePart.isEmpty()) {
            filename = new StringBuilder("attendance_").append(sessionId);
        }
        
        filename.append(extension);
        
        Logger.i(Logger.TAG_UI, "Generated export filename: " + filename.toString());
        return filename.toString();
    }
    
    /**
     * Sanitize a string for use in a filename
     * Removes special characters, replaces spaces with underscores, limits length
     */
    private String sanitizeForFilename(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        
        // Replace spaces and special characters with underscores
        String sanitized = input.replaceAll("[^a-zA-Z0-9_-]", "_")
                                .replaceAll("_+", "_")  // Replace multiple underscores with single
                                .replaceAll("^_|_$", ""); // Remove leading/trailing underscores
        
        // Limit length to 50 characters
        if (sanitized.length() > 50) {
            sanitized = sanitized.substring(0, 50);
        }
        
        return sanitized.toLowerCase();
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        // Navigate to home instead of just finishing
        navigateToHome();
        return true;
    }
    
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // Navigate to home instead of finishing (which would log user out)
        // This ensures user returns to home page when they come back to the app
        navigateToHome();
    }
}


