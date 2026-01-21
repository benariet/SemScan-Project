package org.example.semscan.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;

import org.example.semscan.R;

public class DegreeSelectionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_degree_selection);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
        }

        MaterialCardView mscCard = findViewById(R.id.card_degree_msc);
        MaterialCardView phdCard = findViewById(R.id.card_degree_phd);

        View.OnClickListener listener = v -> {
            String degree;
            if (v.getId() == R.id.card_degree_phd) {
                degree = FirstTimeSetupActivity.DEGREE_PHD;
            } else {
                degree = FirstTimeSetupActivity.DEGREE_MSC;
            }
            Intent result = new Intent();
            result.putExtra(FirstTimeSetupActivity.EXTRA_SELECTED_DEGREE, degree);
            setResult(RESULT_OK, result);
            finish();
        };

        mscCard.setOnClickListener(listener);
        phdCard.setOnClickListener(listener);
    }
}

