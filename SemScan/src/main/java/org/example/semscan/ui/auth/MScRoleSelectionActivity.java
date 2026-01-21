package org.example.semscan.ui.auth;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import org.example.semscan.R;

public class MScRoleSelectionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_msc_role_selection);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
        }

        MaterialButton presenterButton = findViewById(R.id.btn_msc_presenter);
        MaterialButton participantButton = findViewById(R.id.btn_msc_participant);
        MaterialButton bothButton = findViewById(R.id.btn_msc_both);

        presenterButton.setOnClickListener(v -> returnSelection(
                FirstTimeSetupActivity.PARTICIPATION_PRESENTER_ONLY,
                getString(R.string.setup_msc_option_presenter)
        ));

        participantButton.setOnClickListener(v -> returnSelection(
                FirstTimeSetupActivity.PARTICIPATION_PARTICIPANT_ONLY,
                getString(R.string.setup_msc_option_participant)
        ));

        bothButton.setOnClickListener(v -> returnSelection(
                FirstTimeSetupActivity.PARTICIPATION_BOTH,
                getString(R.string.setup_msc_option_both)
        ));
    }

    private void returnSelection(String value, String label) {
        Intent result = new Intent();
        result.putExtra(FirstTimeSetupActivity.EXTRA_SELECTED_PARTICIPATION, value);
        result.putExtra(FirstTimeSetupActivity.EXTRA_SELECTED_LABEL, label);
        setResult(RESULT_OK, result);
        finish();
    }
}

