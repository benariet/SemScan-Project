package org.example.semscan.ui.student;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.example.semscan.R;
import org.example.semscan.data.api.ApiClient;
import org.example.semscan.data.api.ApiService;
import org.example.semscan.data.model.Attendance;
import org.example.semscan.utils.Logger;
import org.example.semscan.utils.PreferencesManager;
import org.example.semscan.utils.ServerLogger;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AttendanceHistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView textTotal;
    private TextView textEmptyState;
    private AttendanceHistoryAdapter adapter;
    private ApiService apiService;
    private PreferencesManager preferencesManager;
    private ServerLogger serverLogger;
    private List<Attendance> attendanceList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance_history);

        Logger.i(Logger.TAG_SCREEN_VIEW, "AttendanceHistoryActivity created");
        Logger.userAction("Open Attendance History", "Student opened attendance history");

        preferencesManager = PreferencesManager.getInstance(this);
        apiService = ApiClient.getInstance(this).getApiService();
        serverLogger = ServerLogger.getInstance(this);

        initializeViews();
        setupToolbar();
        loadAttendanceHistory();
    }

    private void initializeViews() {
        recyclerView = findViewById(R.id.recycler_attendance_history);
        textTotal = findViewById(R.id.text_total);
        textEmptyState = findViewById(R.id.text_empty_state);

        adapter = new AttendanceHistoryAdapter(new ArrayList<>());
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.attendance_history);
        }

        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadAttendanceHistory() {
        String username = preferencesManager.getUserName();
        if (username == null || username.isEmpty()) {
            Logger.e(Logger.TAG_API, "Username not found, cannot load attendance history");
            showError("User not logged in. Please log in again.");
            return;
        }

        Logger.i(Logger.TAG_API, "Loading attendance history for user: " + username);
        if (serverLogger != null) {
            serverLogger.i(ServerLogger.TAG_API, "Loading attendance history for user: " + username);
        }

        showLoading(true);

        Call<List<Attendance>> call = apiService.getAttendanceByStudent(username);
        call.enqueue(new Callback<List<Attendance>>() {
            @Override
            public void onResponse(@NonNull Call<List<Attendance>> call, @NonNull Response<List<Attendance>> response) {
                showLoading(false);

                if (response.isSuccessful() && response.body() != null) {
                    attendanceList = response.body();
                    
                    // Sort by attendance time (most recent first)
                    Collections.sort(attendanceList, new Comparator<Attendance>() {
                        @Override
                        public int compare(Attendance a1, Attendance a2) {
                            try {
                                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                                Date date1 = sdf.parse(a1.getAttendanceTime());
                                Date date2 = sdf.parse(a2.getAttendanceTime());
                                if (date1 != null && date2 != null) {
                                    return date2.compareTo(date1); // Descending order
                                }
                            } catch (ParseException e) {
                                Logger.e(Logger.TAG_API, "Error parsing attendance time", e);
                            }
                            return 0;
                        }
                    });

                    Logger.i(Logger.TAG_API, "Loaded " + attendanceList.size() + " attendance records");
                    if (serverLogger != null) {
                        serverLogger.i(ServerLogger.TAG_API, "Loaded " + attendanceList.size() + " attendance records");
                    }

                    updateUI();
                } else {
                    Logger.e(Logger.TAG_API, "Failed to load attendance history - Status: " + response.code());
                    if (serverLogger != null) {
                        serverLogger.e(ServerLogger.TAG_API, "Failed to load attendance history - Status: " + response.code());
                    }
                    showError(getString(R.string.error_load_failed));
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Attendance>> call, @NonNull Throwable t) {
                showLoading(false);
                Logger.e(Logger.TAG_API, "Failed to load attendance history", t);
                if (serverLogger != null) {
                    serverLogger.e(ServerLogger.TAG_API, "Failed to load attendance history: " + t.getMessage());
                }
                showError(getString(R.string.error_network_connection));
            }
        });
    }

    private void updateUI() {
        if (attendanceList.isEmpty()) {
            textEmptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            textTotal.setVisibility(View.GONE);
        } else {
            textEmptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            textTotal.setVisibility(View.VISIBLE);

            adapter.updateList(attendanceList);
            adapter.notifyDataSetChanged();

            // Update total count
            int total = attendanceList.size();
            textTotal.setText(getString(R.string.attendance_history_total, total));
        }
    }

    private void showLoading(boolean show) {
        // You can add a progress bar here if needed
        if (show) {
            textEmptyState.setText(R.string.loading);
            textEmptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            textTotal.setVisibility(View.GONE);
        }
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        textEmptyState.setText(message);
        textEmptyState.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        textTotal.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        if (serverLogger != null) {
            serverLogger.flushLogs();
        }
        super.onDestroy();
    }
}
