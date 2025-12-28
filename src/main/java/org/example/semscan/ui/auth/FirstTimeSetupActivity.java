package org.example.semscan.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
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
    private TextInputLayout inputLayoutFirstName;
    private TextInputLayout inputLayoutLastName;
    private TextInputLayout inputLayoutNationalId;
    private RadioGroup radioGroupDegree;
    private View btnSubmit;
    private CircularProgressIndicator progressBar;

    private PreferencesManager preferencesManager;
    private ServerLogger serverLogger;
    private ApiService apiService;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first_time_setup);

        preferencesManager = PreferencesManager.getInstance(this);
        serverLogger = ServerLogger.getInstance(this);
        apiService = ApiClient.getInstance(this).getApiService();

        initViews();
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
        inputLayoutFirstName = findViewById(R.id.input_layout_first_name);
        inputLayoutLastName = findViewById(R.id.input_layout_last_name);
        inputLayoutNationalId = findViewById(R.id.input_layout_national_id);
        radioGroupDegree = findViewById(R.id.radio_group_degree);
        btnSubmit = findViewById(R.id.btn_submit_setup);
        progressBar = findViewById(R.id.progress_loading);

        if (!TextUtils.isEmpty(preferencesManager.getFirstName())) {
            editFirstName.setText(preferencesManager.getFirstName());
        }
        if (!TextUtils.isEmpty(preferencesManager.getLastName())) {
            editLastName.setText(preferencesManager.getLastName());
        }

        // Pre-select degree if already set
        String savedDegree = preferencesManager.getDegree();
        if (DEGREE_PHD.equals(savedDegree)) {
            radioGroupDegree.check(R.id.radio_degree_phd);
        } else if (DEGREE_MSC.equals(savedDegree)) {
            radioGroupDegree.check(R.id.radio_degree_msc);
        }
    }

    private void setupListeners() {
        btnSubmit.setOnClickListener(v -> submitProfile());
    }

    private void setupNationalIdValidation() {
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

    private void submitProfile() {
        String firstName = trim(editFirstName);
        String lastName = trim(editLastName);
        String nationalIdNumber = trim(editNationalId);

        // Get selected degree
        int selectedDegreeId = radioGroupDegree.getCheckedRadioButtonId();
        String selectedDegree = null;
        if (selectedDegreeId == R.id.radio_degree_phd) {
            selectedDegree = DEGREE_PHD;
        } else if (selectedDegreeId == R.id.radio_degree_msc) {
            selectedDegree = DEGREE_MSC;
        }

        boolean valid = true;

        // Validate first name
        if (TextUtils.isEmpty(firstName)) {
            if (inputLayoutFirstName != null) {
                inputLayoutFirstName.setError(getString(R.string.setup_error_first_name));
            }
            valid = false;
        } else {
            if (inputLayoutFirstName != null) {
                inputLayoutFirstName.setError(null);
            }
        }

        // Validate last name
        if (TextUtils.isEmpty(lastName)) {
            if (inputLayoutLastName != null) {
                inputLayoutLastName.setError(getString(R.string.setup_error_last_name));
            }
            valid = false;
        } else {
            if (inputLayoutLastName != null) {
                inputLayoutLastName.setError(null);
            }
        }

        // Validate national ID
        if (TextUtils.isEmpty(nationalIdNumber)) {
            if (inputLayoutNationalId != null) {
                inputLayoutNationalId.setError(getString(R.string.setup_national_id_error_required));
            }
            valid = false;
        } else {
            String trimmedId = nationalIdNumber.trim();

            if (trimmedId.length() != 9) {
                if (inputLayoutNationalId != null) {
                    inputLayoutNationalId.setError(getString(R.string.setup_national_id_error_length));
                }
                valid = false;
            } else if (!trimmedId.matches("\\d{9}")) {
                if (inputLayoutNationalId != null) {
                    inputLayoutNationalId.setError(getString(R.string.setup_national_id_error_length));
                }
                valid = false;
            } else if (!validateIsraeliIdChecksum(trimmedId)) {
                if (inputLayoutNationalId != null) {
                    inputLayoutNationalId.setError(getString(R.string.setup_national_id_error_checksum));
                }
                valid = false;
            } else {
                if (inputLayoutNationalId != null) {
                    inputLayoutNationalId.setError(null);
                }
            }
        }

        // Validate degree selection
        if (TextUtils.isEmpty(selectedDegree)) {
            Toast.makeText(this, R.string.setup_error_degree, Toast.LENGTH_SHORT).show();
            valid = false;
        }

        if (!valid) {
            return;
        }

        // Save to preferences
        preferencesManager.setFirstName(firstName);
        preferencesManager.setLastName(lastName);
        preferencesManager.setNationalId(nationalIdNumber);
        preferencesManager.setDegree(selectedDegree);

        // Everyone is ALWAYS both presenter and participant
        preferencesManager.setParticipationPreference(PARTICIPATION_BOTH);

        Logger.i(Logger.TAG_UI, "First time setup form validated, degree=" + selectedDegree);
        serverLogger.userAction("FirstTimeSetup", "Degree selected: " + selectedDegree);

        // Update the user profile on the server with BOTH (everyone is always both)
        updateUserProfile(PARTICIPATION_BOTH);
    }

    private void updateUserProfile(String participationPreference) {
        String username = preferencesManager.getUserName();
        String firstName = preferencesManager.getFirstName();
        String lastName = preferencesManager.getLastName();
        String degree = preferencesManager.getDegree();
        String nationalId = preferencesManager.getNationalId();

        // Get email from preferences or derive from username
        String email = preferencesManager.getEmail();
        if (TextUtils.isEmpty(email)) {
            try {
                String emailDomain = ConfigManager.getInstance(this).getEmailDomain();
                email = username + emailDomain;
            } catch (Exception e) {
                Logger.e(Logger.TAG_UI, "Failed to get email domain from ConfigManager: " + e.getMessage(), e);
                email = username + "@bgu.ac.il"; // Fallback
            }
        }

        // Show loading indicator
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }
        if (btnSubmit != null) {
            btnSubmit.setEnabled(false);
        }

        Logger.i(Logger.TAG_API, "Updating user profile on server: username=" + username + ", degree=" + degree + ", participation=" + participationPreference);

        ApiService.UserProfileUpdateRequest request = new ApiService.UserProfileUpdateRequest(
                username,
                email,
                firstName,
                lastName,
                degree,
                participationPreference,
                nationalId
        );

        apiService.upsertUser(request).enqueue(new Callback<User>() {
            @Override
            public void onResponse(Call<User> call, Response<User> response) {
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }
                if (btnSubmit != null) {
                    btnSubmit.setEnabled(true);
                }

                if (response.isSuccessful() && response.body() != null) {
                    Logger.i(Logger.TAG_API, "User profile updated successfully on server");
                    serverLogger.userAction("FirstTimeSetup", "Profile saved to server successfully");

                    // Mark initial setup as completed
                    preferencesManager.setInitialSetupCompleted(true);

                    // Navigate to appropriate home screen based on participation preference
                    navigateToHome(participationPreference);
                } else {
                    Logger.w(Logger.TAG_API, "Failed to update user profile on server: " + response.code());
                    Toast.makeText(FirstTimeSetupActivity.this, "Failed to save profile. Please try again.", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<User> call, Throwable t) {
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }
                if (btnSubmit != null) {
                    btnSubmit.setEnabled(true);
                }

                Logger.e(Logger.TAG_API, "Network error updating user profile: " + t.getMessage(), t);
                Toast.makeText(FirstTimeSetupActivity.this, "Network error. Please check your connection and try again.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void navigateToHome(String participationPreference) {
        // Everyone is BOTH, so always go to role picker
        Intent intent = new Intent(this, RolePickerActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private String trim(@Nullable TextInputEditText editText) {
        if (editText == null || editText.getText() == null) {
            return null;
        }
        return editText.getText().toString().trim();
    }
}

