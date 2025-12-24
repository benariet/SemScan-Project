package org.example.semscan;

import android.app.Application;

import org.example.semscan.utils.CrashHandler;
import org.example.semscan.utils.PreferencesManager;
import org.example.semscan.utils.Logger;

public class SemScanApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize global crash handler FIRST - catches all uncaught exceptions
        CrashHandler.init(this);

        // Initialize preferences manager
        PreferencesManager.getInstance(this);

        // Initialize global logger forwarding to server
        Logger.init(this);
    }
}
