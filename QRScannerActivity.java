package com.yourpackage.yourapp.activities;

import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import com.yourpackage.yourapp.utils.QRUrlParser;
import com.yourpackage.yourapp.Config;

public class QRScannerActivity extends AppCompatActivity {
    
    private static final String TAG = "QRScannerActivity";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_scanner);
        
        // Initialize your QR scanner here
        setupQRScanner();
    }
    
    private void setupQRScanner() {
        // Your existing QR scanner setup code
        // This is where you'd initialize your QR code scanner library
        // (e.g., ZXing, ML Kit, etc.)
        
        Log.d(TAG, "QR Scanner initialized");
    }
    
    /**
     * Handle the scanned QR code result
     * This method should be called when your QR scanner detects a code
     */
    public void handleQRResult(String scannedText) {
        Log.d(TAG, "Raw QR scanned: " + scannedText);
        
        // Get your app's base URL from Config
        String baseUrl = Config.getServerUrl();
        
        // Parse and rebuild the URL using your app's base URL
        String correctedUrl = QRUrlParser.parseAndRebuildUrl(scannedText, baseUrl);
        
        Log.d(TAG, "Corrected URL: " + correctedUrl);
        
        // Extract session ID for additional processing if needed
        String sessionId = QRUrlParser.extractSessionId(scannedText);
        Log.d(TAG, "Extracted session ID: " + sessionId);
        
        // Now use the corrected URL for your API call
        makeApiCall(correctedUrl, sessionId);
    }
    
    private void makeApiCall(String url, String sessionId) {
        // Your existing API call logic using the corrected URL
        // This will now always use your app's configured base URL
        // regardless of what was in the QR code
        
        Log.d(TAG, "Making API call to: " + url);
        Log.d(TAG, "Session ID: " + sessionId);
        
        // Example API call using Retrofit or your preferred HTTP client
        // ApiService.getApiInterface().getSession(sessionId)
        //     .enqueue(new Callback<Session>() {
        //         @Override
        //         public void onResponse(Call<Session> call, Response<Session> response) {
        //             if (response.isSuccessful()) {
        //                 // Handle successful response
        //                 Log.d(TAG, "API call successful");
        //             } else {
        //                 // Handle error response
        //                 Log.e(TAG, "API call failed: " + response.code());
        //             }
        //         }
        //         
        //         @Override
        //         public void onFailure(Call<Session> call, Throwable t) {
        //             // Handle network error
        //             Log.e(TAG, "API call failed: " + t.getMessage());
        //         }
        //     });
    }
    
    /**
     * Test the QR URL parser with various inputs
     */
    private void testQRUrlParser() {
        Log.d(TAG, "Testing QR URL Parser...");
        QRUrlParser.testUrlParsing();
    }
}
