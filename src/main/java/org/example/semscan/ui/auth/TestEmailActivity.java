package org.example.semscan.ui.auth;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import org.example.semscan.R;
import org.example.semscan.data.api.ApiClient;
import org.example.semscan.data.api.ApiService;
import org.example.semscan.utils.ConfigManager;
import org.example.semscan.utils.Logger;
import org.example.semscan.utils.PreferencesManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TestEmailActivity extends AppCompatActivity {

    private MaterialButton btnSendTestEmail;
    private MaterialButton btnCancel;
    private ProgressBar progressTestEmail;
    private PreferencesManager preferencesManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_email);

        preferencesManager = PreferencesManager.getInstance(this);

        btnSendTestEmail = findViewById(R.id.btn_send_test_email);
        btnCancel = findViewById(R.id.btn_cancel);
        progressTestEmail = findViewById(R.id.progress_test_email);

        btnSendTestEmail.setOnClickListener(v -> sendTestEmail());
        btnCancel.setOnClickListener(v -> finish());
    }

    private void sendTestEmail() {
        // Use test email address from ConfigManager
        String userEmail = ConfigManager.getInstance(this).getTestEmailRecipient();

        // Disable button and show progress
        btnSendTestEmail.setEnabled(false);
        progressTestEmail.setVisibility(View.VISIBLE);
        btnSendTestEmail.setText(getString(R.string.test_email_sending));

        Logger.i(Logger.TAG_UI, "Sending test email to: " + userEmail);

        // Build HTML email content
        String htmlContent = buildTestEmailHtml();
        String subject = "SemScan - Test Email";
        
        // Call API to send test email
        ApiService apiService = ApiClient.getInstance(this).getApiService();
        ApiService.TestEmailRequest request = new ApiService.TestEmailRequest(
            userEmail, 
            subject, 
            htmlContent
        );
        
        apiService.sendTestEmail(request).enqueue(new Callback<ApiService.TestEmailResponse>() {
            @Override
            public void onResponse(Call<ApiService.TestEmailResponse> call, Response<ApiService.TestEmailResponse> response) {
                btnSendTestEmail.setEnabled(true);
                progressTestEmail.setVisibility(View.GONE);
                btnSendTestEmail.setText(getString(R.string.ok));

                if (response.isSuccessful() && response.body() != null) {
                    ApiService.TestEmailResponse emailResponse = response.body();
                    if (emailResponse.success) {
                        Logger.i(Logger.TAG_API, "Test email sent successfully to: " + userEmail);
                        Toast.makeText(TestEmailActivity.this, R.string.test_email_success, Toast.LENGTH_LONG).show();
                        // Close activity after a short delay
                        btnSendTestEmail.postDelayed(() -> finish(), 1500);
                    } else {
                        String errorMsg = emailResponse.message != null ? emailResponse.message : "Failed to send email";
                        Logger.e(Logger.TAG_API, "Test email failed: " + errorMsg);
                        Toast.makeText(TestEmailActivity.this, 
                                getString(R.string.test_email_error, errorMsg), Toast.LENGTH_LONG).show();
                    }
                } else {
                    String errorMsg = "Server error: " + response.code();
                    try {
                        if (response.errorBody() != null) {
                            String errorBody = response.errorBody().string();
                            if (errorBody.length() > 0) {
                                errorMsg = errorBody.length() > 100 ? errorBody.substring(0, 100) : errorBody;
                            }
                        }
                    } catch (Exception e) {
                        // Ignore
                    }
                    Logger.e(Logger.TAG_API, "Test email API error, code=" + response.code() + ", message=" + errorMsg);
                    Toast.makeText(TestEmailActivity.this, 
                            getString(R.string.test_email_error, errorMsg), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ApiService.TestEmailResponse> call, Throwable t) {
                btnSendTestEmail.setEnabled(true);
                progressTestEmail.setVisibility(View.GONE);
                btnSendTestEmail.setText(getString(R.string.ok));
                
                Logger.e(Logger.TAG_API, "Test email network error", t);
                String errorMsg = getString(R.string.error_network_connection);
                if (t instanceof java.net.SocketTimeoutException || t instanceof java.net.ConnectException) {
                    errorMsg = getString(R.string.error_network_timeout);
                } else if (t instanceof java.net.UnknownHostException) {
                    errorMsg = getString(R.string.error_server_unavailable);
                }
                Toast.makeText(TestEmailActivity.this, 
                        getString(R.string.test_email_error, errorMsg), Toast.LENGTH_LONG).show();
            }
        });
    }
    
    /**
     * Build HTML content for test email
     */
    private String buildTestEmailHtml() {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<meta charset=\"UTF-8\">" +
                "<style>" +
                "body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }" +
                ".container { max-width: 600px; margin: 0 auto; padding: 20px; }" +
                ".header { background-color: #2196F3; color: white; padding: 20px; text-align: center; }" +
                ".content { padding: 20px; background-color: #f9f9f9; }" +
                ".footer { padding: 20px; text-align: center; color: #666; font-size: 12px; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class=\"container\">" +
                "<div class=\"header\">" +
                "<h1>SemScan Test Email</h1>" +
                "</div>" +
                "<div class=\"content\">" +
                "<p>Hello,</p>" +
                "<p>This is a test email from the SemScan Attendance System.</p>" +
                "<p>If you received this email, it means that Jakarta Mail (Spring Boot Mail) is configured correctly and working properly.</p>" +
                "<p><strong>Email Details:</strong></p>" +
                "<ul>" +
                "<li>Sent via: Jakarta Mail API</li>" +
                "<li>SMTP: Configured and working</li>" +
                "<li>Timestamp: " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date()) + "</li>" +
                "</ul>" +
                "<p>You can now use this email service for supervisor notifications and other email features.</p>" +
                "</div>" +
                "<div class=\"footer\">" +
                "<p>This is an automated message from SemScan System.</p>" +
                "<p>Please do not reply to this email.</p>" +
                "</div>" +
                "</div>" +
                "</body>" +
                "</html>";
    }
}

