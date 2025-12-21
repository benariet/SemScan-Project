package org.example.semscan.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.example.semscan.R;
import org.example.semscan.data.api.ApiClient;
import org.example.semscan.data.api.ApiService;
import org.example.semscan.data.model.User;
import org.example.semscan.ui.RolePickerActivity;
import org.example.semscan.utils.ConfigManager;
import org.example.semscan.utils.Logger;
import org.example.semscan.utils.PreferencesManager;
import org.example.semscan.utils.ServerLogger;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FirstTimeSetupActivity extends AppCompatActivity {

    public static final String EXTRA_SELECTED_DEGREE = "extra_selected_degree";
    public static final String EXTRA_SELECTED_PARTICIPATION = "extra_selected_participation";
    public static final String EXTRA_SELECTED_LABEL = "extra_selected_label";

    public static final String DEGREE_MSC = "MSc";
    public static final String DEGREE_PHD = "PhD";

    public static final String PARTICIPATION_PRESENTER_ONLY = "PRESENTER_ONLY";
    public static final String PARTICIPATION_PARTICIPANT_ONLY = "PARTICIPANT_ONLY";
    public static final String PARTICIPATION_BOTH = "BOTH";

    private TextInputEditText editFirstName;
    private TextInputEditText editLastName;
    private TextInputEditText editNationalId;
    private TextInputLayout inputLayoutNationalId;
    private TextInputEditText editPresenterName;
    private TextInputEditText editPresenterLastName;
    private TextInputEditText editEmail;
    private TextInputLayout inputLayoutEmail;
    private MaterialCheckBox checkboxConfirmNationalId;
    private MaterialCardView cardDegree;
    private MaterialCardView cardParticipation;
    private TextView textSelectedDegree;
    private TextView textSelectedParticipation;
    private View btnSubmit;
    private CircularProgressIndicator progressBar;

    private PreferencesManager preferencesManager;
    private ServerLogger serverLogger;
    private ApiService apiService;

    private String selectedDegree;
    private String selectedParticipation;
    private String selectedParticipationLabel;

    private ActivityResultLauncher<Intent> degreeLauncher;
    private ActivityResultLauncher<Intent> participationLauncher;
    private ActivityResultLauncher<Intent> mscLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first_time_setup);

        preferencesManager = PreferencesManager.getInstance(this);
        serverLogger = ServerLogger.getInstance(this);
        apiService = ApiClient.getInstance(this).getApiService();

        initViews();
        initActivityResults();
        setupListeners();
        setupNationalIdValidation();

        Logger.i(Logger.TAG_UI, "FirstTimeSetupActivity created for username=" + preferencesManager.getUserName());
        serverLogger.userAction("FirstTimeSetup", "Onboarding started");
    }

    private void initViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
        }

        editFirstName = findViewById(R.id.edit_first_name);
        editLastName = findViewById(R.id.edit_last_name);
        editNationalId = findViewById(R.id.edit_national_id);
        inputLayoutNationalId = findViewById(R.id.input_layout_national_id);
        editPresenterName = findViewById(R.id.edit_presenter_name);
        editPresenterLastName = findViewById(R.id.edit_presenter_last_name);
        editEmail = findViewById(R.id.edit_email);
        inputLayoutEmail = findViewById(R.id.input_layout_email);
        checkboxConfirmNationalId = findViewById(R.id.checkbox_confirm_national_id);
        cardDegree = findViewById(R.id.card_degree);
        cardParticipation = findViewById(R.id.card_role_context);
        textSelectedDegree = findViewById(R.id.text_selected_degree);
        textSelectedParticipation = findViewById(R.id.text_selected_participation);
        btnSubmit = findViewById(R.id.btn_submit_setup);
        progressBar = findViewById(R.id.progress_loading);

        if (!TextUtils.isEmpty(preferencesManager.getFirstName())) {
            editFirstName.setText(preferencesManager.getFirstName());
        }
        if (!TextUtils.isEmpty(preferencesManager.getLastName())) {
            editLastName.setText(preferencesManager.getLastName());
        }
        if (!TextUtils.isEmpty(preferencesManager.getDegree())) {
            selectedDegree = preferencesManager.getDegree();
            textSelectedDegree.setText(selectedDegree);
        }
        if (!TextUtils.isEmpty(preferencesManager.getParticipationPreference())) {
            selectedParticipation = preferencesManager.getParticipationPreference();
            updateParticipationLabel();
        }
        updateParticipationCardState();
    }

    private void initActivityResults() {
        degreeLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                selectedDegree = result.getData().getStringExtra(EXTRA_SELECTED_DEGREE);
                if (!TextUtils.isEmpty(selectedDegree)) {
                    textSelectedDegree.setText(selectedDegree);
                    serverLogger.userAction("FirstTimeSetup", "Degree selected=" + selectedDegree);
                    applyDegreeConstraints();
                }
            }
        });

        participationLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                selectedParticipation = result.getData().getStringExtra(EXTRA_SELECTED_PARTICIPATION);
                selectedParticipationLabel = result.getData().getStringExtra(EXTRA_SELECTED_LABEL);
                updateParticipationLabel();
            }
        });

        mscLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                selectedParticipation = result.getData().getStringExtra(EXTRA_SELECTED_PARTICIPATION);
                selectedParticipationLabel = result.getData().getStringExtra(EXTRA_SELECTED_LABEL);
                updateParticipationLabel();
            }
        });
    }

    private void setupListeners() {
        cardDegree.setOnClickListener(v -> openDegreeSelection());
        cardParticipation.setOnClickListener(v -> openParticipationSelector());
        btnSubmit.setOnClickListener(v -> submitProfile());
    }

    private void setupNationalIdValidation() {
        // Show/hide toggle for national ID
        if (inputLayoutNationalId != null && editNationalId != null) {
            // Initialize as hidden (password mode)
            editNationalId.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            inputLayoutNationalId.setEndIconDrawable(R.drawable.ic_visibility_off);
            
            inputLayoutNationalId.setEndIconOnClickListener(v -> {
                int inputType = editNationalId.getInputType();
                boolean isPasswordMode = (inputType & android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD) != 0;
                
                if (isPasswordMode) {
                    // Show - switch to visible number
                    editNationalId.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                    inputLayoutNationalId.setEndIconDrawable(R.drawable.ic_visibility);
                } else {
                    // Hide - switch to password
                    editNationalId.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
                    inputLayoutNationalId.setEndIconDrawable(R.drawable.ic_visibility_off);
                }
                // Move cursor to end
                if (editNationalId.getText() != null) {
                    editNationalId.setSelection(editNationalId.getText().length());
                }
            });
        }

        // Real-time validation as user types
        if (editNationalId != null) {
            editNationalId.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    validateNationalIdRealTime(s != null ? s.toString() : "");
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });
        }
    }

    private void validateNationalIdRealTime(String id) {
        if (id == null || id.trim().isEmpty()) {
            // Show error if field is empty (required field)
            if (inputLayoutNationalId != null) {
                inputLayoutNationalId.setError(getString(R.string.setup_national_id_error_required));
            }
            return;
        }

        String trimmedId = id.trim();
        
        // Validate length
        if (trimmedId.length() != 9) {
            if (inputLayoutNationalId != null) {
                inputLayoutNationalId.setError(getString(R.string.setup_national_id_error_length));
            }
            return;
        }

        // Validate all digits
        if (!trimmedId.matches("\\d{9}")) {
            if (inputLayoutNationalId != null) {
                inputLayoutNationalId.setError(getString(R.string.setup_national_id_error_length));
            }
            return;
        }

        // Validate checksum (Israeli ID algorithm)
        if (!validateIsraeliIdChecksum(trimmedId)) {
            if (inputLayoutNationalId != null) {
                inputLayoutNationalId.setError(getString(R.string.setup_national_id_error_checksum));
            }
            return;
        }

        // Valid - clear error
        if (inputLayoutNationalId != null) {
            inputLayoutNationalId.setError(null);
        }
    }

    /**
     * Validates Israeli National ID checksum using Luhn-like algorithm
     * Algorithm: Multiply each digit by 1 or 2 alternately, sum all results, check if mod 10 == 0
     */
    private boolean validateIsraeliIdChecksum(String id) {
        if (id == null || id.length() != 9 || !id.matches("\\d{9}")) {
            return false;
        }

        int sum = 0;
        for (int i = 0; i < 9; i++) {
            int digit = Character.getNumericValue(id.charAt(i));
            int multiplier = (i % 2 == 0) ? 1 : 2; // Alternate: 1, 2, 1, 2, ...
            int product = digit * multiplier;
            
            // If product is two digits, add them together (e.g., 12 -> 1+2 = 3)
            if (product >= 10) {
                product = (product / 10) + (product % 10);
            }
            
            sum += product;
        }

        return (sum % 10) == 0;
    }

    private void openDegreeSelection() {
        degreeLauncher.launch(new Intent(this, DegreeSelectionActivity.class));
    }

    private void openParticipationSelector() {
        // PhD students can now select participation (both presenter and participant roles)
        if (DEGREE_MSC.equals(selectedDegree)) {
            mscLauncher.launch(new Intent(this, MScRoleSelectionActivity.class));
        } else {
            participationLauncher.launch(new Intent(this, RoleContextActivity.class));
        }
    }

    private void applyDegreeConstraints() {
        // PhD students can now choose their participation role (presenter or participant)
        updateParticipationCardState();
        if (TextUtils.isEmpty(selectedParticipation)) {
            textSelectedParticipation.setText(R.string.setup_participation_placeholder);
        }
    }

    private void updateParticipationCardState() {
        // PhD students can now access participation selection
        cardParticipation.setEnabled(true);
        cardParticipation.setAlpha(1f);
    }

    private void updateParticipationLabel() {
        if (!TextUtils.isEmpty(selectedParticipationLabel)) {
            textSelectedParticipation.setText(selectedParticipationLabel);
        } else if (!TextUtils.isEmpty(selectedParticipation)) {
            switch (selectedParticipation) {
                case PARTICIPATION_PRESENTER_ONLY:
                    textSelectedParticipation.setText(R.string.setup_participation_presenter);
                    break;
                case PARTICIPATION_PARTICIPANT_ONLY:
                    textSelectedParticipation.setText(R.string.setup_participation_participant);
                    break;
                case PARTICIPATION_BOTH:
                    textSelectedParticipation.setText(R.string.setup_participation_both);
                    break;
                default:
                    textSelectedParticipation.setText(selectedParticipation);
                    break;
            }
        } else {
            textSelectedParticipation.setText(R.string.setup_participation_placeholder);
        }
        serverLogger.userAction("FirstTimeSetup", "Participation preference=" + selectedParticipation);
    }

    private void submitProfile() {
        String firstName = trim(editFirstName);
        String lastName = trim(editLastName);
        String nationalIdNumber = trim(editNationalId); // Required field
        String presenterName = trim(editPresenterName);
        String presenterLastName = trim(editPresenterLastName);
        String email = trim(editEmail);

        boolean valid = true;

        if (TextUtils.isEmpty(firstName)) {
            editFirstName.setError(getString(R.string.setup_error_first_name));
            valid = false;
        } else {
            editFirstName.setError(null);
        }

        if (TextUtils.isEmpty(lastName)) {
            editLastName.setError(getString(R.string.setup_error_last_name));
            valid = false;
        } else {
            editLastName.setError(null);
        }

        if (TextUtils.isEmpty(presenterName)) {
            editPresenterName.setError(getString(R.string.setup_error_presenter_name));
            valid = false;
        } else {
            editPresenterName.setError(null);
        }

        if (TextUtils.isEmpty(presenterLastName)) {
            editPresenterLastName.setError(getString(R.string.setup_error_presenter_last_name));
            valid = false;
        } else {
            editPresenterLastName.setError(null);
        }

        if (TextUtils.isEmpty(email)) {
            if (inputLayoutEmail != null) {
                inputLayoutEmail.setError(getString(R.string.setup_error_email));
            }
            valid = false;
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            if (inputLayoutEmail != null) {
                inputLayoutEmail.setError(getString(R.string.setup_error_email_invalid));
            }
            valid = false;
        } else {
            if (inputLayoutEmail != null) {
                inputLayoutEmail.setError(null);
            }
        }

        // Validate national ID (required field)
        if (TextUtils.isEmpty(nationalIdNumber)) {
            if (inputLayoutNationalId != null) {
                inputLayoutNationalId.setError(getString(R.string.setup_national_id_error_required));
            }
            valid = false;
        } else {
            String trimmedId = nationalIdNumber.trim();
            
            // Validate length
            if (trimmedId.length() != 9) {
                if (inputLayoutNationalId != null) {
                    inputLayoutNationalId.setError(getString(R.string.setup_national_id_error_length));
                }
                valid = false;
            }
            // Validate all digits
            else if (!trimmedId.matches("\\d{9}")) {
                if (inputLayoutNationalId != null) {
                    inputLayoutNationalId.setError(getString(R.string.setup_national_id_error_length));
                }
                valid = false;
            }
            // Validate checksum
            else if (!validateIsraeliIdChecksum(trimmedId)) {
                if (inputLayoutNationalId != null) {
                    inputLayoutNationalId.setError(getString(R.string.setup_national_id_error_checksum));
                }
                valid = false;
            }
            // Require confirmation checkbox
            else if (checkboxConfirmNationalId != null && !checkboxConfirmNationalId.isChecked()) {
                Toast.makeText(this, R.string.setup_national_id_error_confirm, Toast.LENGTH_SHORT).show();
                valid = false;
            }
            else {
                // Valid - clear error
                if (inputLayoutNationalId != null) {
                    inputLayoutNationalId.setError(null);
                }
            }
        }

        if (TextUtils.isEmpty(selectedDegree)) {
            Toast.makeText(this, R.string.setup_error_degree, Toast.LENGTH_SHORT).show();
            valid = false;
        }

        if (TextUtils.isEmpty(selectedParticipation)) {
            Toast.makeText(this, R.string.setup_error_participation, Toast.LENGTH_SHORT).show();
            valid = false;
        }

        if (!valid) {
            return;
        }

        toggleLoading(true);

        String username = preferencesManager.getUserName();
        
        // CRITICAL: Check if username exists before proceeding
        if (username == null || username.isEmpty()) {
            Logger.e(Logger.TAG_UI, "ERROR: Username is NULL or empty in FirstTimeSetupActivity!");
            Logger.e(Logger.TAG_UI, "Cannot create user profile without username. User must log in first.");
            toggleLoading(false);
            Toast.makeText(this, "Username not found. Please log in again.", Toast.LENGTH_LONG).show();
            // Navigate back to login
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }
        
        Logger.i(Logger.TAG_UI, "Creating user profile with username: " + username);
        // Use the email from the input field, or fallback to username + domain if empty
        String emailDomain = ConfigManager.getInstance(this).getEmailDomain();
        String emailToUse = email;
        if (TextUtils.isEmpty(emailToUse) && username != null) {
            emailToUse = username + emailDomain;
        }
        final String finalEmail = emailToUse;

        ApiService.UserProfileUpdateRequest request = new ApiService.UserProfileUpdateRequest(
                username,
                finalEmail,
                firstName,
                lastName,
                selectedDegree,
                selectedParticipation,
                nationalIdNumber // Optional - can be null or empty
        );

        // Create user directly - this is the first time creating the user
        serverLogger.api("POST", "/api/v1/users", "Creating new user profile for " + username);
        
        apiService.upsertUser(request).enqueue(new Callback<User>() {
            @Override
            public void onResponse(Call<User> call, Response<User> response) {
                toggleLoading(false);
                if (!response.isSuccessful() || response.body() == null) {
                    String errorMsg = "Failed to create user profile";
                    try {
                        if (response.errorBody() != null) {
                            String errorBody = response.errorBody().string();
                            errorMsg = errorBody.length() > 100 ? errorBody.substring(0, 100) : errorBody;
                        }
                    } catch (Exception e) {
                        // Ignore
                    }
                    Logger.apiError("POST", "/api/v1/users", response.code(), errorMsg);
                    serverLogger.e(ServerLogger.TAG_API, "Failed to create user profile: " + errorMsg);
                    Toast.makeText(FirstTimeSetupActivity.this, errorMsg != null && !errorMsg.trim().isEmpty() ? errorMsg : getString(R.string.error_operation_failed), Toast.LENGTH_LONG).show();
                    return;
                }

                persistProfile(firstName, lastName, finalEmail, selectedDegree, selectedParticipation, nationalIdNumber);
                serverLogger.updateUserContext(preferencesManager.getUserName(), preferencesManager.getUserRole());
                Toast.makeText(FirstTimeSetupActivity.this, R.string.setup_success, Toast.LENGTH_LONG).show();
                Logger.userAction("FirstTimeSetup", "Onboarding complete");
                serverLogger.userAction("FirstTimeSetup", "Onboarding complete");
                navigateToRolePicker();
            }

            @Override
            public void onFailure(Call<User> call, Throwable t) {
                toggleLoading(false);
                Logger.e(Logger.TAG_API, "Failed to create user profile", t);
                serverLogger.e(ServerLogger.TAG_API, "Failed to create user profile", t);
                String errorMessage = getString(R.string.error_network_connection);
                if (t instanceof java.net.SocketTimeoutException || t instanceof java.net.ConnectException) {
                    errorMessage = getString(R.string.error_network_timeout);
                } else if (t instanceof java.net.UnknownHostException) {
                    errorMessage = getString(R.string.error_server_unavailable);
                }
                Toast.makeText(FirstTimeSetupActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void persistProfile(String firstName, String lastName, String email, String degree, String participation, String nationalIdNumber) {
        // CRITICAL: Ensure username is preserved - it should already be set from login
        String username = preferencesManager.getUserName();
        if (username == null || username.isEmpty()) {
            Logger.e(Logger.TAG_UI, "WARNING: Username is NULL in persistProfile! This should not happen.");
            Logger.e(Logger.TAG_UI, "Username should have been set during login. User needs to log in again.");
        } else {
            // Explicitly preserve username (should already be set, but ensure it's not lost)
            preferencesManager.setUserName(username);
            Logger.i(Logger.TAG_UI, "Preserving username in persistProfile: " + username);
        }
        
        preferencesManager.setFirstName(firstName);
        preferencesManager.setLastName(lastName);
        preferencesManager.setEmail(email);
        preferencesManager.setDegree(degree);
        preferencesManager.setParticipationPreference(participation);
        preferencesManager.setInitialSetupCompleted(true);

        if (PARTICIPATION_PRESENTER_ONLY.equals(participation)) {
            preferencesManager.setUserRole("PRESENTER");
        } else if (PARTICIPATION_PARTICIPANT_ONLY.equals(participation)) {
            preferencesManager.setUserRole("PARTICIPANT"); // Changed from "STUDENT" to "PARTICIPANT" for consistency
        } else {
            preferencesManager.setUserRole(null);
        }
        
        // Final check: Log all saved values
        Logger.i(Logger.TAG_UI, "=== Profile Persisted ===");
        Logger.i(Logger.TAG_UI, "Username: " + preferencesManager.getUserName());
        Logger.i(Logger.TAG_UI, "Role: " + preferencesManager.getUserRole());
        Logger.i(Logger.TAG_UI, "First Name: " + preferencesManager.getFirstName());
        Logger.i(Logger.TAG_UI, "Last Name: " + preferencesManager.getLastName());
        Logger.i(Logger.TAG_UI, "Email: " + preferencesManager.getEmail());
        Logger.i(Logger.TAG_UI, "Degree: " + preferencesManager.getDegree());
        Logger.i(Logger.TAG_UI, "Participation: " + preferencesManager.getParticipationPreference());
        Logger.i(Logger.TAG_UI, "=========================");
    }

    private void navigateToRolePicker() {
        Intent intent = new Intent(this, RolePickerActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void toggleLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSubmit.setEnabled(!loading);
        cardDegree.setEnabled(!loading);
        if (loading) {
            cardParticipation.setEnabled(false);
            cardParticipation.setAlpha(0.6f);
        } else {
            updateParticipationCardState();
        }
        editFirstName.setEnabled(!loading);
        editLastName.setEnabled(!loading);
        editPresenterName.setEnabled(!loading);
        editPresenterLastName.setEnabled(!loading);
        editEmail.setEnabled(!loading);
    }

    private String trim(@Nullable TextInputEditText editText) {
        if (editText == null || editText.getText() == null) {
            return null;
        }
        return editText.getText().toString().trim();
    }
}

