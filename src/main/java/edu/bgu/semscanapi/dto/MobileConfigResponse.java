package edu.bgu.semscanapi.dto;

import edu.bgu.semscanapi.entity.AppConfig;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mobile configuration response DTO
 * Transforms config list from database into flat structure for mobile app
 */
public class MobileConfigResponse {

    private String serverUrl;
    private String exportEmailRecipients;
    private String supportEmail;
    private String emailDomain;
    private String testEmailRecipient;
    private int connectionTimeoutSeconds;
    private int readTimeoutSeconds;
    private int writeTimeoutSeconds;
    private int manualAttendanceWindowBeforeMinutes;
    private int manualAttendanceWindowAfterMinutes;
    private int maxExportFileSizeMb;
    private int toastDurationError;
    private int toastDurationSuccess;
    private int toastDurationInfo;
    private int presenterSlotOpenWindowBeforeMinutes;
    private int presenterSlotOpenWindowAfterMinutes;
    private int studentAttendanceWindowBeforeMinutes;
    private int studentAttendanceWindowAfterMinutes;
    private int waitingListApprovalWindowHours;
    private int presenterCloseSessionDurationMinutes;
    private String emailFromName;
    private String emailReplyTo;
    private String emailBccList;
    private String appVersion;
    private int waitingListLimitPerSlot;
    private int phdCapacityWeight;

    public MobileConfigResponse() {
    }

    /**
     * Create response from list of AppConfig entities
     */
    public static MobileConfigResponse fromConfigList(List<AppConfig> configs) {
        MobileConfigResponse response = new MobileConfigResponse();

        // Convert list to map for easy lookup
        Map<String, String> configMap = configs.stream()
                .collect(Collectors.toMap(
                        AppConfig::getConfigKey,
                        c -> c.getConfigValue() != null ? c.getConfigValue() : "",
                        (existing, replacement) -> existing // keep first if duplicates
                ));

        // Map config keys to response fields
        response.serverUrl = configMap.getOrDefault("SERVER_URL", "");
        response.exportEmailRecipients = configMap.getOrDefault("EXPORT_EMAIL_RECIPIENTS", "");
        response.supportEmail = configMap.getOrDefault("SUPPORT_EMAIL", "benariet@bgu.ac.il");
        response.emailDomain = configMap.getOrDefault("EMAIL_DOMAIN", "@bgu.ac.il");
        response.testEmailRecipient = configMap.getOrDefault("TEST_EMAIL_RECIPIENT", "");
        response.connectionTimeoutSeconds = parseIntOrDefault(configMap.get("CONNECTION_TIMEOUT_SECONDS"), 30);
        response.readTimeoutSeconds = parseIntOrDefault(configMap.get("READ_TIMEOUT_SECONDS"), 30);
        response.writeTimeoutSeconds = parseIntOrDefault(configMap.get("WRITE_TIMEOUT_SECONDS"), 30);
        response.manualAttendanceWindowBeforeMinutes = parseIntOrDefault(configMap.get("MANUAL_ATTENDANCE_WINDOW_BEFORE_MINUTES"), 30);
        response.manualAttendanceWindowAfterMinutes = parseIntOrDefault(configMap.get("MANUAL_ATTENDANCE_WINDOW_AFTER_MINUTES"), 60);
        response.maxExportFileSizeMb = parseIntOrDefault(configMap.get("MAX_EXPORT_FILE_SIZE_MB"), 10);
        response.toastDurationError = parseIntOrDefault(configMap.get("TOAST_DURATION_ERROR"), 5000);
        response.toastDurationSuccess = parseIntOrDefault(configMap.get("TOAST_DURATION_SUCCESS"), 3000);
        response.toastDurationInfo = parseIntOrDefault(configMap.get("TOAST_DURATION_INFO"), 3000);
        response.presenterSlotOpenWindowBeforeMinutes = parseIntOrDefault(configMap.get("PRESENTER_SLOT_OPEN_WINDOW_BEFORE_MINUTES"), 30);
        response.presenterSlotOpenWindowAfterMinutes = parseIntOrDefault(configMap.get("PRESENTER_SLOT_OPEN_WINDOW_AFTER_MINUTES"), 15);
        response.studentAttendanceWindowBeforeMinutes = parseIntOrDefault(configMap.get("STUDENT_ATTENDANCE_WINDOW_BEFORE_MINUTES"), 5);
        response.studentAttendanceWindowAfterMinutes = parseIntOrDefault(configMap.get("STUDENT_ATTENDANCE_WINDOW_AFTER_MINUTES"), 10);
        response.waitingListApprovalWindowHours = parseIntOrDefault(configMap.get("WAITING_LIST_APPROVAL_WINDOW_HOURS"), 24);
        response.presenterCloseSessionDurationMinutes = parseIntOrDefault(configMap.get("PRESENTER_CLOSE_SESSION_DURATION_MINUTES"), 15);
        response.emailFromName = configMap.getOrDefault("EMAIL_FROM_NAME", "SemScan System");
        response.emailReplyTo = configMap.getOrDefault("EMAIL_REPLY_TO", "noreply@bgu.ac.il");
        response.emailBccList = configMap.getOrDefault("EMAIL_BCC_LIST", "");
        response.appVersion = configMap.getOrDefault("APP_VERSION", "1.0");
        response.waitingListLimitPerSlot = parseIntOrDefault(configMap.get("waiting.list.limit.per.slot"), 2);
        response.phdCapacityWeight = parseIntOrDefault(configMap.get("phd.capacity.weight"), 2);

        return response;
    }

    private static int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // Getters and setters
    public String getServerUrl() { return serverUrl; }
    public void setServerUrl(String serverUrl) { this.serverUrl = serverUrl; }

    public String getExportEmailRecipients() { return exportEmailRecipients; }
    public void setExportEmailRecipients(String exportEmailRecipients) { this.exportEmailRecipients = exportEmailRecipients; }

    public String getSupportEmail() { return supportEmail; }
    public void setSupportEmail(String supportEmail) { this.supportEmail = supportEmail; }

    public String getEmailDomain() { return emailDomain; }
    public void setEmailDomain(String emailDomain) { this.emailDomain = emailDomain; }

    public String getTestEmailRecipient() { return testEmailRecipient; }
    public void setTestEmailRecipient(String testEmailRecipient) { this.testEmailRecipient = testEmailRecipient; }

    public int getConnectionTimeoutSeconds() { return connectionTimeoutSeconds; }
    public void setConnectionTimeoutSeconds(int connectionTimeoutSeconds) { this.connectionTimeoutSeconds = connectionTimeoutSeconds; }

    public int getReadTimeoutSeconds() { return readTimeoutSeconds; }
    public void setReadTimeoutSeconds(int readTimeoutSeconds) { this.readTimeoutSeconds = readTimeoutSeconds; }

    public int getWriteTimeoutSeconds() { return writeTimeoutSeconds; }
    public void setWriteTimeoutSeconds(int writeTimeoutSeconds) { this.writeTimeoutSeconds = writeTimeoutSeconds; }

    public int getManualAttendanceWindowBeforeMinutes() { return manualAttendanceWindowBeforeMinutes; }
    public void setManualAttendanceWindowBeforeMinutes(int manualAttendanceWindowBeforeMinutes) { this.manualAttendanceWindowBeforeMinutes = manualAttendanceWindowBeforeMinutes; }

    public int getManualAttendanceWindowAfterMinutes() { return manualAttendanceWindowAfterMinutes; }
    public void setManualAttendanceWindowAfterMinutes(int manualAttendanceWindowAfterMinutes) { this.manualAttendanceWindowAfterMinutes = manualAttendanceWindowAfterMinutes; }

    public int getMaxExportFileSizeMb() { return maxExportFileSizeMb; }
    public void setMaxExportFileSizeMb(int maxExportFileSizeMb) { this.maxExportFileSizeMb = maxExportFileSizeMb; }

    public int getToastDurationError() { return toastDurationError; }
    public void setToastDurationError(int toastDurationError) { this.toastDurationError = toastDurationError; }

    public int getToastDurationSuccess() { return toastDurationSuccess; }
    public void setToastDurationSuccess(int toastDurationSuccess) { this.toastDurationSuccess = toastDurationSuccess; }

    public int getToastDurationInfo() { return toastDurationInfo; }
    public void setToastDurationInfo(int toastDurationInfo) { this.toastDurationInfo = toastDurationInfo; }

    public int getPresenterSlotOpenWindowBeforeMinutes() { return presenterSlotOpenWindowBeforeMinutes; }
    public void setPresenterSlotOpenWindowBeforeMinutes(int presenterSlotOpenWindowBeforeMinutes) { this.presenterSlotOpenWindowBeforeMinutes = presenterSlotOpenWindowBeforeMinutes; }

    public int getPresenterSlotOpenWindowAfterMinutes() { return presenterSlotOpenWindowAfterMinutes; }
    public void setPresenterSlotOpenWindowAfterMinutes(int presenterSlotOpenWindowAfterMinutes) { this.presenterSlotOpenWindowAfterMinutes = presenterSlotOpenWindowAfterMinutes; }

    public int getStudentAttendanceWindowBeforeMinutes() { return studentAttendanceWindowBeforeMinutes; }
    public void setStudentAttendanceWindowBeforeMinutes(int studentAttendanceWindowBeforeMinutes) { this.studentAttendanceWindowBeforeMinutes = studentAttendanceWindowBeforeMinutes; }

    public int getStudentAttendanceWindowAfterMinutes() { return studentAttendanceWindowAfterMinutes; }
    public void setStudentAttendanceWindowAfterMinutes(int studentAttendanceWindowAfterMinutes) { this.studentAttendanceWindowAfterMinutes = studentAttendanceWindowAfterMinutes; }

    public int getWaitingListApprovalWindowHours() { return waitingListApprovalWindowHours; }
    public void setWaitingListApprovalWindowHours(int waitingListApprovalWindowHours) { this.waitingListApprovalWindowHours = waitingListApprovalWindowHours; }

    public int getPresenterCloseSessionDurationMinutes() { return presenterCloseSessionDurationMinutes; }
    public void setPresenterCloseSessionDurationMinutes(int presenterCloseSessionDurationMinutes) { this.presenterCloseSessionDurationMinutes = presenterCloseSessionDurationMinutes; }

    public String getEmailFromName() { return emailFromName; }
    public void setEmailFromName(String emailFromName) { this.emailFromName = emailFromName; }

    public String getEmailReplyTo() { return emailReplyTo; }
    public void setEmailReplyTo(String emailReplyTo) { this.emailReplyTo = emailReplyTo; }

    public String getEmailBccList() { return emailBccList; }
    public void setEmailBccList(String emailBccList) { this.emailBccList = emailBccList; }

    public String getAppVersion() { return appVersion; }
    public void setAppVersion(String appVersion) { this.appVersion = appVersion; }

    public int getWaitingListLimitPerSlot() { return waitingListLimitPerSlot; }
    public void setWaitingListLimitPerSlot(int waitingListLimitPerSlot) { this.waitingListLimitPerSlot = waitingListLimitPerSlot; }

    public int getPhdCapacityWeight() { return phdCapacityWeight; }
    public void setPhdCapacityWeight(int phdCapacityWeight) { this.phdCapacityWeight = phdCapacityWeight; }
}
