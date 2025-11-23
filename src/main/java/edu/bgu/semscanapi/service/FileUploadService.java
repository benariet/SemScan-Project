package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.config.GlobalConfig;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for uploading files to external server
 * Handles file uploads and verifies upload success
 */
@Service
public class FileUploadService {

    private static final Logger logger = LoggerUtil.getLogger(FileUploadService.class);

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
    }

    /**
     * Upload a file to the configured upload server
     * 
     * @param fileData The file data as byte array
     * @param filename The filename
     * @param contentType The content type (e.g., "text/csv", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
     * @param sessionId Optional session ID for tracking
     * @return UploadResult containing success status and response details
     */
    public UploadResult uploadFile(byte[] fileData, String filename, String contentType, Long sessionId) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        String uploadUrl = globalConfig.getUploadServerUrl();
        
        logger.info("Uploading file to server: {} - filename: {}, size: {} bytes, sessionId: {}", 
            uploadUrl, filename, fileData.length, sessionId);
        
        try {
            // Create multipart form data
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            
            // Add file as multipart file
            org.springframework.core.io.ByteArrayResource fileResource = 
                new org.springframework.core.io.ByteArrayResource(fileData) {
                    @Override
                    public String getFilename() {
                        return filename;
                    }
                };
            
            body.add("file", fileResource);
            body.add("filename", filename);
            if (sessionId != null) {
                body.add("sessionId", sessionId.toString());
            }
            
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            
            // Upload file
            ResponseEntity<Map> response = restTemplate.exchange(
                uploadUrl,
                HttpMethod.POST,
                requestEntity,
                Map.class
            );
            
            // Check if upload was successful
            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> responseBody = response.getBody();
                String successMsg = String.format(
                    "File uploaded successfully to server: %s - filename: %s, sessionId: %s, response: %s",
                    uploadUrl, filename, sessionId, responseBody
                );
                logger.info(successMsg);
                
                // Log successful upload to database
                databaseLoggerService.logBusinessEvent("FILE_UPLOAD_SUCCESS", 
                    successMsg, 
                    String.format("uploadUrl=%s,filename=%s,sessionId=%s,fileSize=%d,statusCode=%d", 
                        uploadUrl, filename, sessionId, fileData.length, response.getStatusCodeValue()));
                
                return new UploadResult(true, response.getStatusCodeValue(), 
                    "File uploaded successfully", responseBody, null);
            } else {
                String errorMsg = String.format(
                    "File upload failed - unexpected status code: %d, filename: %s, sessionId: %s",
                    response.getStatusCodeValue(), filename, sessionId
                );
                logger.warn(errorMsg);
                
                databaseLoggerService.logError("FILE_UPLOAD_FAILED", errorMsg, null, null,
                    String.format("uploadUrl=%s,filename=%s,sessionId=%s,statusCode=%d", 
                        uploadUrl, filename, sessionId, response.getStatusCodeValue()));
                
                return new UploadResult(false, response.getStatusCodeValue(), 
                    "Upload failed with status: " + response.getStatusCode(), null, null);
            }
            
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            String errorMsg = String.format(
                "File upload failed - HTTP error: %d, filename: %s, sessionId: %s, message: %s",
                e.getStatusCode().value(), filename, sessionId, e.getMessage()
            );
            logger.error(errorMsg, e);
            
            databaseLoggerService.logError("FILE_UPLOAD_HTTP_ERROR", errorMsg, e, null,
                String.format("uploadUrl=%s,filename=%s,sessionId=%s,statusCode=%d,responseBody=%s", 
                    uploadUrl, filename, sessionId, e.getStatusCode().value(), e.getResponseBodyAsString()));
            
            return new UploadResult(false, e.getStatusCode().value(), 
                "HTTP error: " + e.getMessage(), null, e);
            
        } catch (RestClientException e) {
            String errorMsg = String.format(
                "File upload failed - connection error: filename: %s, sessionId: %s, message: %s",
                filename, sessionId, e.getMessage()
            );
            logger.error(errorMsg, e);
            
            databaseLoggerService.logError("FILE_UPLOAD_CONNECTION_ERROR", errorMsg, e, null,
                String.format("uploadUrl=%s,filename=%s,sessionId=%s", uploadUrl, filename, sessionId));
            
            return new UploadResult(false, 0, 
                "Connection error: " + e.getMessage(), null, e);
            
        } catch (Exception e) {
            String errorMsg = String.format(
                "File upload failed - unexpected error: filename: %s, sessionId: %s",
                filename, sessionId
            );
            logger.error(errorMsg, e);
            
            databaseLoggerService.logError("FILE_UPLOAD_ERROR", errorMsg, e, null,
                String.format("uploadUrl=%s,filename=%s,sessionId=%s,exceptionType=%s", 
                    uploadUrl, filename, sessionId, e.getClass().getName()));
            
            return new UploadResult(false, 0, 
                "Unexpected error: " + e.getMessage(), null, e);
        } finally {
            LoggerUtil.clearContext();
        }
    }

    /**
     * Verify if a file exists on the upload server
     * 
     * @param filename The filename to check
     * @param sessionId Optional session ID
     * @return true if file exists, false otherwise
     */
    public boolean verifyFileExists(String filename, Long sessionId) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        String uploadUrl = globalConfig.getUploadServerUrl();
        String verifyUrl = uploadUrl + "/verify?filename=" + filename;
        if (sessionId != null) {
            verifyUrl += "&sessionId=" + sessionId;
        }
        
        logger.info("Verifying file existence on server: {} - filename: {}, sessionId: {}", 
            verifyUrl, filename, sessionId);
        
        try {
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<String> requestEntity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                verifyUrl,
                HttpMethod.GET,
                requestEntity,
                Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> responseBody = response.getBody();
                boolean exists = responseBody != null && 
                    Boolean.TRUE.equals(responseBody.get("exists"));
                
                logger.info("File verification result - filename: {}, exists: {}", filename, exists);
                
                databaseLoggerService.logAction("INFO", "FILE_VERIFY", 
                    String.format("File verification - filename: %s, exists: %s", filename, exists),
                    null, String.format("filename=%s,sessionId=%s,exists=%s", filename, sessionId, exists));
                
                return exists;
            } else {
                logger.warn("File verification failed - status: {}, filename: {}", 
                    response.getStatusCode(), filename);
                return false;
            }
            
        } catch (Exception e) {
            logger.error("File verification error - filename: {}", filename, e);
            databaseLoggerService.logError("FILE_VERIFY_ERROR", 
                String.format("File verification error - filename: %s", filename), e, null,
                String.format("filename=%s,sessionId=%s", filename, sessionId));
            return false;
        } finally {
            LoggerUtil.clearContext();
        }
    }

    /**
     * Result class for file upload operations
     */
    public static class UploadResult {
        private final boolean success;
        private final int statusCode;
        private final String message;
        private final Map<String, Object> responseBody;
        private final Exception exception;
        private final LocalDateTime timestamp;

        public UploadResult(boolean success, int statusCode, String message, 
                          Map<String, Object> responseBody, Exception exception) {
            this.success = success;
            this.statusCode = statusCode;
            this.message = message;
            this.responseBody = responseBody;
            this.exception = exception;
            this.timestamp = LocalDateTime.now();
        }

        public boolean isSuccess() {
            return success;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getMessage() {
            return message;
        }

        public Map<String, Object> getResponseBody() {
            return responseBody;
        }

        public Exception getException() {
            return exception;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }
    }
}

