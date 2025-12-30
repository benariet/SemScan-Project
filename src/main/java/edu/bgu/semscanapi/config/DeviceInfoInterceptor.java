package edu.bgu.semscanapi.config;

import edu.bgu.semscanapi.service.DatabaseLoggerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * Interceptor to extract device info from request headers and store in thread-local
 * for database logging. Expects headers:
 * - User-Agent: Standard browser/app user agent
 * - X-Device-Info: Custom device info (model, OS version)
 * - X-App-Version: Mobile app version
 */
@Component
public class DeviceInfoInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Extract device info from headers
        String userAgent = request.getHeader("User-Agent");
        String deviceInfo = request.getHeader("X-Device-Info");
        String appVersion = request.getHeader("X-App-Version");

        // Build device info string
        StringBuilder deviceBuilder = new StringBuilder();
        if (deviceInfo != null && !deviceInfo.isBlank()) {
            deviceBuilder.append(deviceInfo);
        } else if (userAgent != null && !userAgent.isBlank()) {
            // Parse useful info from User-Agent
            deviceBuilder.append(parseUserAgent(userAgent));
        }

        // Set in thread-local for logging
        String finalDeviceInfo = deviceBuilder.length() > 0 ? deviceBuilder.toString() : null;
        DatabaseLoggerService.setDeviceInfo(finalDeviceInfo, appVersion);

        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {
        // Nothing to do here
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // Clear thread-local to prevent memory leaks
        DatabaseLoggerService.clearDeviceInfo();
    }

    /**
     * Parse useful device info from User-Agent header
     */
    private String parseUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "";
        }

        // Check for Android
        if (userAgent.contains("Android")) {
            // Try to extract Android version and device model
            // Format: "Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit..."
            int androidIndex = userAgent.indexOf("Android");
            int closeParen = userAgent.indexOf(")", androidIndex);
            if (closeParen > androidIndex) {
                String androidPart = userAgent.substring(androidIndex, closeParen);
                return androidPart.trim();
            }
            return "Android";
        }

        // Check for iOS
        if (userAgent.contains("iPhone") || userAgent.contains("iPad")) {
            // Try to extract iOS version
            // Format: "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X)..."
            int iosIndex = userAgent.indexOf("CPU");
            if (iosIndex > 0) {
                int closeParen = userAgent.indexOf(")", iosIndex);
                if (closeParen > iosIndex) {
                    String iosPart = userAgent.substring(iosIndex, closeParen);
                    if (userAgent.contains("iPhone")) {
                        return "iPhone " + iosPart.replace("CPU ", "").replace("iPhone OS ", "iOS ").replace("_", ".");
                    } else {
                        return "iPad " + iosPart.replace("CPU ", "").replace("iPad OS ", "iPadOS ").replace("_", ".");
                    }
                }
            }
            return userAgent.contains("iPhone") ? "iPhone" : "iPad";
        }

        // For other user agents, just return first 100 chars
        return userAgent.length() > 100 ? userAgent.substring(0, 100) : userAgent;
    }
}
