package org.example.semscan.ui.teacher;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;

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

    private CardView cardStartSession;
    private CardView cardEnrollSlot;
    private CardView cardMySlot;
    private CardView cardChangeRole;
    private TextView textWelcomeMessage;
    private PreferencesManager preferencesManager;
    private ApiService apiService;
    private ServerLogger serverLogger;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_presenter_home);
        
        preferencesManager = PreferencesManager.getInstance(this);
        apiService = ApiClient.getInstance(this).getApiService();
        serverLogger = ServerLogger.getInstance(this);
        
        // Update user context for logging
        String username = preferencesManager.getUserName();
        String userRole = preferencesManager.getUserRole();
        if (serverLogger != null) {
            serverLogger.updateUserContext(username, userRole);
        }
        
        // Check if user is actually a presenter
        if (!preferencesManager.isPresenter()) {
            navigateToRolePicker();
            return;
        }
        
        initializeViews();
        setupToolbar();
        setupClickListeners();
        updateWelcomeMessage();

        Logger.userAction("Open Presenter Home", "Presenter home screen opened");
        if (serverLogger != null) {
            serverLogger.userAction("Open Presenter Home", "Presenter home screen opened");
        }
    }
    
    private void initializeViews() {
        cardStartSession = findViewById(R.id.card_start_session);
        cardEnrollSlot = findViewById(R.id.card_enroll_slot);
        cardMySlot = findViewById(R.id.card_my_slot);
        cardChangeRole = findViewById(R.id.card_change_role);
        textWelcomeMessage = findViewById(R.id.text_welcome_message);
    }
    
    private void updateWelcomeMessage() {
        final String cachedName = preferencesManager.getUserName();
        if (!TextUtils.isEmpty(cachedName)) {
            textWelcomeMessage.setText(getString(R.string.welcome_user, cachedName.trim()));
        } else {
            textWelcomeMessage.setText(R.string.presenter_home_welcome_default);
        }

        final String username = preferencesManager.getUserName();
        if (username == null || username.trim().isEmpty()) {
            Logger.w(Logger.TAG_UI, "No username cached; skipping presenter home fetch");
            return;
        }

        final String normalizedUsername = username.trim().toLowerCase(Locale.US);
        apiService.getPresenterHome(normalizedUsername).enqueue(new Callback<ApiService.PresenterHomeResponse>() {
            @Override
            public void onResponse(Call<ApiService.PresenterHomeResponse> call, Response<ApiService.PresenterHomeResponse> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().presenter == null) {
                    Logger.w(Logger.TAG_UI, "Presenter home fetch failed code=" + response.code());
                    return;
                }
                String name = response.body().presenter.name;
                if (name == null || name.trim().isEmpty()) {
                    return;
                }
                textWelcomeMessage.setText(getString(R.string.welcome_user, name.trim()));
                if (serverLogger != null) {
                    serverLogger.i(ServerLogger.TAG_UI, "Presenter resolved from home endpoint: " + name);
                }
            }

            @Override
            public void onFailure(Call<ApiService.PresenterHomeResponse> call, Throwable t) {
                Logger.e(Logger.TAG_UI, "Failed to load presenter home", t);
                if (serverLogger != null) {
                    serverLogger.e(ServerLogger.TAG_UI, "Failed to load presenter home", t);
                }
            }
        });
    }
    
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }
    }
    
    private void setupClickListeners() {
        cardStartSession.setOnClickListener(v -> {
            Logger.userAction("Open Session", "Presenter tapped open session card");
            if (serverLogger != null) {
                serverLogger.userAction("Open Session", "Presenter tapped open session card");
            }
            openStartSession();
        });

        cardEnrollSlot.setOnClickListener(v -> {
            Logger.userAction("Open Slot Selection", "Presenter tapped enroll slot card");
            if (serverLogger != null) {
                serverLogger.userAction("Open Slot Selection", "Presenter tapped enroll slot card");
            }
            openSlotSelection(false);
        });

        cardMySlot.setOnClickListener(v -> {
            Logger.userAction("Open My Slot", "Presenter tapped my slot card");
            if (serverLogger != null) {
                serverLogger.userAction("Open My Slot", "Presenter tapped my slot card");
            }
            openMySlot();
        });
        
        cardChangeRole.setOnClickListener(v -> {
            Logger.userAction("Change Role", "Presenter tapped change role card");
            if (serverLogger != null) {
                serverLogger.userAction("Change Role", "Presenter tapped change role card");
            }
            changeRole();
        });
    }
    
    private void openStartSession() {
        Intent intent = new Intent(this, PresenterStartSessionActivity.class);
        startActivity(intent);
        Logger.userAction("Navigate", "Opened PresenterStartSessionActivity");
        if (serverLogger != null) {
            serverLogger.userAction("Navigate", "Opened PresenterStartSessionActivity");
        }
    }
    
    private void openSlotSelection(boolean scrollToMySlot) {
        Intent intent = new Intent(this, PresenterSlotSelectionActivity.class);
        intent.putExtra(PresenterSlotSelectionActivity.EXTRA_SCROLL_TO_MY_SLOT, scrollToMySlot);
        startActivity(intent);
    }

    private void openMySlot() {
        Intent intent = new Intent(this, PresenterMySlotActivity.class);
        startActivity(intent);
    }
    
    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
        Logger.userAction("Navigate", "Opened SettingsActivity from presenter home");
        if (serverLogger != null) {
            serverLogger.userAction("Navigate", "Opened SettingsActivity from presenter home");
        }
    }
    
    private void changeRole() {
        Logger.userAction("Change Role", "Presenter clicked change role");
        if (serverLogger != null) {
            serverLogger.userAction("Change Role", "Presenter clicked change role");
        }
        
        // Preserve username when changing roles - only clear the role, not all user data
        String username = preferencesManager.getUserName();
        String firstName = preferencesManager.getFirstName();
        String lastName = preferencesManager.getLastName();
        String email = preferencesManager.getEmail();
        String degree = preferencesManager.getDegree();
        String participation = preferencesManager.getParticipationPreference();
        
        // Clear only role-related data
        preferencesManager.setUserRole(null);
        preferencesManager.setInitialSetupCompleted(false);
        
        // Restore user data (username is critical for role selection)
        if (username != null && !username.isEmpty()) {
            preferencesManager.setUserName(username);
            Logger.i(Logger.TAG_UI, "Preserved username when changing role: " + username);
        }
        if (firstName != null && !firstName.isEmpty()) {
            preferencesManager.setFirstName(firstName);
        }
        if (lastName != null && !lastName.isEmpty()) {
            preferencesManager.setLastName(lastName);
        }
        if (email != null && !email.isEmpty()) {
            preferencesManager.setEmail(email);
        }
        if (degree != null && !degree.isEmpty()) {
            preferencesManager.setDegree(degree);
        }
        if (participation != null && !participation.isEmpty()) {
            preferencesManager.setParticipationPreference(participation);
        }
        
        navigateToRolePicker();
    }
    
    private void navigateToRolePicker() {
        Intent intent = new Intent(this, RolePickerActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    // Menu removed - no longer showing 3 dots menu
    
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // Prevent going back to role picker
        moveTaskToBack(true);
        super.onBackPressed(); // Required by lint
    }
}