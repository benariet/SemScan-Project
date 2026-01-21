package org.example.semscan.data.api;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.example.semscan.utils.Logger;
import org.example.semscan.utils.ServerLogger;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Response;

/**
 * OkHttp Interceptor that detects 401/403 responses and broadcasts a session expired event.
 * This allows the app to automatically redirect users to login when their session expires.
 */
public class AuthInterceptor implements Interceptor {

    public static final String ACTION_SESSION_EXPIRED = "org.example.semscan.SESSION_EXPIRED";
    private static final String TAG = "AuthInterceptor";

    private final Context context;

    public AuthInterceptor(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Response response = chain.proceed(chain.request());

        int statusCode = response.code();

        // Detect session expiration (401 Unauthorized or 403 Forbidden)
        if (statusCode == 401 || statusCode == 403) {
            String url = chain.request().url().toString();
            String logMessage = "Session expired detected - HTTP " + statusCode + " on " + url;
            Logger.w(TAG, logMessage);

            // Log to server for tracking authentication issues
            ServerLogger serverLogger = ServerLogger.getInstance(context);
            if (serverLogger != null) {
                serverLogger.w(ServerLogger.TAG_AUTH, logMessage);
            }

            // Broadcast session expired event
            Intent intent = new Intent(ACTION_SESSION_EXPIRED);
            intent.putExtra("status_code", statusCode);
            intent.putExtra("url", url);
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        }

        return response;
    }
}
