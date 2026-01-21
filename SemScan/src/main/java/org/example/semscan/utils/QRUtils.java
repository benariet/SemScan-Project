package org.example.semscan.utils;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import org.example.semscan.data.model.QRPayload;

public class QRUtils {
    private static final Gson gson = new Gson();
    
    /**
     * Parse QR code content to QRPayload
     * @param qrContent The content scanned from QR code
     * @return QRPayload object or null if parsing fails
     */
    public static QRPayload parseQRContent(String qrContent) {
        try {
            return gson.fromJson(qrContent, QRPayload.class);
        } catch (JsonSyntaxException e) {
            return null;
        }
    }
    
    /**
     * Generate QR code content from session ID
     * @param sessionId The session ID
     * @return JSON string for QR code
     */
    public static String generateQRContent(Long sessionId) {
        QRPayload payload = new QRPayload(sessionId);
        return gson.toJson(payload);
    }
    
    /**
     * Validate if QR content is valid
     * @param qrContent The content to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidQRContent(String qrContent) {
        QRPayload payload = parseQRContent(qrContent);
        return payload != null && payload.getSessionId() != null;
    }
}
