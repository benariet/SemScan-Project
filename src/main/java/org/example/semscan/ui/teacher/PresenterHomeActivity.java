package org.example.semscan.ui.teacher;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.example.semscan.ui.auth.LoginActivity;
import org.example.semscan.R;
import org.example.semscan.data.api.ApiClient;
import org.example.semscan.data.api.ApiService;
import org.example.semscan.ui.RolePickerActivity;
import org.example.semscan.ui.SettingsActivity;
import org.example.semscan.utils.Logger;
import org.example.semscan.utils.PreferencesManager;
import org.example.semscan.utils.ServerLogger;

import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PresenterHomeActivity extends AppCompatActivity {

    private static final int REQUEST_NOTIFICATION_PERMISSION = 1001;

    private CardView cardStartSession;
    private MaterialCardView cardEnrollSlot;
    private CardView cardMySlot;
    private CardView cardChangeRole;
    private LinearLayout cardStartSessionInner;
    private LinearLayout cardMySlotInner;
    private LinearLayout cardEnrollSlotInner;
    private PreferencesManager preferencesManager;
    private ApiService apiService;
    private ServerLogger serverLogger;
    private boolean hasRegisteredSlot = false;
    private boolean canOpenSession = false;

    private MaterialCardView cardPresentationDetails;
    private LinearLayout presentationDetailsHeader;
    private LinearLayout presentationDetailsContent;
    private ImageView iconExpand;
    private TextView textPresentationStatus;
    private TextInputLayout inputLayoutTopic;
    private TextInputLayout inputLayoutAbstract;
    private TextInputLayout inputLayoutSupervisorName;
    private TextInputLayout inputLayoutSupervisorEmail;
    private TextInputEditText editTopic;
    private TextInputEditText editAbstract;
    private TextInputEditText editSupervisorName;
    private TextInputEditText editSupervisorEmail;
    private MaterialButton btnSavePresentationDetails;
    private boolean isPresentationDetailsExpanded = false;
    private boolean hasPresentationDetails = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_presenter_home);
        preferencesManager = PreferencesManager.getInstance(this);
        apiService = ApiClient.getInstance(this).getApiService();
        serverLogger = ServerLogger.getInstance(this);
        String username = preferencesManager.getUserName();
        String userRole = preferencesManager.getUserRole();
        if (serverLogger != null) serverLogger.updateUserContext(username, userRole);
        if (!preferencesManager.isPresenter()) { navigateToRolePicker(); return; }
        requestNotificationPermission();
        initializeViews();
        setupToolbar();
        setupPresentationDetailsCard();
        setupClickListeners();
        loadPresentationDetails();
        checkRegistrationStatus();
        Logger.i(Logger.TAG_SCREEN_VIEW, "PresenterHomeActivity opened");
        if (serverLogger != null) serverLogger.i(ServerLogger.TAG_SCREEN_VIEW, "PresenterHomeActivity opened");
    }

    private void initializeViews() {
        cardStartSession = findViewById(R.id.card_start_session);
        cardEnrollSlot = findViewById(R.id.card_enroll_slot);
        cardMySlot = findViewById(R.id.card_my_slot);
        cardChangeRole = findViewById(R.id.card_change_role);
        cardStartSessionInner = findViewById(R.id.card_start_session_inner);
        cardMySlotInner = findViewById(R.id.card_my_slot_inner);
        if (cardEnrollSlot != null) {
            for (int i = 0; i < cardEnrollSlot.getChildCount(); i++) {
                View child = cardEnrollSlot.getChildAt(i);
                if (child instanceof LinearLayout) { cardEnrollSlotInner = (LinearLayout) child; break; }
            }
        }
        cardPresentationDetails = findViewById(R.id.card_presentation_details);
        presentationDetailsHeader = findViewById(R.id.presentation_details_header);
        presentationDetailsContent = findViewById(R.id.presentation_details_content);
        iconExpand = findViewById(R.id.icon_expand);
        textPresentationStatus = findViewById(R.id.text_presentation_status);
        inputLayoutTopic = findViewById(R.id.input_layout_topic);
        inputLayoutAbstract = findViewById(R.id.input_layout_abstract);
        inputLayoutSupervisorName = findViewById(R.id.input_layout_supervisor_name);
        inputLayoutSupervisorEmail = findViewById(R.id.input_layout_supervisor_email);
        editTopic = findViewById(R.id.edit_topic);
        editAbstract = findViewById(R.id.edit_abstract);
        editSupervisorName = findViewById(R.id.edit_supervisor_name);
        editSupervisorEmail = findViewById(R.id.edit_supervisor_email);
        btnSavePresentationDetails = findViewById(R.id.btn_save_presentation_details);
        setCardsEnabled(false);
        updateEnrollSlotCardState();
    }

    private void setupPresentationDetailsCard() {
        if (presentationDetailsHeader != null) presentationDetailsHeader.setOnClickListener(v -> togglePresentationDetails());
        if (btnSavePresentationDetails != null) btnSavePresentationDetails.setOnClickListener(v -> savePresentationDetails());
        if (editTopic != null && inputLayoutTopic != null) addTextWatcherToClearError(editTopic, inputLayoutTopic);
        if (editAbstract != null && inputLayoutAbstract != null) addTextWatcherToClearError(editAbstract, inputLayoutAbstract);
        if (editSupervisorName != null && inputLayoutSupervisorName != null) addTextWatcherToClearError(editSupervisorName, inputLayoutSupervisorName);
        if (editSupervisorEmail != null && inputLayoutSupervisorEmail != null) addTextWatcherToClearError(editSupervisorEmail, inputLayoutSupervisorEmail);
    }

    private void addTextWatcherToClearError(TextInputEditText editText, TextInputLayout layout) {
        editText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { layout.setError(null); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void togglePresentationDetails() {
        isPresentationDetailsExpanded = !isPresentationDetailsExpanded;
        if (iconExpand != null) {
            float fromDegrees = isPresentationDetailsExpanded ? 0f : 180f;
            float toDegrees = isPresentationDetailsExpanded ? 180f : 0f;
            RotateAnimation rotate = new RotateAnimation(fromDegrees, toDegrees, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            rotate.setDuration(200);
            rotate.setFillAfter(true);
            iconExpand.startAnimation(rotate);
        }
        if (presentationDetailsContent != null) presentationDetailsContent.setVisibility(isPresentationDetailsExpanded ? View.VISIBLE : View.GONE);
        Logger.i(Logger.TAG_NAVIGATION, "Toggle Presentation Details: expanded=" + isPresentationDetailsExpanded);
    }

    private void loadPresentationDetails() {
        String topic = preferencesManager.getPresentationTopic();
        String seminarAbstract = preferencesManager.getSeminarAbstract();
        String supervisorName = preferencesManager.getSupervisorName();
        String supervisorEmail = preferencesManager.getSupervisorEmail();
        if (!TextUtils.isEmpty(topic) && editTopic != null) editTopic.setText(topic);
        if (!TextUtils.isEmpty(seminarAbstract) && editAbstract != null) editAbstract.setText(seminarAbstract);
        if (!TextUtils.isEmpty(supervisorName) && editSupervisorName != null) editSupervisorName.setText(supervisorName);
        if (!TextUtils.isEmpty(supervisorEmail) && editSupervisorEmail != null) editSupervisorEmail.setText(supervisorEmail);
        updatePresentationDetailsStatus();
    }

    private void savePresentationDetails() {
        if (inputLayoutTopic != null) inputLayoutTopic.setError(null);
        if (inputLayoutAbstract != null) inputLayoutAbstract.setError(null);
        if (inputLayoutSupervisorName != null) inputLayoutSupervisorName.setError(null);
        if (inputLayoutSupervisorEmail != null) inputLayoutSupervisorEmail.setError(null);
        String topic = editTopic != null && editTopic.getText() != null ? editTopic.getText().toString().trim() : "";
        String seminarAbstract = editAbstract != null && editAbstract.getText() != null ? editAbstract.getText().toString().trim() : "";
        String supervisorName = editSupervisorName != null && editSupervisorName.getText() != null ? editSupervisorName.getText().toString().trim() : "";
        String supervisorEmail = editSupervisorEmail != null && editSupervisorEmail.getText() != null ? editSupervisorEmail.getText().toString().trim() : "";
        boolean valid = true;
        if (TextUtils.isEmpty(topic)) { if (inputLayoutTopic != null) inputLayoutTopic.setError(getString(R.string.presentation_details_topic_required)); valid = false; }
        if (TextUtils.isEmpty(seminarAbstract)) { if (inputLayoutAbstract != null) inputLayoutAbstract.setError(getString(R.string.presentation_details_abstract_required)); valid = false; }
        if (TextUtils.isEmpty(supervisorName)) { if (inputLayoutSupervisorName != null) inputLayoutSupervisorName.setError(getString(R.string.presentation_details_supervisor_name_required)); valid = false; }
        if (TextUtils.isEmpty(supervisorEmail)) { if (inputLayoutSupervisorEmail != null) inputLayoutSupervisorEmail.setError(getString(R.string.presentation_details_supervisor_email_required)); valid = false; }
        else if (!Patterns.EMAIL_ADDRESS.matcher(supervisorEmail).matches()) { if (inputLayoutSupervisorEmail != null) inputLayoutSupervisorEmail.setError(getString(R.string.presentation_details_supervisor_email_invalid)); valid = false; }
        if (!valid) return;
        preferencesManager.setPresentationTopic(topic);
        preferencesManager.setSeminarAbstract(seminarAbstract);
        preferencesManager.setSupervisorName(supervisorName);
        preferencesManager.setSupervisorEmail(supervisorEmail);
        Toast.makeText(this, R.string.presentation_details_saved, Toast.LENGTH_SHORT).show();
        updatePresentationDetailsStatus();
        if (isPresentationDetailsExpanded) togglePresentationDetails();
        Logger.i(Logger.TAG_SETTINGS_CHANGE, "Presentation details saved: topic=" + topic);
        if (serverLogger != null) serverLogger.i(ServerLogger.TAG_SETTINGS_CHANGE, "Presentation details saved");
    }

    private void updatePresentationDetailsStatus() {
        String topic = preferencesManager.getPresentationTopic();
        String seminarAbstract = preferencesManager.getSeminarAbstract();
        String supervisorName = preferencesManager.getSupervisorName();
        String supervisorEmail = preferencesManager.getSupervisorEmail();
        hasPresentationDetails = !TextUtils.isEmpty(topic) && !TextUtils.isEmpty(seminarAbstract) && !TextUtils.isEmpty(supervisorName) && !TextUtils.isEmpty(supervisorEmail) && Patterns.EMAIL_ADDRESS.matcher(supervisorEmail).matches();
        if (textPresentationStatus != null) textPresentationStatus.setText(hasPresentationDetails ? R.string.presentation_details_status_complete : R.string.presentation_details_status_incomplete);
        updateEnrollSlotCardState();
    }

    private void updateEnrollSlotCardState() {
        if (cardEnrollSlot == null) return;
        if (hasPresentationDetails) {
            cardEnrollSlot.setClickable(true); cardEnrollSlot.setFocusable(true); cardEnrollSlot.setAlpha(1.0f);
            if (cardEnrollSlotInner != null) cardEnrollSlotInner.setBackgroundResource(R.drawable.bg_card_orange_light_gradient);
        } else {
            cardEnrollSlot.setClickable(true); cardEnrollSlot.setFocusable(true); cardEnrollSlot.setAlpha(0.6f);
            if (cardEnrollSlotInner != null) cardEnrollSlotInner.setBackgroundResource(R.drawable.bg_card_disabled);
        }
    }

    private void checkRegistrationStatus() {
        final String username = preferencesManager.getUserName();
        if (username == null || username.trim().isEmpty()) { Logger.w(Logger.TAG_PREFERENCES, "No username cached"); return; }
        final String normalizedUsername = username.trim().toLowerCase(Locale.US);
        apiService.getPresenterHome(normalizedUsername).enqueue(new Callback<ApiService.PresenterHomeResponse>() {
            @Override public void onResponse(Call<ApiService.PresenterHomeResponse> call, Response<ApiService.PresenterHomeResponse> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().presenter == null) return;
                ApiService.PresenterHomeResponse body = response.body();
                hasRegisteredSlot = body.mySlot != null && body.mySlot.slotId != null;
                // Only enable Start Session card if registration is APPROVED (canOpen=true)
                canOpenSession = hasRegisteredSlot && body.attendance != null && body.attendance.canOpen;
                setStartSessionEnabled(canOpenSession);
                setMySlotEnabled(hasRegisteredSlot);
            }
            @Override public void onFailure(Call<ApiService.PresenterHomeResponse> call, Throwable t) { Logger.e(Logger.TAG_SLOTS_LOAD, "Failed to check registration status", t); }
        });
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        toolbar.setNavigationIcon(R.drawable.ic_menu);
        toolbar.setNavigationOnClickListener(v -> showHamburgerMenu(toolbar));
    }

    private void showHamburgerMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_hamburger, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_settings) { openSettings(); return true; }
            else if (id == R.id.action_logout) { showLogoutDialog(); return true; }
            return false;
        });
        popup.show();
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(this).setTitle(R.string.logout_confirm_title).setMessage(R.string.logout_confirm_message)
            .setPositiveButton(android.R.string.ok, (d, w) -> performLogout()).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void performLogout() {
        preferencesManager.clearUserData();
        // Note: Do NOT clear saved credentials here - "Remember Me" should persist after logout
        Toast.makeText(this, R.string.logout_success, Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setupClickListeners() {
        cardStartSession.setOnClickListener(v -> {
            if (!hasRegisteredSlot) { Toast.makeText(this, R.string.presenter_select_slot_first, Toast.LENGTH_SHORT).show(); return; }
            if (!canOpenSession) { Toast.makeText(this, R.string.presenter_waiting_approval, Toast.LENGTH_SHORT).show(); return; }
            openStartSession();
        });
        cardEnrollSlot.setOnClickListener(v -> {
            if (!hasPresentationDetails) {
                Toast.makeText(this, R.string.presentation_details_required, Toast.LENGTH_LONG).show();
                if (!isPresentationDetailsExpanded) togglePresentationDetails();
                return;
            }
            openSlotSelection(false);
        });
        cardMySlot.setOnClickListener(v -> {
            if (!hasRegisteredSlot) { Toast.makeText(this, R.string.presenter_select_slot_first, Toast.LENGTH_SHORT).show(); return; }
            openMySlot();
        });
        cardChangeRole.setOnClickListener(v -> changeRole());
    }

    private void setCardsEnabled(boolean enabled) {
        setStartSessionEnabled(enabled);
        setMySlotEnabled(enabled);
    }

    private void setStartSessionEnabled(boolean enabled) {
        cardStartSession.setClickable(enabled); cardStartSession.setFocusable(enabled); cardStartSession.setAlpha(enabled ? 1.0f : 0.5f);
        if (cardStartSessionInner != null) cardStartSessionInner.setBackgroundResource(enabled ? R.drawable.bg_card_orange_light_gradient : R.drawable.bg_card_disabled);
    }

    private void setMySlotEnabled(boolean enabled) {
        cardMySlot.setClickable(enabled); cardMySlot.setFocusable(enabled); cardMySlot.setAlpha(enabled ? 1.0f : 0.5f);
        if (cardMySlotInner != null) cardMySlotInner.setBackgroundResource(enabled ? R.drawable.bg_card_orange_light_gradient : R.drawable.bg_card_disabled);
    }

    @Override protected void onResume() { super.onResume(); loadPresentationDetails(); checkRegistrationStatus(); }

    private void openStartSession() { startActivity(new Intent(this, PresenterStartSessionActivity.class)); }
    private void openSlotSelection(boolean s) { Intent i = new Intent(this, PresenterSlotSelectionActivity.class); i.putExtra(PresenterSlotSelectionActivity.EXTRA_SCROLL_TO_MY_SLOT, s); startActivity(i); }
    private void openMySlot() { startActivity(new Intent(this, PresenterMySlotActivity.class)); }
    private void openSettings() { startActivity(new Intent(this, SettingsActivity.class)); }

    private void changeRole() {
        String username = preferencesManager.getUserName();
        String firstName = preferencesManager.getFirstName();
        String lastName = preferencesManager.getLastName();
        String email = preferencesManager.getEmail();
        String degree = preferencesManager.getDegree();
        String participation = preferencesManager.getParticipationPreference();
        preferencesManager.setUserRole(null);
        preferencesManager.setInitialSetupCompleted(false);
        if (username != null && !username.isEmpty()) preferencesManager.setUserName(username);
        if (firstName != null && !firstName.isEmpty()) preferencesManager.setFirstName(firstName);
        if (lastName != null && !lastName.isEmpty()) preferencesManager.setLastName(lastName);
        if (email != null && !email.isEmpty()) preferencesManager.setEmail(email);
        if (degree != null && !degree.isEmpty()) preferencesManager.setDegree(degree);
        if (participation != null && !participation.isEmpty()) preferencesManager.setParticipationPreference(participation);
        navigateToRolePicker();
    }

    private void navigateToRolePicker() {
        Intent intent = new Intent(this, RolePickerActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override @SuppressWarnings("deprecation") public void onBackPressed() { moveTaskToBack(true); super.onBackPressed(); }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
                    showNotificationPermissionRationale();
                } else {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.POST_NOTIFICATIONS},
                            REQUEST_NOTIFICATION_PERMISSION);
                }
            }
        }
    }

    private void showNotificationPermissionRationale() {
        new AlertDialog.Builder(this)
                .setTitle("Enable Notifications")
                .setMessage("SemScan needs notification permission to alert you when your registration is approved or when you're promoted from a waiting list.")
                .setPositiveButton("Allow", (dialog, which) -> {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.POST_NOTIFICATIONS},
                            REQUEST_NOTIFICATION_PERMISSION);
                })
                .setNegativeButton("Not Now", null)
                .show();
    }

    private void showNotificationSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Notifications Disabled")
                .setMessage("To receive alerts about registration approvals and waiting list updates, please enable notifications in Settings.")
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    Intent intent = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                    intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
                    startActivity(intent);
                })
                .setNegativeButton("Not Now", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Logger.i(Logger.TAG_PREFS, "POST_NOTIFICATIONS permission granted");
            } else {
                Logger.w(Logger.TAG_PREFS, "POST_NOTIFICATIONS permission denied");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
                        showNotificationSettingsDialog();
                    }
                }
            }
        }
    }
}