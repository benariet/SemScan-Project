package org.example.semscan.utils;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.os.Handler;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.example.semscan.ui.auth.LoginActivity;

/**
 * Global crash handler to prevent app crashes and provide graceful recovery.
 * Catches uncaught exceptions and attempts to recover or restart gracefully.
 */
public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static final String TAG = "CrashHandler";
    private static CrashHandler instance;
    private final Context applicationContext;
    private final Thread.UncaughtExceptionHandler defaultHandler;
    private Activity currentActivity;

    private CrashHandler(Context context) {
        this.applicationContext = context.getApplicationContext();
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    /**
     * Initialize the crash handler. Call this in Application.onCreate()
     */
    public static synchronized void init(Application application) {
        if (instance == null) {
            instance = new CrashHandler(application);
            Thread.setDefaultUncaughtExceptionHandler(instance);

            // Track current activity for better crash recovery
            application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}

                @Override
                public void onActivityStarted(@NonNull Activity activity) {}

                @Override
                public void onActivityResumed(@NonNull Activity activity) {
                    instance.currentActivity = activity;
                }

                @Override
                public void onActivityPaused(@NonNull Activity activity) {
                    if (instance.currentActivity == activity) {
                        instance.currentActivity = null;
                    }
                }

                @Override
                public void onActivityStopped(@NonNull Activity activity) {}

                @Override
                public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

                @Override
                public void onActivityDestroyed(@NonNull Activity activity) {}
            });

            Logger.i(TAG, "CrashHandler initialized");
        }
    }

    @Override
    public void uncaughtException(@NonNull Thread thread, @NonNull Throwable throwable) {
        try {
            // Log the crash
            Logger.e(TAG, "UNCAUGHT EXCEPTION on thread " + thread.getName(), throwable);

            // Try to show a toast on UI thread
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    Toast.makeText(applicationContext,
                        "An error occurred. Restarting...",
                        Toast.LENGTH_LONG).show();
                } catch (Exception ignored) {}
            });

            // Give time for toast and logging
            Thread.sleep(1500);

            // Try to restart the app gracefully
            restartApp();

        } catch (Exception e) {
            Logger.e(TAG, "Error in crash handler", e);
        } finally {
            // If all else fails, let the default handler take over
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            } else {
                System.exit(1);
            }
        }
    }

    private void restartApp() {
        try {
            Intent intent = new Intent(applicationContext, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            applicationContext.startActivity(intent);

            // Kill the current process
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(0);
        } catch (Exception e) {
            Logger.e(TAG, "Failed to restart app", e);
        }
    }

    /**
     * Get the currently visible activity (if any)
     */
    @Nullable
    public static Activity getCurrentActivity() {
        return instance != null ? instance.currentActivity : null;
    }
}
