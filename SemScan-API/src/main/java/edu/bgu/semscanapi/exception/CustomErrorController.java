package edu.bgu.semscanapi.exception;

import edu.bgu.semscanapi.service.DatabaseLoggerService;
import edu.bgu.semscanapi.util.LoggerUtil;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom Error Controller to catch ALL errors, including those that bypass normal exception handlers
 * This catches errors at the servlet container level (404s, 500s, etc.)
 */
@RestController
public class CustomErrorController implements ErrorController {

    private static final Logger logger = LoggerFactory.getLogger(CustomErrorController.class);
    
    @Autowired(required = false)
    private DatabaseLoggerService databaseLoggerService;

    @RequestMapping("/error")
    public ResponseEntity<Map<String, Object>> handleError(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        Object requestUri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);

        String statusCode = status != null ? status.toString() : "UNKNOWN";
        String exceptionType = exception != null ? exception.getClass().getName() : "UNKNOWN";
        String errorMessage = message != null ? message.toString() : "Unknown error";
        String uri = requestUri != null ? requestUri.toString() : request.getRequestURI();

        // Log error details
        if (exception instanceof Throwable) {
            logger.error("ERROR CONTROLLER TRIGGERED - Status: {}, Type: {}, Message: {}, URI: {}, Method: {}, Query: {}",
                    statusCode, exceptionType, errorMessage, uri, request.getMethod(), request.getQueryString(),
                    (Throwable) exception);
        } else {
            logger.error("ERROR CONTROLLER TRIGGERED - Status: {}, Type: {}, Message: {}, URI: {}, Method: {}, Query: {}",
                    statusCode, exceptionType, errorMessage, uri, request.getMethod(), request.getQueryString());
        }

        // Generate correlation ID if not present
        String correlationId = LoggerUtil.getCurrentCorrelationId();
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = LoggerUtil.generateAndSetCorrelationId();
        }
        
        // Log to file logger
        logger.error("ERROR CONTROLLER - Status: {}, URI: {}, Exception: {}, Message: {}, Correlation ID: {}", 
                    statusCode, uri, exceptionType, errorMessage, correlationId, exception);
        
        // Log to app_logs table
        if (databaseLoggerService != null) {
            try {
                String bguUsername = LoggerUtil.getCurrentBguUsername();
                String payload = String.format("statusCode=%s,uri=%s,method=%s,exceptionType=%s,message=%s,correlationId=%s",
                                              statusCode, uri, request.getMethod(), exceptionType, errorMessage, correlationId);
                databaseLoggerService.logError("ERROR_CONTROLLER",
                                            String.format("Error Controller triggered - Status %s: %s", statusCode, errorMessage),
                                            exception instanceof Throwable ? (Throwable) exception : null,
                                            bguUsername, payload);
            } catch (Exception logEx) {
                logger.error("FAILED to log to app_logs: {}", logEx.getMessage(), logEx);
            }
        }
        
        // Build response
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "An error occurred");
        errorResponse.put("status", statusCode);
        errorResponse.put("message", errorMessage);
        errorResponse.put("path", uri);
        errorResponse.put("correlationId", correlationId);
        
        HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        try {
            if (status != null) {
                int statusCodeInt = Integer.parseInt(status.toString());
                httpStatus = HttpStatus.valueOf(statusCodeInt);
            }
        } catch (Exception e) {
            // Use default INTERNAL_SERVER_ERROR
        }
        
        return ResponseEntity.status(httpStatus).body(errorResponse);
    }
}
