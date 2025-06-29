package com.example.geotracker;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "attendance_records")
public class AttendanceRecord {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    public String officeName = "Headquarters"; // Default for testing
    public String checkInTime;
    public String checkOutTime;
    public boolean synced = false; // For Firestore sync later

    public AttendanceRecord(String officeName, String checkInTime) {
        if (officeName != null) this.officeName = officeName;
        this.checkInTime = checkInTime;
    }
}