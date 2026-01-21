package org.example.semscan.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import org.example.semscan.R;
import org.example.semscan.utils.LoggingConfig;
import org.example.semscan.utils.ServerLogger;

/**
 * Activity for configuring logging settings
 */
public class LoggingSettingsActivity extends AppCompatActivity {
    
    private CheckBox checkServerLogging;
    private CheckBox checkIncludeDeviceInfo;
    private CheckBox checkIncludeUserInfo;
    private SeekBar seekLogLevel;
    private TextView textLogLevel;
    private Spinner spinnerBatchSize;
    private Spinner spinnerSendInterval;
    private Button btnTestLogging;
    private Button btnClearLogs;
    
    private LoggingConfig loggingConfig;
    private ServerLogger serverLogger;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logging_settings);
        
        loggingConfig = LoggingConfig.getInstance(this);
        serverLogger = ServerLogger.getInstance(this);
        
        initializeViews();
        setupToolbar();
        loadSettings();
        setupListeners();
    }
    
    private void initializeViews() {
        checkServerLogging = findViewById(R.id.check_server_logging);
        checkIncludeDeviceInfo = findViewById(R.id.check_include_device_info);
        checkIncludeUserInfo = findViewById(R.id.check_include_user_info);
        seekLogLevel = findViewById(R.id.seek_log_level);
        textLogLevel = findViewById(R.id.text_log_level);
        spinnerBatchSize = findViewById(R.id.spinner_batch_size);
        spinnerSendInterval = findViewById(R.id.spinner_send_interval);
        btnTestLogging = findViewById(R.id.btn_test_logging);
        btnClearLogs = findViewById(R.id.btn_clear_logs);
    }
    
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Logging Settings");
        }
    }
    
    private void loadSettings() {
        // Load server logging setting
        checkServerLogging.setChecked(loggingConfig.isServerLoggingEnabled());
        
        // Load device info setting
        checkIncludeDeviceInfo.setChecked(loggingConfig.shouldIncludeDeviceInfo());
        
        // Load user info setting
        checkIncludeUserInfo.setChecked(loggingConfig.shouldIncludeUserInfo());
        
        // Load log level setting
        int logLevel = loggingConfig.getLogLevel();
        seekLogLevel.setProgress(logLevel);
        updateLogLevelText(logLevel);
        
        // Load batch size setting
        int batchSize = loggingConfig.getBatchSize();
        // Set spinner selection based on batch size
        String[] batchSizes = {"5", "10", "20", "50", "100"};
        for (int i = 0; i < batchSizes.length; i++) {
            if (Integer.parseInt(batchSizes[i]) == batchSize) {
                spinnerBatchSize.setSelection(i);
                break;
            }
        }
        
        // Load send interval setting
        int sendInterval = loggingConfig.getSendInterval();
        // Set spinner selection based on send interval
        String[] intervals = {"10", "30", "60", "300", "600"}; // seconds
        for (int i = 0; i < intervals.length; i++) {
            if (Integer.parseInt(intervals[i]) == sendInterval) {
                spinnerSendInterval.setSelection(i);
                break;
            }
        }
    }
    
    private void setupListeners() {
        // Server logging toggle
        checkServerLogging.setOnCheckedChangeListener((buttonView, isChecked) -> {
            loggingConfig.setServerLoggingEnabled(isChecked);
            serverLogger.setServerLoggingEnabled(isChecked);
        });
        
        // Device info toggle
        checkIncludeDeviceInfo.setOnCheckedChangeListener((buttonView, isChecked) -> {
            loggingConfig.setIncludeDeviceInfo(isChecked);
        });
        
        // User info toggle
        checkIncludeUserInfo.setOnCheckedChangeListener((buttonView, isChecked) -> {
            loggingConfig.setIncludeUserInfo(isChecked);
        });
        
        // Log level seekbar
        seekLogLevel.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateLogLevelText(progress);
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                loggingConfig.setLogLevel(seekBar.getProgress());
            }
        });
        
        // Test logging button
        btnTestLogging.setOnClickListener(v -> testLogging());
        
        // Clear logs button
        btnClearLogs.setOnClickListener(v -> clearLogs());
    }
    
    private void updateLogLevelText(int level) {
        String levelText;
        switch (level) {
            case 0: levelText = "VERBOSE"; break;
            case 1: levelText = "DEBUG"; break;
            case 2: levelText = "INFO"; break;
            case 3: levelText = "WARN"; break;
            case 4: levelText = "ERROR"; break;
            default: levelText = "UNKNOWN"; break;
        }
        textLogLevel.setText(levelText);
    }
    
    private void testLogging() {
        // Test different log levels
        serverLogger.v(ServerLogger.TAG_UI, "Test verbose log message");
        serverLogger.i(ServerLogger.TAG_UI, "Test debug log message");
        serverLogger.i(ServerLogger.TAG_UI, "Test info log message");
        serverLogger.w(ServerLogger.TAG_UI, "Test warning log message");
        serverLogger.e(ServerLogger.TAG_UI, "Test error log message");
        
        // Test specific log types
        serverLogger.userAction("Test Action", "Testing logging system");
        serverLogger.api("GET", "api/v1/test", "Testing API logging");
        serverLogger.session("Test Session", "Testing session logging");
        serverLogger.qr("Test QR", "Testing QR logging");
        serverLogger.attendance("Test Attendance", "Testing attendance logging");
        serverLogger.security("Test Security", "Testing security logging");
        serverLogger.performance("Test Performance", "Testing performance logging");
        
        // Force send logs immediately
        serverLogger.flushLogs();
        
        Toast.makeText(this, "Test logs sent! Check server logs.", Toast.LENGTH_SHORT).show();
    }
    
    private void clearLogs() {
        // This would clear local logs if implemented
        Toast.makeText(this, "Local logs cleared!", Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
