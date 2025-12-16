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
        if (!isScanning) {
            image.close();
            return;
        }
        
        InputImage inputImage = InputImage.fromMediaImage(
                image.getImage(), 
                image.getImageInfo().getRotationDegrees()
        );
        
        barcodeScanner.process(inputImage)
                .addOnSuccessListener(barcodes -> {
                    if (!barcodes.isEmpty() && isScanning) {
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
                .addOnCompleteListener(task -> image.close());
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

        // Skip frontend validation - go directly to backend
        // The backend will validate the session status correctly
        // Frontend validation was causing issues when backend doesn't return all open sessions
        Logger.i(TAG, "═══════════════════════════════════════════════════════════");
        Logger.i(TAG, "QR CODE SCANNED - ATTENDANCE SUBMISSION STARTING");
        Logger.i(TAG, "═══════════════════════════════════════════════════════════");
        Logger.i(TAG, "Session ID: " + sessionId);
        Logger.i(TAG, "Student Username: " + studentUsername);
        Logger.i(TAG, "Timestamp: " + System.currentTimeMillis());
        Logger.i(TAG, "Proceeding directly to backend - no frontend validation");
        if (serverLogger != null) {
            serverLogger.i(ServerLogger.TAG_QR, "QR CODE SCANNED - ATTENDANCE SUBMISSION | Session ID: " + sessionId + ", Student: " + studentUsername);
        }
        proceedWithAttendanceSubmission(sessionId, studentUsername);
    }
    
    /**
     * Proceed with attendance submission - backend will validate session status.
     */
    private void proceedWithAttendanceSubmission(Long sessionId, String studentUsername) {
        Logger.i(TAG, "───────────────────────────────────────────────────────────");
        Logger.i(TAG, "PROCEEDING WITH ATTENDANCE SUBMISSION");
        Logger.i(TAG, "───────────────────────────────────────────────────────────");
        Logger.i(TAG, "Session ID: " + sessionId + " (Type: " + (sessionId != null ? sessionId.getClass().getSimpleName() : "NULL") + ")");
        Logger.i(TAG, "Student Username: '" + studentUsername + "' (Length: " + (studentUsername != null ? studentUsername.length() : 0) + ")");
        
        if (serverLogger != null) {
            serverLogger.attendance("Submit Attendance", "Session: " + sessionId + ", Student: " + studentUsername);
            serverLogger.i(ServerLogger.TAG_QR, "PROCEEDING WITH ATTENDANCE SUBMISSION | Session ID: " + sessionId + ", Student: " + studentUsername);
        }
        
        // Debug: Log the API base URL being used
        String apiBaseUrl = ApiClient.getInstance(this).getCurrentBaseUrl();
        Logger.i(TAG, "API Base URL: " + apiBaseUrl);
        Logger.i(TAG, "Full Endpoint URL: " + apiBaseUrl + "/attendance");
        if (serverLogger != null) {
            serverLogger.api("POST", "api/v1/attendance", "Base URL: " + apiBaseUrl + ", Session: " + sessionId + ", Student: " + studentUsername);
            serverLogger.i(ServerLogger.TAG_QR, "API Base URL: " + apiBaseUrl);
        }
        
        long timestampMs = System.currentTimeMillis();
        Logger.i(TAG, "Creating request with timestamp: " + timestampMs + " (" + new java.util.Date(timestampMs) + ")");
        
        ApiService.SubmitAttendanceRequest request = new ApiService.SubmitAttendanceRequest(
            sessionId, studentUsername, timestampMs
        );
        
        // Log request details
        Logger.i(TAG, "═══════════════════════════════════════════════════════════");
        Logger.i(TAG, "REQUEST DETAILS");
        Logger.i(TAG, "═══════════════════════════════════════════════════════════");
        Logger.i(TAG, "Method: POST");
        Logger.i(TAG, "Endpoint: /api/v1/attendance");
        Logger.i(TAG, "Full URL: " + apiBaseUrl + "/attendance");
        Logger.i(TAG, "Session ID: " + sessionId);
        Logger.i(TAG, "Student Username: '" + studentUsername + "'");
        Logger.i(TAG, "Timestamp (ms): " + timestampMs);
        Logger.i(TAG, "Timestamp (readable): " + new java.util.Date(timestampMs));
        Logger.i(TAG, "Request Object: " + request.toString());
        Logger.i(TAG, "═══════════════════════════════════════════════════════════");
        
        if (serverLogger != null) {
            serverLogger.i(ServerLogger.TAG_QR, "REQUEST DETAILS | Method: POST, Endpoint: /api/v1/attendance, " +
                "Session ID: " + sessionId + ", Student: " + studentUsername + ", Timestamp: " + timestampMs);
        }
        
        // API key no longer required - removed authentication
        
        Logger.i(TAG, "Sending HTTP request to backend...");
        if (serverLogger != null) {
            serverLogger.i(ServerLogger.TAG_QR, "Sending HTTP request to backend...");
        }
        
        long requestStartTime = System.currentTimeMillis();
        Call<Attendance> call = apiService.submitAttendance(request);
        
        Logger.i(TAG, "Request enqueued. Waiting for response...");
        if (serverLogger != null) {
            serverLogger.i(ServerLogger.TAG_QR, "Request enqueued. Waiting for response...");
        }
        
        call.enqueue(new Callback<Attendance>() {
            @Override
            public void onResponse(Call<Attendance> call, Response<Attendance> response) {
                long requestDuration = System.currentTimeMillis() - requestStartTime;
                Logger.i(TAG, "═══════════════════════════════════════════════════════════");
                Logger.i(TAG, "RESPONSE RECEIVED");
                Logger.i(TAG, "═══════════════════════════════════════════════════════════");
                Logger.i(TAG, "Request Duration: " + requestDuration + "ms");
                Logger.i(TAG, "Response Code: " + response.code());
                Logger.i(TAG, "Response Message: " + response.message());
                Logger.i(TAG, "Is Successful: " + response.isSuccessful());
                Logger.i(TAG, "Has Body: " + (response.body() != null));
                Logger.i(TAG, "Has Error Body: " + (response.errorBody() != null));
                Logger.i(TAG, "Request URL: " + (call.request() != null ? call.request().url() : "NULL"));
                Logger.i(TAG, "═══════════════════════════════════════════════════════════");
                
                if (serverLogger != null) {
                    serverLogger.i(ServerLogger.TAG_QR, "RESPONSE RECEIVED | Duration: " + requestDuration + "ms, " +
                        "Status Code: " + response.code() + ", Is Successful: " + response.isSuccessful() + 
                        ", Has Body: " + (response.body() != null) + ", Has Error Body: " + (response.errorBody() != null));
                }
                
                if (response.isSuccessful()) {
                    Attendance result = response.body();
                    if (result != null) {
                        Logger.i(TAG, "═══════════════════════════════════════════════════════════");
                        Logger.i(TAG, "✅ SUCCESS - ATTENDANCE RECORDED");
                        Logger.i(TAG, "═══════════════════════════════════════════════════════════");
                        Logger.i(TAG, "Attendance ID: " + result.getAttendanceId());
                        Logger.i(TAG, "Session ID: " + result.getSessionId() + " (Expected: " + sessionId + ", Match: " + (result.getSessionId() != null && result.getSessionId().equals(sessionId)) + ")");
                        Logger.i(TAG, "Student Username: " + result.getStudentUsername() + " (Expected: " + studentUsername + ", Match: " + (result.getStudentUsername() != null && result.getStudentUsername().equals(studentUsername)) + ")");
                        Logger.i(TAG, "Attendance Time: " + result.getAttendanceTime());
                        Logger.i(TAG, "Method: " + result.getMethod());
                        Logger.i(TAG, "Already Present: " + result.isAlreadyPresent());
                        Logger.i(TAG, "═══════════════════════════════════════════════════════════");
                        
                        if (serverLogger != null) {
                            serverLogger.i(ServerLogger.TAG_QR, "✅ SUCCESS - ATTENDANCE RECORDED | " +
                                "Attendance ID: " + result.getAttendanceId() + ", Session ID: " + result.getSessionId() + 
                                " (Expected: " + sessionId + "), Student: " + result.getStudentUsername() + 
                                " (Expected: " + studentUsername + "), Method: " + result.getMethod());
                        }
                        
                        Logger.apiResponse("POST", "api/v1/attendance", response.code(), "Attendance submitted successfully");
                        if (serverLogger != null) {
                            serverLogger.apiResponse("POST", "api/v1/attendance", response.code(), "Attendance submitted successfully");
                            serverLogger.attendance("Attendance Success", "Session: " + sessionId + ", Username: " + studentUsername);
                            serverLogger.flushLogs();
                        }
                        vibrateSuccess();
                        updateStatus("Success!", R.color.success_green);
                        showSuccess("Attendance recorded successfully!");
                        Logger.attendance("Attendance Success", "Session: " + sessionId + ", Username: " + studentUsername);
                        
                        // Return to previous screen after delay
                        new android.os.Handler().postDelayed(() -> finish(), 2000);
                    } else {
                        Logger.e(TAG, "=== Error: Response body is NULL ===");
                        Logger.e(TAG, "Response Code: " + response.code());
                        Logger.e(TAG, "Response Message: " + response.message());
                        updateStatus("Invalid response", R.color.error_red);
                        showError("Invalid response from server");
                        resumeScanning();
                    }
                } else {
                    Logger.e(TAG, "═══════════════════════════════════════════════════════════");
                    Logger.e(TAG, "❌ ERROR RESPONSE - ATTENDANCE SUBMISSION FAILED");
                    Logger.e(TAG, "═══════════════════════════════════════════════════════════");
                    Logger.e(TAG, "Session ID Submitted: " + sessionId);
                    Logger.e(TAG, "Student Username: " + studentUsername);
                    Logger.e(TAG, "Response Code: " + response.code());
                    Logger.e(TAG, "Response Message: " + response.message());
                    Logger.e(TAG, "Request URL: " + (call.request() != null ? call.request().url() : "NULL"));
                    Logger.e(TAG, "Request Method: " + (call.request() != null ? call.request().method() : "NULL"));
                    Logger.e(TAG, "Has Error Body: " + (response.errorBody() != null));
                    Logger.e(TAG, "═══════════════════════════════════════════════════════════");
                    
                    Logger.apiError("POST", "api/v1/attendance", response.code(), "Failed to submit attendance");
                    if (serverLogger != null) {
                        serverLogger.apiError("POST", "api/v1/attendance", response.code(), "Failed to submit attendance for student " + studentUsername);
                        serverLogger.e(ServerLogger.TAG_QR, "❌ ERROR RESPONSE - ATTENDANCE SUBMISSION FAILED | " +
                            "Session ID: " + sessionId + ", Student: " + studentUsername + ", Status Code: " + response.code());
                    }
                    updateStatus("Request failed", R.color.error_red);
                    
                    // Parse error message from response body
                    String errorMessage = "Unknown error occurred";
                    try {
                        if (response.errorBody() != null) {
                            // Create a copy of the error body source to read it
                            okhttp3.ResponseBody errorBody = response.errorBody();
                            String errorBodyString = errorBody.string();
                            
                            Logger.e(TAG, "───────────────────────────────────────────────────────────");
                            Logger.e(TAG, "ERROR RESPONSE BODY");
                            Logger.e(TAG, "───────────────────────────────────────────────────────────");
                            Logger.e(TAG, "Full Error Body: " + errorBodyString);
                            Logger.e(TAG, "Error Body Length: " + errorBodyString.length());
                            Logger.e(TAG, "Error Body is Empty: " + errorBodyString.trim().isEmpty());
                            Logger.e(TAG, "───────────────────────────────────────────────────────────");
                            
                            // Log to server logger as well
                            if (serverLogger != null) {
                                serverLogger.e(ServerLogger.TAG_QR, "ERROR RESPONSE BODY: " + errorBodyString);
                            }
                            
                            if (errorBodyString != null && !errorBodyString.trim().isEmpty()) {
                                // Try to parse as JSON first
                                try {
                                    com.google.gson.JsonObject jsonObject = new com.google.gson.JsonParser().parse(errorBodyString).getAsJsonObject();
                                    if (jsonObject.has("message") && jsonObject.get("message").isJsonPrimitive()) {
                                        errorMessage = jsonObject.get("message").getAsString();
                                        Logger.i(TAG, "Extracted error message from JSON: " + errorMessage);
                                    } else if (jsonObject.has("error") && jsonObject.get("error").isJsonPrimitive()) {
                                        errorMessage = jsonObject.get("error").getAsString();
                                        Logger.i(TAG, "Extracted error from JSON: " + errorMessage);
                                    }
                                } catch (Exception jsonEx) {
                                    Logger.i(TAG, "Error body is not valid JSON, trying string matching");
                                    
                                    // Try to extract the actual error message from the response using string matching
                                    if (errorBodyString.contains("Session is not open") || 
                                        errorBodyString.contains("status: CLOSED") || 
                                        errorBodyString.contains("not open") ||
                                        errorBodyString.contains("session is closed")) {
                                        // This is likely a backend bug where it's checking the wrong session
                                        // Log detailed info for debugging
                                        Logger.e(TAG, "═══════════════════════════════════════════════════════════");
                                        Logger.e(TAG, "🚨 BACKEND VALIDATION ERROR DETECTED");
                                        Logger.e(TAG, "═══════════════════════════════════════════════════════════");
                                        Logger.e(TAG, "Session ID Submitted: " + sessionId);
                                        Logger.e(TAG, "Student Username: " + studentUsername);
                                        Logger.e(TAG, "Error: Session reported as 'not open' or 'CLOSED'");
                                        Logger.e(TAG, "⚠️  POSSIBLE BACKEND BUG:");
                                        Logger.e(TAG, "   Backend may be checking by slot_id instead of session_id");
                                        Logger.e(TAG, "   This prevents attendance to second session in same slot");
                                        Logger.e(TAG, "   See: docs/BACKEND_FIX_MULTIPLE_PRESENTERS_SESSION.md");
                                        Logger.e(TAG, "═══════════════════════════════════════════════════════════");
                                        
                                        if (serverLogger != null) {
                                            serverLogger.e(ServerLogger.TAG_QR, "🚨 BACKEND VALIDATION ERROR DETECTED | " +
                                                "Session ID: " + sessionId + ", Student: " + studentUsername + 
                                                ", Error: Session reported as 'not open', " +
                                                "⚠️ POSSIBLE BACKEND BUG: Checking wrong session (slot_id vs session_id)");
                                        }
                                        errorMessage = "Session validation failed. Please try scanning again or contact your presenter if the problem persists.";
                                    } else {
                                        // Use ErrorMessageHelper to clean up the message
                                        errorMessage = ErrorMessageHelper.cleanBackendMessage(errorBodyString);
                                        if (errorMessage == null || errorMessage.equals(errorBodyString)) {
                                            // If cleaning didn't help, try to extract JSON message
                                            if (errorBodyString.contains("\"message\":\"")) {
                                                int messageStart = errorBodyString.indexOf("\"message\":\"");
                                                messageStart += 10; // Length of "\"message\":\""
                                                int messageEnd = errorBodyString.indexOf("\"", messageStart);
                                                if (messageEnd > messageStart) {
                                                    String extracted = errorBodyString.substring(messageStart, messageEnd);
                                                    errorMessage = ErrorMessageHelper.cleanBackendMessage(extracted);
                                                }
                                            }
                                            // If still no good message, use generic
                                            if (errorMessage == null || errorMessage.length() > 200) {
                                                errorMessage = ErrorMessageHelper.getHttpErrorMessage(
                                                    ModernQRScannerActivity.this, response.code(), errorBodyString);
                                            }
                                        }
                                    }
                                }
                            } else {
                                Logger.w(TAG, "Error body is null or empty");
                                // Fall back to HTTP status code message
                                errorMessage = "Request failed with status " + response.code();
                            }
                        } else {
                            Logger.w(TAG, "Response error body is null");
                            errorMessage = "Request failed with status " + response.code();
                        }
                    } catch (Exception e) {
                        Logger.e(TAG, "=== Exception reading error body ===");
                        Logger.e(TAG, "Exception Type: " + e.getClass().getName());
                        Logger.e(TAG, "Exception Message: " + e.getMessage());
                        Logger.e(TAG, "Stack trace:", e);
                        Logger.e(TAG, "====================================");
                        
                        if (serverLogger != null) {
                            serverLogger.e(ServerLogger.TAG_QR, "Exception reading error body", e);
                        }
                        
                        // Fall back to generic error handling
                        handleAttendanceError(response.code());
                        return;
                    }
                    
                    Logger.e(TAG, "───────────────────────────────────────────────────────────");
                    Logger.e(TAG, "FINAL ERROR MESSAGE FOR USER");
                    Logger.e(TAG, "───────────────────────────────────────────────────────────");
                    Logger.e(TAG, "Error Message: " + errorMessage);
                    Logger.e(TAG, "Session ID: " + sessionId);
                    Logger.e(TAG, "Student: " + studentUsername);
                    Logger.e(TAG, "───────────────────────────────────────────────────────────");
                    
                    // Show the specific error message in a dialog
                    showErrorDialog(errorMessage);
                    if (serverLogger != null) {
                        serverLogger.attendance("Attendance Failed", "Session: " + sessionId + ", Student: " + studentUsername + ", Reason: " + errorMessage);
                        serverLogger.e(ServerLogger.TAG_QR, "FINAL ERROR MESSAGE: " + errorMessage);
                        serverLogger.flushLogs();
                    }
                    resumeScanning();
                }
            }
            
            @Override
            public void onFailure(Call<Attendance> call, Throwable t) {
                long requestDuration = System.currentTimeMillis() - requestStartTime;
                Logger.e(TAG, "═══════════════════════════════════════════════════════════");
                Logger.e(TAG, "❌ NETWORK FAILURE - REQUEST DID NOT REACH SERVER");
                Logger.e(TAG, "═══════════════════════════════════════════════════════════");
                Logger.e(TAG, "Request Duration: " + requestDuration + "ms");
                Logger.e(TAG, "Exception Type: " + t.getClass().getName());
                Logger.e(TAG, "Exception Message: " + t.getMessage());
                Logger.e(TAG, "Request URL: " + (call.request() != null ? call.request().url() : "NULL"));
                Logger.e(TAG, "Request Method: " + (call.request() != null ? call.request().method() : "NULL"));
                Logger.e(TAG, "Session ID: " + sessionId);
                Logger.e(TAG, "Student Username: '" + studentUsername + "'");
                Logger.e(TAG, "Stack trace:", t);
                Logger.e(TAG, "═══════════════════════════════════════════════════════════");
                
                if (serverLogger != null) {
                    serverLogger.e(ServerLogger.TAG_QR, "❌ NETWORK FAILURE | Exception: " + t.getClass().getSimpleName() + 
                        ", Message: " + t.getMessage() + ", Session ID: " + sessionId + ", Student: " + studentUsername, t);
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
        new android.os.Handler().postDelayed(() -> {
            isScanning = true;
            updateStatus("Ready to scan", R.color.success_green);
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
        super.onDestroy();
        if (barcodeScanner != null) {
            barcodeScanner.close();
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }
}


