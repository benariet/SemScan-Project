package org.example.semscan;

import android.app.Application;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.ProcessLifecycleOwner;

import org.example.semscan.utils.CrashHandler;
import org.example.semscan.utils.PreferencesManager;
import org.example.semscan.utils.Logger;
import org.example.semscan.utils.ServerLogger;
import org.example.semscan.utils.ConfigManager;

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

        // Register lifecycle observer for proper resource cleanup
        // This is more reliable than onTerminate() which is rarely called on real devices
        ProcessLifecycleOwner.get().getLifecycle().addObserver(
            (LifecycleEventObserver) (source, event) -> {
                if (event == Lifecycle.Event.ON_STOP) {
                    // App went to background - flush logs but don't shutdown completely
                    // (user might return to the app)
                    ServerLogger serverLogger = ServerLogger.getInstance(this);
                    if (serverLogger != null) {
                        serverLogger.flushLogs();
                    }
                } else if (event == Lifecycle.Event.ON_DESTROY) {
                    // App is being destroyed - full cleanup
                    shutdownResources();
                }
            }
        );
    }

    /**
     * Clean up resources when the app is being destroyed.
     * This properly shuts down executors and unregisters listeners to prevent memory leaks.
     */
    private void shutdownResources() {
        try {
            ServerLogger serverLogger = ServerLogger.getInstance(this);
            if (serverLogger != null) {
                serverLogger.shutdown();
            }
        } catch (Exception e) {
            Logger.e(Logger.TAG_API, "Error shutting down ServerLogger: " + e.getMessage());
        }

        try {
            ConfigManager configManager = ConfigManager.getInstance(this);
            if (configManager != null) {
                configManager.shutdown();
            }
        } catch (Exception e) {
            Logger.e(Logger.TAG_API, "Error shutting down ConfigManager: " + e.getMessage());
        }
    }
}
