package com.example.geotracker;

import android.content.Context;
import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class FirestoreSyncHelper {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final Executor executor = Executors.newSingleThreadExecutor();

    public void syncRecords(Context context) {
        executor.execute(() -> {
            AppDatabase appDb = AppDatabase.getInstance(context);
            AttendanceDao dao = appDb.attendanceDao();
            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

            // Get only completed but unsynced records
            List<AttendanceRecord> unsyncedRecords = dao.getUnsyncedCompletedRecords();

            for (AttendanceRecord record : unsyncedRecords) {
                Map<String, Object> recordData = new HashMap<>();
                recordData.put("officeName", record.officeName);
                recordData.put("checkInTime", record.checkInTime);
                recordData.put("checkOutTime", record.checkOutTime);
                recordData.put("userId", userId);

                if (record.firestoreId == null) {
                    // New record - add to Firestore
                    db.collection("attendance")
                            .add(recordData)
                            .addOnSuccessListener(documentReference -> {
                                record.firestoreId = documentReference.getId();
                                record.synced = true;
                                new Thread(() -> {
                                    dao.update(record);
                                    Log.d("FirestoreSync", "Record synced: " + record.id);
                                }).start();
                            })
                            .addOnFailureListener(e ->
                                    Log.e("FirestoreSync", "Error adding document", e));
                } else {
                    // Existing record - update in Firestore
                    db.collection("attendance").document(record.firestoreId)
                            .set(recordData)
                            .addOnSuccessListener(aVoid -> {
                                record.synced = true;
                                new Thread(() -> {
                                    dao.update(record);
                                    Log.d("FirestoreSync", "Record updated: " + record.id);
                                }).start();
                            })
                            .addOnFailureListener(e ->
                                    Log.e("FirestoreSync", "Error updating document", e));
                }
            }
        });
    }
}