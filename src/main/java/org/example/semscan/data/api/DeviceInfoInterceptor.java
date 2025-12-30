package org.example.semscan.data.api;

import android.content.Context;
import android.os.Build;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * OkHttp interceptor that adds device info headers to all requests.
 * This allows the server to log device information for debugging.
 */
public class DeviceInfoInterceptor implements Interceptor {

    private final Context context;

    public DeviceInfoInterceptor(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request originalRequest = chain.request();

        // Build device info string: "Manufacturer Model (Android Version)"
        String deviceInfo = String.format("%s %s (Android %s, SDK %d)",
                Build.MANUFACTURER,
                Build.MODEL,
                Build.VERSION.RELEASE,
                Build.VERSION.SDK_INT);

        // Get app version
        String appVersion = getAppVersion();

        // Add headers to request
        Request.Builder requestBuilder = originalRequest.newBuilder()
                .header("X-Device-Info", deviceInfo);

        if (appVersion != null) {
            requestBuilder.header("X-App-Version", appVersion);
        }

        return chain.proceed(requestBuilder.build());
    }

    private String getAppVersion() {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0)
                    .versionName;
        } catch (Exception e) {
            return null;
        }
    }
}
