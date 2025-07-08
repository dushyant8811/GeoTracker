package com.example.geotracker;

import android.content.Context;
import android.util.Log;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class FirestoreSyncHelper {
    private static final String TAG = "FirestoreSyncHelper";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final Executor executor = Executors.newSingleThreadExecutor();

    public void syncRecords(Context context) {
        executor.execute(() -> {
            AppDatabase appDb = AppDatabase.getInstance(context);
            AttendanceDao dao = appDb.attendanceDao();

            // Get only completed but unsynced records
            List<AttendanceRecord> unsyncedRecords = dao.getUnsyncedCompletedRecords();

            if (unsyncedRecords.isEmpty()) {
                Log.d(TAG, "No new records to sync.");
                return;
            }

            Log.d(TAG, "Found " + unsyncedRecords.size() + " records to sync.");

            for (AttendanceRecord record : unsyncedRecords) {
                // Use the userId stored in the record, which is safer for background tasks.
                if (record.userId == null || record.userId.isEmpty()) {
                    Log.w(TAG, "Skipping record with no user ID: " + record.id);
                    continue;
                }

                Map<String, Object> recordData = new HashMap<>();
                recordData.put("officeName", record.officeName);
                recordData.put("checkInTime", record.checkInTime);
                recordData.put("checkOutTime", record.checkOutTime);
                recordData.put("userId", record.userId);

                // Use the record's firestoreId if it exists, otherwise it's a new document
                String documentId = record.firestoreId;

                if (documentId == null) {
                    // New record - add to Firestore
                    db.collection("attendance")
                            .add(recordData)
                            .addOnSuccessListener(documentReference -> {
                                Log.d(TAG, "Record successfully added to Firestore with ID: " + documentReference.getId());
                                // Update the local record with the new Firestore ID and set synced flag
                                record.firestoreId = documentReference.getId();
                                record.synced = true;
                                executor.execute(() -> dao.update(record));
                            })
                            .addOnFailureListener(e ->
                                    Log.e(TAG, "Error adding document for record ID " + record.id, e));
                } else {
                    db.collection("attendance").document(documentId)
                            .set(recordData)
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "Record successfully updated in Firestore: " + documentId);
                                record.synced = true;
                                executor.execute(() -> dao.update(record));
                            })
                            .addOnFailureListener(e ->
                                    Log.e(TAG, "Error updating document " + documentId, e));
                }
            }
        });
    }
}