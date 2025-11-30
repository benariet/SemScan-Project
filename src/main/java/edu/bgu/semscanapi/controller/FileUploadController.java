package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.dto.ErrorResponse;
import edu.bgu.semscanapi.service.DatabaseLoggerService;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for file upload operations
 * Handles file uploads from the backend service
 */
@RestController
@RequestMapping("/api/v1/upload")
@CrossOrigin(origins = "*")
public class FileUploadController {

    private static final Logger logger = LoggerUtil.getLogger(FileUploadController.class);
    
    private static final String EXPORTS_DIR = "/opt/semscan-api/exports";
    
    @Autowired
    private DatabaseLoggerService databaseLoggerService;

    /**
     * Upload a file
     * POST /api/v1/upload
     * 
     * Expected multipart form data:
     * - file: The file to upload
     * - filename: Optional filename override
     * - sessionId: Optional session ID for tracking
     */
    @PostMapping
    public ResponseEntity<Object> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "filename", required = false) String filename,
            @RequestParam(value = "sessionId", required = false) Long sessionId) {
        
        LoggerUtil.generateAndSetCorrelationId();
        logger.info("File upload request - filename: {}, size: {} bytes, sessionId: {}", 
            filename != null ? filename : file.getOriginalFilename(), 
            file.getSize(), 
            sessionId);
        LoggerUtil.logApiRequest(logger, "POST", "/api/v1/upload", 
            String.format("filename=%s,size=%d,sessionId=%s", 
                filename != null ? filename : file.getOriginalFilename(), 
                file.getSize(), 
                sessionId));

        try {
            // Validate file
            if (file.isEmpty()) {
                ErrorResponse errorResponse = new ErrorResponse(
                    "File is empty",
                    "Bad Request",
                    400,
                    "/api/v1/upload");
                LoggerUtil.logApiResponse(logger, "POST", "/api/v1/upload", 400, "File is empty");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
            }

            // Determine filename
            String finalFilename = filename != null ? filename : file.getOriginalFilename();
            if (finalFilename == null || finalFilename.isEmpty()) {
                finalFilename = "upload_" + System.currentTimeMillis();
            }

            // Ensure exports directory exists
            Path exportsPath = Paths.get(EXPORTS_DIR);
            if (!Files.exists(exportsPath)) {
                Files.createDirectories(exportsPath);
                logger.info("Created exports directory: {}", exportsPath.toAbsolutePath());
            }

            // Save file
            Path filePath = exportsPath.resolve(finalFilename);
            Files.write(filePath, file.getBytes());
            
            logger.info("File saved successfully: {} ({} bytes)", filePath.toAbsolutePath(), file.getSize());
            
            // Log successful upload to database
            databaseLoggerService.logBusinessEvent("FILE_UPLOAD_SUCCESS", 
                String.format("File uploaded successfully - filename: %s, size: %d bytes, sessionId: %s", 
                    finalFilename, file.getSize(), sessionId),
                String.format("filename=%s,fileSize=%d,sessionId=%s,filePath=%s", 
                    finalFilename, file.getSize(), sessionId, filePath.toAbsolutePath()));

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "File uploaded successfully");
            response.put("filename", finalFilename);
            response.put("size", file.getSize());
            response.put("path", filePath.toAbsolutePath().toString());
            if (sessionId != null) {
                response.put("sessionId", sessionId);
            }

            LoggerUtil.logApiResponse(logger, "POST", "/api/v1/upload", 200, 
                "File uploaded successfully - " + finalFilename);
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            String errorMsg = String.format("Failed to save file: %s", e.getMessage());
            logger.error(errorMsg, e);
            LoggerUtil.logError(logger, "File upload failed", e);
            LoggerUtil.logApiResponse(logger, "POST", "/api/v1/upload", 500, "Internal Server Error");
            
            // Log error to database
            databaseLoggerService.logError("FILE_UPLOAD_ERROR", errorMsg, e, null,
                String.format("filename=%s,sessionId=%s,exceptionType=%s", 
                    filename != null ? filename : file.getOriginalFilename(), 
                    sessionId, 
                    e.getClass().getName()));

            ErrorResponse errorResponse = new ErrorResponse(
                "Failed to upload file: " + e.getMessage(),
                "Internal Server Error",
                500,
                "/api/v1/upload");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } catch (Exception e) {
            String errorMsg = String.format("Unexpected error during file upload: %s", e.getMessage());
            logger.error(errorMsg, e);
            LoggerUtil.logError(logger, "File upload failed", e);
            LoggerUtil.logApiResponse(logger, "POST", "/api/v1/upload", 500, "Internal Server Error");
            
            // Log error to database
            databaseLoggerService.logError("FILE_UPLOAD_ERROR", errorMsg, e, null,
                String.format("filename=%s,sessionId=%s,exceptionType=%s", 
                    filename != null ? filename : file.getOriginalFilename(), 
                    sessionId, 
                    e.getClass().getName()));

            ErrorResponse errorResponse = new ErrorResponse(
                "An unexpected error occurred while uploading file",
                "Internal Server Error",
                500,
                "/api/v1/upload");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }

    /**
     * Verify if a file exists
     * GET /api/v1/upload/verify?filename=xxx&sessionId=123
     */
    @GetMapping("/verify")
    public ResponseEntity<Object> verifyFile(
            @RequestParam("filename") String filename,
            @RequestParam(value = "sessionId", required = false) Long sessionId) {
        
        LoggerUtil.generateAndSetCorrelationId();
        logger.info("File verification request - filename: {}, sessionId: {}", filename, sessionId);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/upload/verify", 
            String.format("filename=%s,sessionId=%s", filename, sessionId));

        try {
            Path filePath = Paths.get(EXPORTS_DIR, filename);
            boolean exists = Files.exists(filePath) && Files.isRegularFile(filePath);
            
            logger.info("File verification result - filename: {}, exists: {}", filename, exists);
            
            // Log verification to database
            databaseLoggerService.logAction("INFO", "FILE_VERIFY", 
                String.format("File verification - filename: %s, exists: %s", filename, exists),
                null, 
                String.format("filename=%s,sessionId=%s,exists=%s", filename, sessionId, exists));

            Map<String, Object> response = new HashMap<>();
            response.put("exists", exists);
            response.put("filename", filename);
            if (exists) {
                long fileSize = Files.size(filePath);
                response.put("size", fileSize);
                response.put("path", filePath.toAbsolutePath().toString());
            }
            if (sessionId != null) {
                response.put("sessionId", sessionId);
            }

            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/upload/verify", 200, 
                "File verification completed - exists: " + exists);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            String errorMsg = String.format("File verification error: %s", e.getMessage());
            logger.error(errorMsg, e);
            LoggerUtil.logError(logger, "File verification failed", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/upload/verify", 500, "Internal Server Error");
            
            // Log error to database
            databaseLoggerService.logError("FILE_VERIFY_ERROR", errorMsg, e, null,
                String.format("filename=%s,sessionId=%s,exceptionType=%s", 
                    filename, sessionId, e.getClass().getName()));

            ErrorResponse errorResponse = new ErrorResponse(
                "Failed to verify file: " + e.getMessage(),
                "Internal Server Error",
                500,
                "/api/v1/upload/verify");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
}

