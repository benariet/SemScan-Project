package org.example.semscan.ui.teacher;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.example.semscan.R;
import org.example.semscan.data.model.ManualAttendanceResponse;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ManualRequestAdapter extends RecyclerView.Adapter<ManualRequestAdapter.RequestViewHolder> {
    
    private List<ManualAttendanceResponse> requests;
    private OnRequestActionListener listener;
    private SimpleDateFormat timeFormat;
    
    public interface OnRequestActionListener {
        void onApprove(ManualAttendanceResponse request);
        void onReject(ManualAttendanceResponse request);
    }
    
    public ManualRequestAdapter(OnRequestActionListener listener) {
        this.requests = new ArrayList<>();
        this.listener = listener;
        this.timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    }
    
    public void updateRequests(List<ManualAttendanceResponse> newRequests) {
        this.requests.clear();
        this.requests.addAll(newRequests);
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public RequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_manual_request, parent, false);
        return new RequestViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull RequestViewHolder holder, int position) {
        ManualAttendanceResponse request = requests.get(position);
        holder.bind(request);
    }
    
    @Override
    public int getItemCount() {
        return requests.size();
    }
    
    class RequestViewHolder extends RecyclerView.ViewHolder {
        private TextView textStudentName;
        private TextView textRequestTime;
        private TextView textReason;
        private TextView textFlags;
        private TextView textDuplicateWarning;
        private Button btnApprove;
        private Button btnReject;
        
        public RequestViewHolder(@NonNull View itemView) {
            super(itemView);
            textStudentName = itemView.findViewById(R.id.text_student_name);
            textRequestTime = itemView.findViewById(R.id.text_request_time);
            textReason = itemView.findViewById(R.id.text_reason);
            textFlags = itemView.findViewById(R.id.text_flags);
            textDuplicateWarning = itemView.findViewById(R.id.text_duplicate_warning);
            btnApprove = itemView.findViewById(R.id.btn_approve);
            btnReject = itemView.findViewById(R.id.btn_reject);
        }
        
        public void bind(ManualAttendanceResponse request) {
            // Set student name - prefer full name, fall back to username
            String displayName = request.getStudentName();
            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = request.getStudentUsername();
            }
            textStudentName.setText(displayName);

            // Set request time
            if (request.getRequestedAt() != null) {
                textRequestTime.setText(request.getRequestedAt());
            } else {
                textRequestTime.setText("-");
            }

            // Set reason
            textReason.setText(request.getReason() != null ? request.getReason() : "No reason provided");

            // Set flags based on auto_flags JSON
            setFlags(request);

            // Set button listeners
            btnApprove.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onApprove(request);
                }
            });

            btnReject.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onReject(request);
                }
            });
        }

        private void setFlags(ManualAttendanceResponse request) {
            // Default values
            boolean inWindow = true;
            boolean isDuplicate = false;

            // Parse auto_flags JSON if available
            String autoFlags = request.getAutoFlags();
            if (autoFlags != null && !autoFlags.isEmpty()) {
                try {
                    JSONObject flags = new JSONObject(autoFlags);
                    inWindow = flags.optBoolean("inWindow", true);
                    isDuplicate = flags.optBoolean("duplicate", false);
                } catch (Exception e) {
                    // Ignore parse errors, use defaults
                }
            }

            // Show in-window status
            if (inWindow) {
                textFlags.setText("\u2713 In window");
                textFlags.setVisibility(View.VISIBLE);
            } else {
                textFlags.setText("\u2717 Outside window");
                textFlags.setVisibility(View.VISIBLE);
            }

            // Show duplicate warning if applicable
            if (isDuplicate) {
                textDuplicateWarning.setText("\u26A0 Duplicate");
                textDuplicateWarning.setVisibility(View.VISIBLE);
            } else {
                textDuplicateWarning.setVisibility(View.GONE);
            }
        }
    }
}
