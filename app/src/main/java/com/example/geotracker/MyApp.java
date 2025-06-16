package com.example.geotracker;

import android.app.Application;
import com.google.android.gms.security.ProviderInstaller;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;

public class MyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        installTlsPatch();
    }

    private void installTlsPatch() {
        try {
            ProviderInstaller.installIfNeeded(this);
        } catch (GooglePlayServicesRepairableException e) {
            e.printStackTrace();
        } catch (GooglePlayServicesNotAvailableException e) {
            e.printStackTrace();
        }
    }
}