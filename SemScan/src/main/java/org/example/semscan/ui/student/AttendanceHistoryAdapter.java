package org.example.semscan.ui.student;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.example.semscan.R;
import org.example.semscan.data.model.Attendance;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AttendanceHistoryAdapter extends RecyclerView.Adapter<AttendanceHistoryAdapter.ViewHolder> {

    private List<Attendance> attendanceList;

    public AttendanceHistoryAdapter(List<Attendance> attendanceList) {
        this.attendanceList = new ArrayList<>(attendanceList);
    }

    public void updateList(List<Attendance> newList) {
        this.attendanceList = new ArrayList<>(newList);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_attendance_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Attendance attendance = attendanceList.get(position);
        holder.bind(attendance);
    }

    @Override
    public int getItemCount() {
        return attendanceList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView textDate;
        private final TextView textTime;
        private final TextView textTopic;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            textDate = itemView.findViewById(R.id.text_date);
            textTime = itemView.findViewById(R.id.text_time);
            textTopic = itemView.findViewById(R.id.text_topic);
        }

        void bind(Attendance attendance) {
            // Parse and format attendance time
            String attendanceTime = attendance.getAttendanceTime();
            if (attendanceTime != null && !attendanceTime.isEmpty()) {
                try {
                    SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                    Date date = inputFormat.parse(attendanceTime);
                    if (date != null) {
                        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                        textDate.setText(dateFormat.format(date));
                        textTime.setText(timeFormat.format(date));
                    } else {
                        textDate.setText(attendanceTime);
                        textTime.setText("");
                    }
                } catch (ParseException e) {
                    textDate.setText(attendanceTime);
                    textTime.setText("");
                }
            } else {
                textDate.setText("N/A");
                textTime.setText("");
            }

            // Topic (always show, replace method badge)
            String topic = attendance.getTopic();
            if (topic != null && !topic.isEmpty()) {
                textTopic.setText(topic);
                textTopic.setVisibility(View.VISIBLE);
            } else {
                textTopic.setVisibility(View.GONE);
            }
        }
    }
}
