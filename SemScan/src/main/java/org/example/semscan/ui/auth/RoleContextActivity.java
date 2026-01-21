package org.example.semscan.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;

import org.example.semscan.R;

public class RoleContextActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_context);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
        }

        MaterialCardView presenterCard = findViewById(R.id.card_participation_presenter);
        MaterialCardView participantCard = findViewById(R.id.card_participation_participant);
        MaterialCardView bothCard = findViewById(R.id.card_participation_both);

        presenterCard.setOnClickListener(v -> returnSelection(FirstTimeSetupActivity.PARTICIPATION_PRESENTER_ONLY,
                getString(R.string.setup_participation_presenter)));
        participantCard.setOnClickListener(v -> returnSelection(FirstTimeSetupActivity.PARTICIPATION_PARTICIPANT_ONLY,
                getString(R.string.setup_participation_participant)));
        bothCard.setOnClickListener(v -> returnSelection(FirstTimeSetupActivity.PARTICIPATION_BOTH,
                getString(R.string.setup_participation_both)));
    }

    private void returnSelection(String value, String label) {
        Intent result = new Intent();
        result.putExtra(FirstTimeSetupActivity.EXTRA_SELECTED_PARTICIPATION, value);
        result.putExtra(FirstTimeSetupActivity.EXTRA_SELECTED_LABEL, label);
        setResult(RESULT_OK, result);
        finish();
    }
}

