package org.example.semscan.ui.qr;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;

import org.example.semscan.R;
import org.example.semscan.data.api.ApiClient;
import org.example.semscan.data.api.ApiService;
import org.example.semscan.data.model.Attendance;
import org.example.semscan.data.model.QRPayload;
import org.example.semscan.utils.Logger;
import org.example.semscan.utils.PreferencesManager;
import org.example.semscan.utils.QRUtils;
import org.example.semscan.utils.ServerLogger;
import org.example.semscan.utils.ToastUtils;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class QRScannerActivity extends AppCompatActivity {
    
    private static final int CAMERA_PERMISSION_REQUEST = 1001;
    
    private DecoratedBarcodeView barcodeView;
    private Button btnFlashlight;
    private Button btnCancel;
    private PreferencesManager preferencesManager;
    private ApiService apiService;
    private ServerLogger serverLogger;
    
    private boolean isFlashlightOn = false;
    private Long currentSessionId = null;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_scanner);
        
        initializeViews();
        setupToolbar();
        setupClickListeners();
        
        preferencesManager = PreferencesManager.getInstance(this);
        apiService = ApiClient.getInstance(this).getApiService();
        serverLogger = ServerLogger.getInstance(this);
    }
    
    private void initializeViews() {
        barcodeView = findViewById(R.id.barcode_scanner);
        btnFlashlight = findViewById(R.id.btn_flashlight);
        btnCancel = findViewById(R.id.btn_cancel);
    }
    
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Scan QR Code");
        }
    }
    
    private void setupClickListeners() {
        btnFlashlight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleFlashlight();
            }
        });
        
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        
        // Manual attendance button should not be on QR scanner screen
        // It should only be available from the student dashboard
        
        barcodeView.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                serverLogger.qr("QRScanned", "QR Code: " + result.getText());
                handleQRResult(result.getText());
            }
            
            @Override
            public void possibleResultPoints(java.util.List<com.google.zxing.ResultPoint> resultPoints) {
                // Optional: Handle possible result points
            }
        });
    }
    
    private void startScanning() {
        Logger.qr("Scanner Started", "QR scanner started");
        barcodeView.resume();
    }
    
    private void stopScanning() {
        Logger.qr("Scanner Stopped", "QR scanner stopped");
        barcodeView.pause();
    }
    
    private void toggleFlashlight() {
        if (isFlashlightOn) {
            barcodeView.setTorchOff();
            isFlashlightOn = false;
            Logger.qr("Flashlight", "Flashlight turned off");
        } else {
            barcodeView.setTorchOn();
            isFlashlightOn = true;
            Logger.qr("Flashlight", "Flashlight turned on");
        }
    }
    

    private void handleQRResult(String qrContent) {
        Logger.qr("QR Code Scanned", "Content: " + qrContent);
        Logger.i(Logger.TAG_QR, "=== QR CODE PARSING DEBUG ===");
        Logger.i(Logger.TAG_QR, "Raw QR content: '" + qrContent + "'");
        Logger.i(Logger.TAG_QR, "QR content length: " + qrContent.length());
        Logger.i(Logger.TAG_QR, "QR content trimmed: '" + qrContent.trim() + "'");
        
        stopScanning();
        
        // Parse QR content
        QRPayload payload = QRUtils.parseQRContent(qrContent);
        Logger.i(Logger.TAG_QR, "Parsed payload: " + (payload != null ? payload.toString() : "null"));
        
        if (payload == null || !QRUtils.isValidQRContent(qrContent)) {
            Logger.qr("Invalid QR Code", "Failed to parse QR content: " + qrContent);
            Logger.qr("QR Code Debug", "Expected format: {\"sessionId\":\"session-xxx\"}");
            showError("Invalid QR code format. Expected: {\"sessionId\":\"session-xxx\"}");
            return;
        }
        
        Long sessionId = payload.getSessionId();
        Logger.i(Logger.TAG_QR, "Extracted session ID: '" + sessionId + "'");
        Logger.i(Logger.TAG_QR, "Session ID is null: " + (sessionId == null));
        
        if (sessionId == null || sessionId <= 0) {
            Logger.qr("Invalid QR Code", "Session ID is null or empty");
            showError("QR code missing session ID");
            return;
        }
        
        Logger.qr("QR Code Parsed", "Session ID: " + sessionId);
        
        // Store current session ID (manual request button removed from scanner)
        currentSessionId = sessionId;
        
        // Submit attendance
        submitAttendance(sessionId);
    }
    
    private void submitAttendance(Long sessionId) {
        // Enhanced validation and logging
        Logger.i(Logger.TAG_QR, "=== ATTENDANCE SUBMISSION DEBUG ===");
        Logger.i(Logger.TAG_QR, "Session ID from QR: '" + sessionId + "'");
        
        serverLogger.attendance("AttendanceSubmission", "Session ID: " + sessionId);
        
        // Validate session ID
        if (sessionId == null || sessionId <= 0) {
            Logger.e(Logger.TAG_QR, "Session ID validation failed: null or empty");
            showError("Invalid session ID from QR code");
            return;
        }
        
        // Check user role first
        String userRole = preferencesManager.getUserRole();
        Logger.i(Logger.TAG_QR, "User role: '" + userRole + "'");
        
        if (preferencesManager.isPresenter()) {
            Logger.w(Logger.TAG_QR, "Presenter trying to mark attendance - this should be done by students only");
            showError("Presenters cannot mark attendance. Please use the student app to scan QR codes.");
            return;
        }
        
        String studentUsername = preferencesManager.getUserName();
        Logger.i(Logger.TAG_QR, "Student username from preferences: '" + studentUsername + "'");
        if (TextUtils.isEmpty(studentUsername)) {
            Logger.e(Logger.TAG_QR, "Student username validation failed: null or empty");
            showError("Student username not found. Please log in again.");
            return;
        }
        
        Logger.qr("Submitting attendance", "Session ID: " + sessionId);
        Logger.i(Logger.TAG_QR, "  - username: '" + studentUsername + "'");
        Logger.i(Logger.TAG_QR, "  - timestamp: '" + System.currentTimeMillis() + "'");
        
        // Debug: Log the API base URL being used
        String apiBaseUrl = ApiClient.getInstance(this).getCurrentBaseUrl();
        Logger.i(Logger.TAG_QR, "API Base URL: " + apiBaseUrl);
        
        // Create request with validated data
        ApiService.SubmitAttendanceRequest request = new ApiService.SubmitAttendanceRequest(
                sessionId, studentUsername, System.currentTimeMillis()
        );
        
        // Log the complete request
        Logger.i(Logger.TAG_QR, "Request object created:");
        Logger.i(Logger.TAG_QR, "  - sessionId: '" + request.sessionId + "'");
        Logger.i(Logger.TAG_QR, "  - username: '" + request.studentUsername + "'");
        Logger.i(Logger.TAG_QR, "  - timestampMs: " + request.timestampMs);
        
        // API key no longer required - removed authentication
        
        Call<Attendance> call = apiService.submitAttendance(request);
        call.enqueue(new Callback<Attendance>() {
            @Override
            public void onResponse(Call<Attendance> call, Response<Attendance> response) {
                Logger.i(Logger.TAG_QR, "=== API RESPONSE DEBUG ===");
                Logger.i(Logger.TAG_QR, "Response code: " + response.code());
                Logger.i(Logger.TAG_QR, "Response successful: " + response.isSuccessful());
                
                if (response.isSuccessful()) {
                    Attendance result = response.body();
                    serverLogger.attendance("AttendanceSuccess", "Attendance submitted successfully");
                    serverLogger.flushLogs(); // Force send logs after attendance submission
                    if (result != null) {
                        Logger.apiResponse("POST", "api/v1/attendance", response.code(), "Attendance submitted successfully");
                        showSuccess("Attendance recorded successfully!");
                        Logger.attendance("Attendance Success", "Session: " + sessionId + ", Student: " + studentUsername);
                    } else {
                        showError("Invalid response from server");
                    }
                } else {
                    // Enhanced error logging
                    Logger.apiError("POST", "api/v1/attendance", response.code(), "Failed to submit attendance");
                    // Also send to server as ERROR with details
                    serverLogger.apiError("POST", "api/v1/attendance", response.code(), "Failed to submit attendance");
                    
                    // Parse error message from response body
                    String errorMessage = "Unknown error occurred";
                    try {
                        if (response.errorBody() != null) {
                            String errorBody = response.errorBody().string();
                            Logger.e(Logger.TAG_QR, "Error response body: " + errorBody);
                            
                            // Try to extract the actual error message from the response
                            if (errorBody.contains("Student already attended this session")) {
                                errorMessage = "You have already attended this session";
                            } else if (errorBody.contains("Session not found")) {
                                errorMessage = "Session not found or not active";
                            } else if (errorBody.contains("Invalid session")) {
                                errorMessage = "Invalid session ID";
                            } else if (errorBody.contains("Server error")) {
                                errorMessage = "Server error - please try again";
                            } else if (errorBody.contains("Access denied")) {
                                errorMessage = "Access denied - insufficient permissions";
                            } else {
                                // Try to extract a more specific error message
                                if (errorBody.contains("message")) {
                                    // Look for JSON message field
                                    int messageStart = errorBody.indexOf("\"message\":\"") + 10;
                                    int messageEnd = errorBody.indexOf("\"", messageStart);
                                    if (messageStart > 9 && messageEnd > messageStart) {
                                        errorMessage = errorBody.substring(messageStart, messageEnd);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        Logger.e(Logger.TAG_QR, "Failed to read error response body", e);
                        // Forward exception to server logs as ERROR with stackTrace
                        serverLogger.e(Logger.TAG_QR, "Failed to read error response body", e);
                        // Fall back to generic error handling
                        handleAttendanceError(response.code());
                        return;
                    }
                    
                    // Show the specific error message in a dialog
                    showErrorDialog(errorMessage);
                    // Report the parsed error to server as ERROR (attach synthetic exception for context)
                    if (response.code() == 409 || (errorMessage != null && errorMessage.toLowerCase().contains("already"))) {
                        serverLogger.e(ServerLogger.TAG_ATTENDANCE, "Duplicate attendance attempt for session " + sessionId + ", student " + studentUsername, new IllegalStateException("ALREADY_ATTENDED"));
                    } else {
                        serverLogger.e(Logger.TAG_QR, "Attendance submission error: " + errorMessage, new RuntimeException("HTTP " + response.code()));
                    }
                    serverLogger.flushLogs();
                }
            }
            
            @Override
            public void onFailure(Call<Attendance> call, Throwable t) {
                Logger.e(Logger.TAG_QR, "Attendance submission failed", t);
                // Forward network failure to server as ERROR with stackTrace
                serverLogger.e(Logger.TAG_QR, "Attendance submission failed", t);
                serverLogger.flushLogs();
                String errorMessage = getString(R.string.error_network_connection);
                if (t instanceof java.net.SocketTimeoutException || t instanceof java.net.ConnectException) {
                    errorMessage = getString(R.string.error_network_timeout);
                } else if (t instanceof java.net.UnknownHostException) {
                    errorMessage = getString(R.string.error_server_unavailable);
                }
                showError(errorMessage);
            }
        });
    }
    
    private void handleAttendanceError(int responseCode) {
        switch (responseCode) {
            case 409:
                showError("You have already marked attendance for this session");
                break;
            case 404:
                showError("Session not found or not active");
                break;
            case 400:
                showError("Invalid request - check session and student ID");
                break;
            case 401:
                showError("Network error - please check your connection");
                break;
            case 403:
                showError("Access denied - insufficient permissions");
                break;
            default:
                showError(getString(R.string.error_server_error));
                break;
        }
    }
    
    private void showSuccess(String message) {
        ToastUtils.showSuccess(this, message);
        
        // Return to student home after a delay
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }, 2000);
    }
    
    private void showError(String message) {
        ToastUtils.showError(this, message);
        
        // Resume scanning after error
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                startScanning();
            }
        }, 2000);
    }
    
    private void showErrorDialog(String message) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("⚠️ Error")
                .setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> {
                    dialog.dismiss();
                    // Resume scanning after user acknowledges the error
                    startScanning();
                })
                .setCancelable(false) // User must press OK to dismiss
                .show();
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanning();
            } else {
                showError("Camera permission is required to scan QR codes");
                finish();
            }
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            == PackageManager.PERMISSION_GRANTED) {
            startScanning();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        stopScanning();
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
    
    @Override
    protected void onDestroy() {
        if (serverLogger != null) {
            serverLogger.flushLogs();
        }
        super.onDestroy();
    }
}