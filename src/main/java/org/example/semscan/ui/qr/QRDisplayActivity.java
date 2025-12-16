package org.example.semscan.ui.qr;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.example.semscan.R;
import org.example.semscan.data.api.ApiClient;
import org.example.semscan.data.api.ApiService;
import org.example.semscan.data.model.Attendance;
import org.example.semscan.data.model.Session;
import org.example.semscan.ui.teacher.ExportActivity;
import org.example.semscan.utils.Logger;
import org.example.semscan.utils.PreferencesManager;
import org.example.semscan.utils.QRUtils;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class QRDisplayActivity extends AppCompatActivity {
    
    private TextView textSessionInfo;
    private ImageView imageQRCode;
    private TextView textStatus;
    private TextView textPresentCount;
    private Button btnCancelSession;
    private Button btnEndSession;
    
    private PreferencesManager preferencesManager;
    private ApiService apiService;
    
    private Session currentSession;
    private Timer attendanceUpdateTimer;
    private boolean isPollingActive = false;
    private static final long POLLING_INTERVAL = 30000; // 30 seconds instead of 5
    private int presentCount = 0;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_display);
        
        Logger.i(Logger.TAG_QR, "QRDisplayActivity created");
        
        preferencesManager = PreferencesManager.getInstance(this);
        apiService = ApiClient.getInstance(this).getApiService();
        
        // Get session from intent
        long sessionId = getIntent().getLongExtra("sessionId", -1L);
        long seminarId = getIntent().getLongExtra("seminarId", -1L);
        long startTime = getIntent().getLongExtra("startTime", 0);
        long endTime = getIntent().getLongExtra("endTime", 0);
        String status = getIntent().getStringExtra("status");
        
        Logger.qr("Session Data Received", "Session ID: " + sessionId + ", Seminar ID: " + seminarId + ", Status: " + status);
        
        if (sessionId <= 0 || seminarId <= 0) {
            Logger.e(Logger.TAG_QR, "No session data provided in intent");
            Toast.makeText(this, "No session data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        currentSession = new Session(sessionId, seminarId, startTime, endTime > 0 ? endTime : null, status);
        Logger.qr("Session Created", "Session object created for ID: " + sessionId);
        
        initializeViews();
        setupToolbar();
        setupClickListeners();
        generateAndDisplayQR();
        startAttendanceUpdates();
    }
    
    private void initializeViews() {
        textSessionInfo = findViewById(R.id.text_session_info);
        imageQRCode = findViewById(R.id.image_qr_code);
        textStatus = findViewById(R.id.text_status);
        textPresentCount = findViewById(R.id.text_present_count);
        btnCancelSession = findViewById(R.id.btn_cancel_session);
        btnEndSession = findViewById(R.id.btn_end_session);
    }
    
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }
    
    private void setupClickListeners() {
        btnCancelSession.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCancelSessionDialog();
            }
        });
        
        btnEndSession.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEndSessionDialog();
            }
        });
    }
    
    private void generateAndDisplayQR() {
        // Generate QR content
        String qrContent = QRUtils.generateQRContent(currentSession.getSessionId());
        Logger.qr("QR Code Generated", "Content: " + qrContent);
        
        // Generate QR code bitmap
        try {
            Bitmap qrBitmap = generateQRCode(qrContent, 500, 500);
            imageQRCode.setImageBitmap(qrBitmap);
            
            // Update session info
        textSessionInfo.setText("Session: " + currentSession.getSessionId());
            
            Logger.qr("QR Display Updated", "QR code displayed for session: " + currentSession.getSessionId());
            
        } catch (Exception e) {
            Logger.e(Logger.TAG_QR, "Failed to generate QR code", e);
            Toast.makeText(this, "Failed to generate QR code", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    
    private void startAttendanceUpdates() {
        if (isPollingActive) {
            return; // Already polling
        }
        
        isPollingActive = true;
        Logger.i(Logger.TAG_QR, "Starting smart attendance polling every " + (POLLING_INTERVAL/1000) + " seconds");
        
        // Update attendance count every 30 seconds (reduced from 5 seconds)
        attendanceUpdateTimer = new Timer();
        attendanceUpdateTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (isPollingActive && shouldContinuePolling()) {
                    updateAttendanceCount();
                }
            }
        }, 0, POLLING_INTERVAL); // Update every 30 seconds
    }
    
    private boolean shouldContinuePolling() {
        // Only poll if the activity is still active and visible
        return !isFinishing() && !isDestroyed();
    }
    
    private void updateAttendanceCount() {
        // Don't make API calls if polling is not active
        if (!isPollingActive) {
            Logger.i(Logger.TAG_QR, "Skipping attendance update - polling not active");
            return;
        }
        
        // API key no longer required - removed authentication
        
        Logger.api("GET", "api/v1/attendance", "Session ID: " + currentSession.getSessionId());
        
        Call<List<Attendance>> call = apiService.getAttendance(currentSession.getSessionId());
        call.enqueue(new Callback<List<Attendance>>() {
            @Override
            public void onResponse(Call<List<Attendance>> call, Response<List<Attendance>> response) {
                // Check if polling is still active before processing response
                if (!isPollingActive) {
                    Logger.i(Logger.TAG_QR, "Ignoring attendance response - polling stopped");
                    return;
                }
                
                if (response.isSuccessful() && response.body() != null) {
                    int newCount = response.body().size();
                    if (newCount != presentCount) {
                        Logger.attendance("Attendance Count Updated", "Session: " + currentSession.getSessionId() + ", Count: " + newCount);
                        presentCount = newCount;
                        
                        // Update UI on main thread
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                if (!isFinishing() && !isDestroyed()) {
                                    textPresentCount.setText(getString(R.string.present_count, presentCount));
                                }
                            }
                        });
                    } else {
                        Logger.i(Logger.TAG_QR, "Attendance count unchanged: " + newCount);
                    }
                } else {
                    Logger.w(Logger.TAG_QR, "Failed to get attendance count - Response code: " + response.code());
                }
            }
            
            @Override
            public void onFailure(Call<List<Attendance>> call, Throwable t) {
                if (isPollingActive) {
                    Logger.e(Logger.TAG_QR, "Attendance count update failed", t);
                }
            }
        });
    }
    
    private void openExport() {
        Intent intent = new Intent(this, ExportActivity.class);
        intent.putExtra("sessionId", currentSession.getSessionId());
        startActivity(intent);
        finish();
    }
    
    private void showCancelSessionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("❌ Cancel Session")
                .setMessage("Are you sure you want to cancel this session? This will immediately end the session and students will no longer be able to scan the QR code.")
                .setPositiveButton("Yes, Cancel Session", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        cancelSession();
                    }
                })
                .setNegativeButton("Keep Session", null)
                .show();
    }
    
    private void showEndSessionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("End Session")
                .setMessage("Are you sure you want to end this session? Students will no longer be able to scan the QR code.")
                .setPositiveButton("End Session", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        endSession();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    private void endSession() {
        Logger.userAction("End Session", "Presenter clicked end session for: " + currentSession.getSessionId());
        
        // API key no longer required - removed authentication
        
        Logger.api("PATCH", "api/v1/sessions/" + currentSession.getSessionId() + "/close", null);
        
        Call<Session> call = apiService.closeSession(currentSession.getSessionId());
        call.enqueue(new Callback<Session>() {
            @Override
            public void onResponse(Call<Session> call, Response<Session> response) {
                if (response.isSuccessful()) {
                    Logger.session("Session Ended", "Session ID: " + currentSession.getSessionId());
                    Logger.apiResponse("PATCH", "api/v1/sessions/" + currentSession.getSessionId() + "/close", response.code(), "Session closed successfully");
                    
                    Toast.makeText(QRDisplayActivity.this, "Session ended successfully", Toast.LENGTH_SHORT).show();
                    // Navigate to export after session ends
                    openExport();
                } else {
                    Logger.apiError("PATCH", "api/v1/sessions/" + currentSession.getSessionId() + "/close", response.code(), "Failed to close session");
                    Toast.makeText(QRDisplayActivity.this, "Failed to end session", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<Session> call, Throwable t) {
                Logger.e(Logger.TAG_QR, "Failed to end session", t);
                String errorMessage = getString(R.string.error_operation_failed);
                if (t instanceof java.net.SocketTimeoutException || t instanceof java.net.ConnectException) {
                    errorMessage = getString(R.string.error_network_timeout);
                } else if (t instanceof java.net.UnknownHostException) {
                    errorMessage = getString(R.string.error_server_unavailable);
                }
                Toast.makeText(QRDisplayActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void cancelSession() {
        Logger.userAction("Cancel Session", "Presenter clicked cancel session for: " + currentSession.getSessionId());
        
        // API key no longer required - removed authentication
        
        Logger.api("PATCH", "api/v1/sessions/" + currentSession.getSessionId() + "/close", null);
        
        Call<Session> call = apiService.closeSession(currentSession.getSessionId());
        call.enqueue(new Callback<Session>() {
            @Override
            public void onResponse(Call<Session> call, Response<Session> response) {
                if (response.isSuccessful()) {
                    Logger.session("Session Cancelled", "Session ID: " + currentSession.getSessionId());
                    Logger.apiResponse("PATCH", "api/v1/sessions/" + currentSession.getSessionId() + "/close", response.code(), "Session cancelled successfully");
                    
                    Toast.makeText(QRDisplayActivity.this, "Session cancelled successfully", Toast.LENGTH_SHORT).show();
                    // Return to session creation screen instead of export
                    finish();
                } else {
                    Logger.apiError("PATCH", "api/v1/sessions/" + currentSession.getSessionId() + "/close", response.code(), "Failed to cancel session");
                    Toast.makeText(QRDisplayActivity.this, "Failed to cancel session", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<Session> call, Throwable t) {
                Logger.e(Logger.TAG_QR, "Failed to cancel session", t);
                String errorMessage = getString(R.string.error_operation_failed);
                if (t instanceof java.net.SocketTimeoutException || t instanceof java.net.ConnectException) {
                    errorMessage = getString(R.string.error_network_timeout);
                } else if (t instanceof java.net.UnknownHostException) {
                    errorMessage = getString(R.string.error_server_unavailable);
                }
                Toast.makeText(QRDisplayActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        stopAttendanceUpdates();
        Logger.i(Logger.TAG_QR, "Activity paused - stopping attendance polling");
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (currentSession != null) {
            startAttendanceUpdates();
            Logger.i(Logger.TAG_QR, "Activity resumed - starting attendance polling");
        }
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        stopAttendanceUpdates();
        Logger.i(Logger.TAG_QR, "Activity stopped - stopping attendance polling");
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAttendanceUpdates();
        Logger.i(Logger.TAG_QR, "Activity destroyed - stopping attendance polling");
    }
    
    private void stopAttendanceUpdates() {
        isPollingActive = false;
        if (attendanceUpdateTimer != null) {
            attendanceUpdateTimer.cancel();
            attendanceUpdateTimer = null;
        }
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        showEndSessionDialog();
        return true;
    }
    
    @Override
    public void onBackPressed() {
        showEndSessionDialog();
    }
    
    private Bitmap generateQRCode(String content, int width, int height) throws WriterException {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height);
        
        int w = bitMatrix.getWidth();
        int h = bitMatrix.getHeight();
        int[] pixels = new int[w * h];
        
        for (int y = 0; y < h; y++) {
            int offset = y * w;
            for (int x = 0; x < w; x++) {
                pixels[offset + x] = bitMatrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
            }
        }
        
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
        return bitmap;
    }
}
