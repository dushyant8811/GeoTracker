package com.example.geotracker;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class AttendanceRecordsAdapter extends RecyclerView.Adapter<AttendanceRecordsAdapter.RecordViewHolder> {

    private List<AttendanceRecord> records = new ArrayList<>();
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private final SimpleDateFormat displayFormat = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());

    @NonNull
    @Override
    public RecordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_attendance_record, parent, false);
        return new RecordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecordViewHolder holder, int position) {
        AttendanceRecord record = records.get(position);
        holder.bind(record);
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    public void setRecords(List<AttendanceRecord> records) {
        this.records = records;
        notifyDataSetChanged();
    }

    class RecordViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvOfficeName;
        private final TextView tvCheckInTime;
        private final TextView tvCheckOutTime;
        private final TextView tvDuration;

        public RecordViewHolder(@NonNull View itemView) {
            super(itemView);
            tvOfficeName = itemView.findViewById(R.id.tvOfficeName);
            tvCheckInTime = itemView.findViewById(R.id.tvCheckInTime);
            tvCheckOutTime = itemView.findViewById(R.id.tvCheckOutTime);
            tvDuration = itemView.findViewById(R.id.tvDuration);
        }

        public void bind(AttendanceRecord record) {
            tvOfficeName.setText(record.officeName);
            tvCheckInTime.setText(formatDateTime(record.checkInTime));

            if (record.checkOutTime != null) {
                tvCheckOutTime.setText(formatDateTime(record.checkOutTime));
                tvDuration.setText(calculateDuration(record));
            } else {
                tvCheckOutTime.setText("--:--");
                tvDuration.setText("Active session");
            }
        }

        private String formatDateTime(String dateTime) {
            try {
                Date date = sdf.parse(dateTime);
                return displayFormat.format(date);
            } catch (ParseException e) {
                return dateTime;
            }
        }

        private String calculateDuration(AttendanceRecord record) {
            try {
                Date checkInDate = sdf.parse(record.checkInTime);
                Date checkOutDate = sdf.parse(record.checkOutTime);

                long duration = checkOutDate.getTime() - checkInDate.getTime();

                long hours = TimeUnit.MILLISECONDS.toHours(duration);
                long minutes = TimeUnit.MILLISECONDS.toMinutes(duration) % 60;

                return String.format(Locale.getDefault(), "%dh %02dm", hours, minutes);
            } catch (ParseException e) {
                return "N/A";
            }
        }
    }
}