package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.config.GlobalConfig;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for saving export files locally
 */
@Service
public class FileUploadService {

    private static final Logger logger = LoggerUtil.getLogger(FileUploadService.class);
    private static final String EXPORT_DIR = "/opt/semscan-api/exports";

    private final RestTemplate restTemplate;
    private final GlobalConfig globalConfig;
    private final DatabaseLoggerService databaseLoggerService;

    @Autowired
    public FileUploadService(RestTemplateBuilder restTemplateBuilder,
                            GlobalConfig globalConfig,
                            DatabaseLoggerService databaseLoggerService) {
        this.restTemplate = restTemplateBuilder.build();
        this.globalConfig = globalConfig;
        this.databaseLoggerService = databaseLoggerService;

        // Ensure export directory exists
        try {
            Path exportPath = Paths.get(EXPORT_DIR);
            if (!Files.exists(exportPath)) {
                Files.createDirectories(exportPath);
                logger.info("Created export directory: {}", EXPORT_DIR);
            }
        } catch (IOException e) {
            logger.warn("Could not create export directory: {} - {}", EXPORT_DIR, e.getMessage());
        }
    }

    /**
     * Save a file locally to the exports directory
     */
    public UploadResult uploadFile(byte[] fileData, String filename, String contentType, Long sessionId) {
        logger.info("Saving export file locally - filename: {}, size: {} bytes, sessionId: {}",
            filename, fileData.length, sessionId);

        try {
            // Ensure export directory exists
            Path exportPath = Paths.get(EXPORT_DIR);
            if (!Files.exists(exportPath)) {
                Files.createDirectories(exportPath);
            }

            // Save file locally
            Path filePath = exportPath.resolve(filename);
            Files.write(filePath, fileData);

            String successMsg = String.format(
                "File saved successfully - filename: %s, path: %s, sessionId: %s, size: %d bytes",
                filename, filePath.toString(), sessionId, fileData.length
            );
            logger.info(successMsg);

            // Log successful save to database
            databaseLoggerService.logBusinessEvent("FILE_SAVE_SUCCESS", successMsg,
                String.format("filename=%s,sessionId=%s,fileSize=%d,path=%s",
                    filename, sessionId, fileData.length, filePath.toString()));

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("success", true);
            responseBody.put("filename", filename);
            responseBody.put("path", filePath.toString());
            responseBody.put("size", fileData.length);

            return new UploadResult(true, 200, "File saved successfully", responseBody, null);

        } catch (IOException e) {
            String errorMsg = String.format(
                "File save failed - IO error: filename: %s, sessionId: %s, message: %s",
                filename, sessionId, e.getMessage()
            );
            logger.error(errorMsg, e);

            databaseLoggerService.logError("FILE_SAVE_IO_ERROR", errorMsg, e, null,
                String.format("filename=%s,sessionId=%s", filename, sessionId));

            return new UploadResult(false, 500, "IO error: " + e.getMessage(), null, e);

        } catch (Exception e) {
            String errorMsg = String.format(
                "File save failed - unexpected error: filename: %s, sessionId: %s",
                filename, sessionId
            );
            logger.error(errorMsg, e);

            databaseLoggerService.logError("FILE_SAVE_ERROR", errorMsg, e, null,
                String.format("filename=%s,sessionId=%s,exceptionType=%s",
                    filename, sessionId, e.getClass().getName()));

            return new UploadResult(false, 500, "Unexpected error: " + e.getMessage(), null, e);
        }
    }

    /**
     * Verify if a file exists locally
     */
    public boolean verifyFileExists(String filename, Long sessionId) {
        try {
            Path filePath = Paths.get(EXPORT_DIR).resolve(filename);
            boolean exists = Files.exists(filePath);
            logger.info("File verification - filename: {}, exists: {}", filename, exists);
            return exists;
        } catch (Exception e) {
            logger.error("File verification error - filename: {}", filename, e);
            return false;
        }
    }

    /**
     * Result class for upload operations
     */
    public static class UploadResult {
        private final boolean success;
        private final int statusCode;
        private final String message;
        private final Map<String, Object> responseBody;
        private final Exception exception;

        public UploadResult(boolean success, int statusCode, String message,
                          Map<String, Object> responseBody, Exception exception) {
            this.success = success;
            this.statusCode = statusCode;
            this.message = message;
            this.responseBody = responseBody;
            this.exception = exception;
        }

        public boolean isSuccess() { return success; }
        public int getStatusCode() { return statusCode; }
        public String getMessage() { return message; }
        public Map<String, Object> getResponseBody() { return responseBody; }
        public Exception getException() { return exception; }
    }
}
