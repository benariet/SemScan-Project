package org.example.semscan.service;

import android.content.Context;
import org.example.semscan.data.api.ApiClient;
import org.example.semscan.data.api.ApiService;
import org.example.semscan.utils.PreferencesManager;
import org.example.semscan.data.model.ManualAttendanceRequest;
import org.example.semscan.data.model.ManualAttendanceResponse;

import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ManualAttendanceService {
    
    private ApiService apiService;
    private Context context;
    private PreferencesManager preferencesManager;
    
    public ManualAttendanceService(Context context) {
        this.context = context;
        this.preferencesManager = PreferencesManager.getInstance(context);
        ApiClient apiClient = ApiClient.getInstance(context);
        apiService = apiClient.getApiService();
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
        ApiService.CreateManualRequestRequest apiRequest = new ApiService.CreateManualRequestRequest(
            request.getSessionId(),
            request.getStudentUsername(),
            request.getReason(),
            request.getDeviceId()
        );
        // API key no longer required - removed authentication
        
        Call<ManualAttendanceResponse> call = apiService.createManualRequest(apiRequest);
        call.enqueue(new Callback<ManualAttendanceResponse>() {
            @Override
            public void onResponse(Call<ManualAttendanceResponse> call, Response<ManualAttendanceResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    org.example.semscan.utils.Logger.apiError("POST", "api/v1/attendance/manual", response.code(), "Failed to create manual request");
                    callback.onError(context.getString(org.example.semscan.R.string.error_operation_failed));
                }
            }
            
            @Override
            public void onFailure(Call<ManualAttendanceResponse> call, Throwable t) {
                org.example.semscan.utils.Logger.e(org.example.semscan.utils.Logger.TAG_API, "Manual request creation failed", t);
                String errorMessage = context.getString(org.example.semscan.R.string.error_network_connection);
                if (t instanceof java.net.SocketTimeoutException || t instanceof java.net.ConnectException) {
                    errorMessage = context.getString(org.example.semscan.R.string.error_network_timeout);
                } else if (t instanceof java.net.UnknownHostException) {
                    errorMessage = context.getString(org.example.semscan.R.string.error_server_unavailable);
                }
                callback.onError(errorMessage);
            }
        });
    }
    
    // Get pending requests
    public void getPendingRequests(Long sessionId, PendingRequestsCallback callback) {
        // API key no longer required - removed authentication
        
        Call<List<ManualAttendanceResponse>> call = apiService.getPendingManualRequests(sessionId);
        call.enqueue(new Callback<List<ManualAttendanceResponse>>() {
            @Override
            public void onResponse(Call<List<ManualAttendanceResponse>> call, Response<List<ManualAttendanceResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    org.example.semscan.utils.Logger.apiError("GET", "api/v1/attendance/pending-requests", response.code(), "Failed to get pending requests");
                    callback.onError(context.getString(org.example.semscan.R.string.error_load_failed));
                }
            }
            
            @Override
            public void onFailure(Call<List<ManualAttendanceResponse>> call, Throwable t) {
                org.example.semscan.utils.Logger.e(org.example.semscan.utils.Logger.TAG_API, "Get pending requests failed", t);
                String errorMessage = context.getString(org.example.semscan.R.string.error_load_failed);
                if (t instanceof java.net.SocketTimeoutException || t instanceof java.net.ConnectException) {
                    errorMessage = context.getString(org.example.semscan.R.string.error_network_timeout);
                } else if (t instanceof java.net.UnknownHostException) {
                    errorMessage = context.getString(org.example.semscan.R.string.error_server_unavailable);
                }
                callback.onError(errorMessage);
            }
        });
    }
    
    // Approve request
    public void approveRequest(Long attendanceId, ManualAttendanceCallback callback) {
        // API key no longer required - removed authentication
        String presenterUsername = preferencesManager.getUserName();
        if (presenterUsername == null || presenterUsername.trim().isEmpty()) {
            callback.onError("Missing presenter username. Please log in again.");
            return;
        }
        presenterUsername = presenterUsername.trim().toLowerCase(Locale.US);

        Call<ManualAttendanceResponse> call = apiService.approveManualRequest(attendanceId, presenterUsername);
        call.enqueue(new Callback<ManualAttendanceResponse>() {
            @Override
            public void onResponse(Call<ManualAttendanceResponse> call, Response<ManualAttendanceResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    org.example.semscan.utils.Logger.apiError("POST", "api/v1/attendance/approve", response.code(), "Failed to approve request");
                    callback.onError(context.getString(org.example.semscan.R.string.error_operation_failed));
                }
            }
            
            @Override
            public void onFailure(Call<ManualAttendanceResponse> call, Throwable t) {
                org.example.semscan.utils.Logger.e(org.example.semscan.utils.Logger.TAG_API, "Approve request failed", t);
                String errorMessage = context.getString(org.example.semscan.R.string.error_network_connection);
                if (t instanceof java.net.SocketTimeoutException || t instanceof java.net.ConnectException) {
                    errorMessage = context.getString(org.example.semscan.R.string.error_network_timeout);
                } else if (t instanceof java.net.UnknownHostException) {
                    errorMessage = context.getString(org.example.semscan.R.string.error_server_unavailable);
                }
                callback.onError(errorMessage);
            }
        });
    }
    
    // Reject request
    public void rejectRequest(Long attendanceId, ManualAttendanceCallback callback) {
        // API key no longer required - removed authentication
        String presenterUsername = preferencesManager.getUserName();
        if (presenterUsername == null || presenterUsername.trim().isEmpty()) {
            callback.onError("Missing presenter username. Please log in again.");
            return;
        }
        presenterUsername = presenterUsername.trim().toLowerCase(Locale.US);

        Call<ManualAttendanceResponse> call = apiService.rejectManualRequest(attendanceId, presenterUsername);
        call.enqueue(new Callback<ManualAttendanceResponse>() {
            @Override
            public void onResponse(Call<ManualAttendanceResponse> call, Response<ManualAttendanceResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    org.example.semscan.utils.Logger.apiError("POST", "api/v1/attendance/reject", response.code(), "Failed to reject request");
                    callback.onError(context.getString(org.example.semscan.R.string.error_operation_failed));
                }
            }
            
            @Override
            public void onFailure(Call<ManualAttendanceResponse> call, Throwable t) {
                org.example.semscan.utils.Logger.e(org.example.semscan.utils.Logger.TAG_API, "Reject request failed", t);
                String errorMessage = context.getString(org.example.semscan.R.string.error_network_connection);
                if (t instanceof java.net.SocketTimeoutException || t instanceof java.net.ConnectException) {
                    errorMessage = context.getString(org.example.semscan.R.string.error_network_timeout);
                } else if (t instanceof java.net.UnknownHostException) {
                    errorMessage = context.getString(org.example.semscan.R.string.error_server_unavailable);
                }
                callback.onError(errorMessage);
            }
        });
    }
}
