package com.example.geotracker;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "attendance_records")
public class AttendanceRecord {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    public String officeName = "Headquarters";
    public String checkInTime;
    public String checkOutTime;
    public boolean synced = false;
    public String firestoreId; // Add this
    public String userId;
    public boolean completed = false;// Add this

    public AttendanceRecord(String officeName, String checkInTime) {
        if (officeName != null) this.officeName = officeName;
        this.checkInTime = checkInTime;
    }
}