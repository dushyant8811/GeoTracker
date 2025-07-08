package com.example.geotracker;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface AttendanceDao {
    @Insert
    void insert(AttendanceRecord record);

    @Update
    void update(AttendanceRecord record);

    @Query("SELECT * FROM attendance_records WHERE checkOutTime IS NULL LIMIT 1")
    AttendanceRecord getActiveRecord();

    @Query("SELECT * FROM attendance_records ORDER BY id DESC")
    List<AttendanceRecord> getAllRecords();


    @Query("SELECT * FROM attendance_records WHERE completed = 1 AND synced = 0")
    List<AttendanceRecord> getUnsyncedCompletedRecords();

    @Query("SELECT * FROM attendance_records WHERE completed = 1 ORDER BY id DESC")
    List<AttendanceRecord> getCompletedRecords();

}
