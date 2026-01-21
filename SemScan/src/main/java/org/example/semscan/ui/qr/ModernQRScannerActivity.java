package org.example.semscan.ui.qr;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import org.example.semscan.R;
import org.example.semscan.constants.ApiConstants;
import org.example.semscan.data.api.ApiClient;
import org.example.semscan.data.api.ApiService;
import org.example.semscan.data.model.Attendance;
import org.example.semscan.data.model.QRPayload;
import org.example.semscan.utils.ErrorMessageHelper;
import org.example.semscan.utils.Logger;
import org.example.semscan.utils.PreferencesManager;
import org.example.semscan.utils.QRUtils;
import org.example.semscan.utils.ServerLogger;
import org.example.semscan.utils.ToastUtils;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ModernQRScannerActivity extends AppCompatActivity {
    
    private static final int CAMERA_PERMISSION_REQUEST = 1001;
    private static final String TAG = "ModernQRScanner";
    
    // UI Components
    private PreviewView previewView;
    private View overlayView;
    private ImageView scanFrame;
    private TextView instructionText;
    private TextView statusText;
    private ImageView backButton;
    private ImageView flashButton;
    
    // Camera and ML Kit
    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private BarcodeScanner barcodeScanner;
    private ExecutorService cameraExecutor;
    
    // App Components
    private PreferencesManager preferencesManager;
    private ApiService apiService;
    private ServerLogger serverLogger;
    
    // State
    private boolean isScanning = true;
    private boolean isFlashOn = false;
    private Long currentSessionId = null;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_modern_qr_scanner);
        
        initializeViews();
        setupClickListeners();
        initializeComponents();
        
        if (checkCameraPermission()) {
            startCamera();
        } else {
            requestCameraPermission();
        }
    }
    
    private void initializeViews() {
        previewView = findViewById(R.id.preview_view);
        overlayView = findViewById(R.id.overlay_view);
        scanFrame = findViewById(R.id.scan_frame);
        instructionText = findViewById(R.id.instruction_text);
        statusText = findViewById(R.id.status_text);
        backButton = findViewById(R.id.back_button);
        flashButton = findViewById(R.id.flash_button);
        
        // Set initial status
        statusText.setText("Ready to scan");
        statusText.setTextColor(ContextCompat.getColor(this, R.color.success_green));
    }
    
    private void setupClickListeners() {
        backButton.setOnClickListener(v -> {
            Logger.userAction("QR Back", "Student tapped back from QR scanner");
            if (serverLogger != null) {
                serverLogger.userAction("QR Back", "Student tapped back from QR scanner");
                serverLogger.flushLogs();
            }
            finish();
        });
        
        flashButton.setOnClickListener(v -> {
            toggleFlash();
            if (serverLogger != null) {
                serverLogger.userAction("Toggle Flash", "Student toggled flashlight");
            }
        });
        
        // Add smooth animations
        backButton.setOnTouchListener((v, event) -> {
            animateButtonPress(v);
            return false;
        });
        
        flashButton.setOnTouchListener((v, event) -> {
            animateButtonPress(v);
            return false;
        });
    }
    
    private void initializeComponents() {
        preferencesManager = PreferencesManager.getInstance(this);
        apiService = ApiClient.getInstance(this).getApiService();
        serverLogger = ServerLogger.getInstance(this);
        String username = preferencesManager.getUserName();
        String userRole = preferencesManager.getUserRole();
        
        // Debug: Log stored preferences
        Logger.i(TAG, "=== User Preferences Debug ===");
        Logger.i(TAG, "Username: " + (username != null ? username : "NULL"));
        Logger.i(TAG, "User Role: " + (userRole != null ? userRole : "NULL"));
        Logger.i(TAG, "Is Participant: " + preferencesManager.isParticipant());
        Logger.i(TAG, "Is Presenter: " + preferencesManager.isPresenter());
        Logger.i(TAG, "==============================");
        
        // CRITICAL: Check if username exists - if not, redirect to login
        if (username == null || username.isEmpty()) {
            Logger.e(TAG, "ERROR: Username is NULL or empty in ModernQRScannerActivity!");
            Logger.e(TAG, "User Role: " + userRole);
            Logger.e(TAG, "Cannot scan QR without username. Redirecting to login.");
            Toast.makeText(this, "Username not found. Please log in again.", Toast.LENGTH_LONG).show();
            // Navigate to login
            android.content.Intent intent = new android.content.Intent(this, org.example.semscan.ui.auth.LoginActivity.class);
            intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }
        
        serverLogger.updateUserContext(username, userRole);
        serverLogger.userAction("Open QR Scanner", "ModernQRScannerActivity opened as " + username);
        cameraExecutor = Executors.newSingleThreadExecutor();
        
        // Initialize ML Kit Barcode Scanner
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);
    }
    
    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                == PackageManager.PERMISSION_GRANTED;
    }
    
    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.CAMERA}, 
                CAMERA_PERMISSION_REQUEST);
    }
    
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = 
                ProcessCameraProvider.getInstance(this);
        
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                Logger.e(TAG, "Camera initialization failed", e);
                showError("Camera initialization failed");
            }
        }, ContextCompat.getMainExecutor(this));
    }
    
    private void bindCameraUseCases() {
        if (cameraProvider == null) return;
        
        // Preview use case
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        
        // Image analysis use case for QR code detection
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        
        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);
        
        // Camera selector
        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
        
        try {
            // Unbind use cases before rebinding
            cameraProvider.unbindAll();
            
            // Bind use cases to camera
            camera = cameraProvider.bindToLifecycle(
                    (LifecycleOwner) this,
                    cameraSelector,
                    preview,
                    imageAnalysis
            );
            
            Logger.i(TAG, "Camera started successfully");
            updateStatus("Camera ready", R.color.success_green);
            
        } catch (Exception e) {
            Logger.e(TAG, "Camera binding failed", e);
            showError("Camera binding failed");
        }
    }
    
    private void analyzeImage(ImageProxy image) {
        if (image == null) return;

        if (!isScanning || isFinishing() || isDestroyed()) {
            image.close();
            return;
        }

        try {
            if (image.getImage() == null) {
                image.close();
                return;
            }

            InputImage inputImage = InputImage.fromMediaImage(
                    image.getImage(),
                    image.getImageInfo().getRotationDegrees()
            );

            if (barcodeScanner == null) {
                image.close();
                return;
            }

            barcodeScanner.process(inputImage)
                .addOnSuccessListener(barcodes -> {
                    if (!barcodes.isEmpty() && isScanning && !isFinishing() && !isDestroyed()) {
                        Barcode barcode = barcodes.get(0);
                        String qrContent = barcode.getRawValue();

                        if (qrContent != null) {
                            runOnUiThread(() -> handleQRResult(qrContent));
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Logger.e(TAG, "Barcode scanning failed", e);
                })
                .addOnCompleteListener(task -> {
                    try {
                        image.close();
                    } catch (Exception e) {
                        Logger.e(TAG, "Error closing image", e);
                    }
                });
        } catch (Exception e) {
            Logger.e(TAG, "Error analyzing image", e);
            try {
                image.close();
            } catch (Exception ignored) {}
        }
    }
    
    private void handleQRResult(String qrContent) {
        if (!isScanning) return;
        
        isScanning = false;
        Logger.qr("QR Code Scanned", "Content: " + qrContent);
        if (serverLogger != null) {
            serverLogger.qr("QR Code Scanned", "Content: " + qrContent);
        }
        
        // Parse QR content
        QRPayload payload = QRUtils.parseQRContent(qrContent);
        if (payload == null || !QRUtils.isValidQRContent(qrContent)) {
            Logger.qr("Invalid QR Code", "Failed to parse QR content: " + qrContent);
            if (serverLogger != null) {
                serverLogger.qr("Invalid QR Code", "Failed to parse QR content: " + qrContent);
            }
            updateStatus("Invalid QR code format", R.color.error_red);
            showError("Invalid QR code format. Expected: {\"sessionId\":\"session-xxx\"}");
            resumeScanning();
            return;
        }
        
        Long sessionId = payload.getSessionId();
        if (sessionId == null || sessionId <= 0) {
            Logger.qr("Invalid QR Code", "Session ID is null or empty");
            if (serverLogger != null) {
                serverLogger.qr("Invalid QR Code", "Session ID is null or empty");
            }
            updateStatus("QR code missing session ID", R.color.error_red);
            showError("QR code missing session ID");
            resumeScanning();
            return;
        }
        
        Logger.qr("QR Code Parsed", "Session ID: " + sessionId);
        if (serverLogger != null) {
            serverLogger.qr("QR Code Parsed", "Session ID: " + sessionId);
        }
        currentSessionId = sessionId;
        
        updateStatus("Processing...", R.color.warning_orange);
        animateScanFrame();
        
        // Submit attendance
        submitAttendance(sessionId);
    }
    
    private void submitAttendance(Long sessionId) {
        String studentUsername = preferencesManager.getUserName();
        String userRole = preferencesManager.getUserRole();
        
        // Debug: Log all user info
        Logger.i(TAG, "=== Attendance Submission Debug ===");
        Logger.i(TAG, "Session ID: " + sessionId);
        Logger.i(TAG, "Student Username: " + (studentUsername != null ? studentUsername : "NULL"));
        Logger.i(TAG, "User Role: " + (userRole != null ? userRole : "NULL"));
        Logger.i(TAG, "Is Participant: " + preferencesManager.isParticipant());
        Logger.i(TAG, "Is Presenter: " + preferencesManager.isPresenter());
        Logger.i(TAG, "===================================");
        
        if (TextUtils.isEmpty(studentUsername)) {
            Logger.e(TAG, "Student username not found or invalid");
            Logger.e(TAG, "Current user role: " + userRole);
            Logger.e(TAG, "Is participant check: " + preferencesManager.isParticipant());
            if (serverLogger != null) {
                serverLogger.e(ServerLogger.TAG_QR, "Student username not found or invalid - Role: " + userRole);
            }
            showError("Student username not found. Please log in again.\n\nDebug: Role=" + userRole + ", Username=" + studentUsername);
            return;
        }
        
        // Check if user is actually a participant
        if (!preferencesManager.isParticipant()) {
            Logger.e(TAG, "User is not a participant - Role: " + userRole);
            if (serverLogger != null) {
                serverLogger.e(ServerLogger.TAG_QR, "User is not a participant - Role: " + userRole);
            }
            showError("Only students can scan QR codes for attendance.\n\nCurrent role: " + userRole);
            return;
        }

        String logJson = String.format(
            "{\"event\":\"QR_SCANNED\",\"sessionId\":%d,\"student\":\"%s\",\"role\":\"%s\",\"timestamp\":%d}",
            sessionId, studentUsername, preferencesManager.getUserRole(), System.currentTimeMillis()
        );
        Logger.i(TAG, logJson);
        if (serverLogger != null) {
            serverLogger.i(ServerLogger.TAG_QR, logJson);
        }
        proceedWithAttendanceSubmission(sessionId, studentUsername);
    }
    
    /**
     * Proceed with attendance submission - backend will validate session status.
     */
    private void proceedWithAttendanceSubmission(Long sessionId, String studentUsername) {
        long timestampMs = System.currentTimeMillis();
        ApiService.SubmitAttendanceRequest request = new ApiService.SubmitAttendanceRequest(
            sessionId, studentUsername, timestampMs
        );

        String apiBaseUrl = ApiClient.getInstance(this).getCurrentBaseUrl();
        String logJson = String.format(
            "{\"event\":\"SUBMIT_REQUEST\",\"method\":\"POST\",\"endpoint\":\"/api/v1/attendance\",\"baseUrl\":\"%s\",\"sessionId\":%d,\"student\":\"%s\",\"timestamp\":%d}",
            apiBaseUrl, sessionId, studentUsername, timestampMs
        );
        Logger.i(TAG, logJson);
        if (serverLogger != null) {
            serverLogger.i(ServerLogger.TAG_QR, logJson);
        }

        long requestStartTime = System.currentTimeMillis();
        Call<Attendance> call = apiService.submitAttendance(request);
        
        call.enqueue(new Callback<Attendance>() {
            @Override
            public void onResponse(Call<Attendance> call, Response<Attendance> response) {
                long requestDuration = System.currentTimeMillis() - requestStartTime;
                String responseLogJson = String.format(
                    "{\"event\":\"RESPONSE_RECEIVED\",\"code\":%d,\"successful\":%b,\"hasBody\":%b,\"hasErrorBody\":%b,\"durationMs\":%d,\"url\":\"%s\"}",
                    response.code(), response.isSuccessful(), response.body() != null,
                    response.errorBody() != null, requestDuration,
                    call.request() != null ? call.request().url() : "NULL"
                );
                Logger.i(TAG, responseLogJson);

                if (response.isSuccessful()) {
                    Attendance result = response.body();
                    if (result != null) {
                        String logJson = String.format(
                            "{\"event\":\"ATTENDANCE_SUCCESS\",\"attendanceId\":%s,\"sessionId\":%d,\"expectedSessionId\":%d,\"student\":\"%s\",\"method\":\"%s\",\"time\":\"%s\",\"alreadyPresent\":%b,\"durationMs\":%d}",
                            result.getAttendanceId(), result.getSessionId(), sessionId,
                            result.getStudentUsername(), result.getMethod(),
                            result.getAttendanceTime(), result.isAlreadyPresent(), requestDuration
                        );
                        Logger.i(TAG, logJson);
                        if (serverLogger != null) {
                            serverLogger.i(ServerLogger.TAG_QR, logJson);
                            serverLogger.flushLogs();
                        }
                        vibrateSuccess();
                        updateStatus("Success!", R.color.success_green);
                        showSuccess("Attendance recorded successfully!");

                        // Return to previous screen after delay
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            if (!isFinishing() && !isDestroyed()) {
                                finish();
                            }
                        }, 2000);
                    } else {
                        Logger.e(TAG, "Response body is NULL, code=" + response.code());
                        updateStatus("Invalid response", R.color.error_red);
                        showError("Invalid response from server");
                        resumeScanning();
                    }
                } else {
                    String logJson = String.format(
                        "{\"event\":\"ATTENDANCE_FAILED\",\"sessionId\":%d,\"student\":\"%s\",\"code\":%d,\"message\":\"%s\",\"url\":\"%s\",\"durationMs\":%d}",
                        sessionId, studentUsername, response.code(), response.message(),
                        call.request() != null ? call.request().url() : "NULL", requestDuration
                    );
                    Logger.e(TAG, logJson);
                    if (serverLogger != null) {
                        serverLogger.e(ServerLogger.TAG_QR, logJson);
                    }
                    updateStatus("Request failed", R.color.error_red);
                    
                    // Parse error message from response body
                    String errorMessage = "Unknown error occurred";
                    try {
                        if (response.errorBody() != null) {
                            String errorBodyString = response.errorBody().string();
                            Logger.e(TAG, "Error body: " + errorBodyString);

                            if (errorBodyString != null && !errorBodyString.trim().isEmpty()) {
                                // Try to parse as JSON first
                                try {
                                    com.google.gson.JsonObject jsonObject = new com.google.gson.JsonParser().parse(errorBodyString).getAsJsonObject();
                                    if (jsonObject.has("message") && jsonObject.get("message").isJsonPrimitive()) {
                                        errorMessage = jsonObject.get("message").getAsString();
                                    } else if (jsonObject.has("error") && jsonObject.get("error").isJsonPrimitive()) {
                                        errorMessage = jsonObject.get("error").getAsString();
                                    }
                                } catch (Exception jsonEx) {
                                    // Not valid JSON - try string matching
                                    if (errorBodyString.contains("Session is not open") ||
                                        errorBodyString.contains("status: CLOSED") ||
                                        errorBodyString.contains("not open") ||
                                        errorBodyString.contains("session is closed")) {
                                        Logger.e(TAG, "Session not open error - sessionId=" + sessionId);
                                        errorMessage = "Session validation failed. Please try scanning again or contact your presenter if the problem persists.";
                                    } else {
                                        errorMessage = ErrorMessageHelper.cleanBackendMessage(errorBodyString);
                                        if (errorMessage == null || errorMessage.equals(errorBodyString)) {
                                            if (errorBodyString.contains("\"message\":\"")) {
                                                int messageStart = errorBodyString.indexOf("\"message\":\"") + 11;
                                                int messageEnd = errorBodyString.indexOf("\"", messageStart);
                                                if (messageEnd > messageStart) {
                                                    errorMessage = ErrorMessageHelper.cleanBackendMessage(
                                                        errorBodyString.substring(messageStart, messageEnd));
                                                }
                                            }
                                            if (errorMessage == null || errorMessage.length() > 200) {
                                                errorMessage = ErrorMessageHelper.getHttpErrorMessage(
                                                    ModernQRScannerActivity.this, response.code(), errorBodyString);
                                            }
                                        }
                                    }
                                }
                            } else {
                                errorMessage = "Request failed with status " + response.code();
                            }
                        } else {
                            errorMessage = "Request failed with status " + response.code();
                        }
                    } catch (Exception e) {
                        Logger.e(TAG, "Error parsing response: " + e.getMessage());
                        handleAttendanceError(response.code());
                        return;
                    }

                    Logger.e(TAG, "Showing error: " + errorMessage);
                    showErrorDialog(errorMessage);
                    if (serverLogger != null) {
                        serverLogger.attendance("Attendance Failed", "Session: " + sessionId + ", Reason: " + errorMessage);
                        serverLogger.flushLogs();
                    }
                    resumeScanning();
                }
            }
            
            @Override
            public void onFailure(Call<Attendance> call, Throwable t) {
                long requestDuration = System.currentTimeMillis() - requestStartTime;
                String logJson = String.format(
                    "{\"event\":\"NETWORK_FAILURE\",\"sessionId\":%d,\"student\":\"%s\",\"durationMs\":%d,\"exceptionType\":\"%s\",\"message\":\"%s\",\"url\":\"%s\",\"method\":\"%s\"}",
                    sessionId, studentUsername, requestDuration,
                    t.getClass().getSimpleName(), t.getMessage(),
                    call.request() != null ? call.request().url() : "NULL",
                    call.request() != null ? call.request().method() : "NULL"
                );
                Logger.e(TAG, logJson, t);
                if (serverLogger != null) {
                    serverLogger.e(ServerLogger.TAG_QR, logJson, t);
                }
                updateStatus("Network error", R.color.error_red);
                String errorMessage = ErrorMessageHelper.getNetworkErrorMessage(ModernQRScannerActivity.this, t);
                showError(errorMessage);
                resumeScanning();
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
        resumeScanning();
    }
    
    private void resumeScanning() {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                isScanning = true;
                updateStatus("Ready to scan", R.color.success_green);
            }
        }, 2000);
    }
    
    private void toggleFlash() {
        if (camera == null) return;
        
        if (isFlashOn) {
            camera.getCameraControl().enableTorch(false);
            isFlashOn = false;
            flashButton.setImageResource(R.drawable.ic_flash_off);
            Logger.qr("Flashlight", "Flashlight turned off");
        } else {
            camera.getCameraControl().enableTorch(true);
            isFlashOn = true;
            flashButton.setImageResource(R.drawable.ic_flash_on);
            Logger.qr("Flashlight", "Flashlight turned on");
        }
        
        animateButtonPress(flashButton);
    }
    
    private void updateStatus(String message, int colorRes) {
        statusText.setText(message);
        statusText.setTextColor(ContextCompat.getColor(this, colorRes));
        
        // Add fade animation
        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(300);
        statusText.startAnimation(fadeIn);
    }
    
    private void animateScanFrame() {
        AlphaAnimation pulse = new AlphaAnimation(1.0f, 0.3f);
        pulse.setDuration(500);
        pulse.setRepeatCount(Animation.INFINITE);
        pulse.setRepeatMode(Animation.REVERSE);
        scanFrame.startAnimation(pulse);
    }
    
    private void animateButtonPress(View view) {
        view.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(100)
                .withEndAction(() -> {
                    view.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(100)
                            .start();
                })
                .start();
    }
    
    private void showSuccess(String message) {
        ToastUtils.showSuccess(this, message);
    }
    
    private void showError(String message) {
        ToastUtils.showError(this, message);
    }
    
    private void showErrorDialog(String message) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("⚠️ Error")
                .setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> {
                    dialog.dismiss();
                    // Resume scanning after user acknowledges the error
                    resumeScanning();
                })
                .setCancelable(false) // User must press OK to dismiss
                .show();
    }

    private void vibrateSuccess() {
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            VibrationEffect effect = VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE);
            vibrator.vibrate(effect);
        } else {
            vibrator.vibrate(80);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                showError("Camera permission is required to scan QR codes");
                finish();
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        try {
            // Stop scanning first
            isScanning = false;

            // Close barcode scanner
            if (barcodeScanner != null) {
                try {
                    barcodeScanner.close();
                } catch (Exception e) {
                    Logger.e(TAG, "Error closing barcode scanner", e);
                }
                barcodeScanner = null;
            }

            // Unbind camera
            if (cameraProvider != null) {
                try {
                    cameraProvider.unbindAll();
                } catch (Exception e) {
                    Logger.e(TAG, "Error unbinding camera", e);
                }
                cameraProvider = null;
            }

            // Shutdown executor
            if (cameraExecutor != null) {
                try {
                    cameraExecutor.shutdown();
                } catch (Exception e) {
                    Logger.e(TAG, "Error shutting down camera executor", e);
                }
                cameraExecutor = null;
            }

            camera = null;
        } catch (Exception e) {
            Logger.e(TAG, "Error in onDestroy", e);
        } finally {
            super.onDestroy();
        }
    }
}


