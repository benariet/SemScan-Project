package edu.bgu.semscanapi.exception;

import edu.bgu.semscanapi.dto.ErrorResponse;
import edu.bgu.semscanapi.service.DatabaseLoggerService;
import edu.bgu.semscanapi.util.LoggerUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Global Exception Handler
 * Catches all exceptions and logs them to both file and database
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerUtil.getLogger(GlobalExceptionHandler.class);
    
    @Autowired
    private DatabaseLoggerService databaseLoggerService;
    
    /**
     * Handle all exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex, HttpServletRequest request) {
        String correlationId = LoggerUtil.getCurrentCorrelationId();
        String userId = LoggerUtil.getCurrentUserId();
        Long userIdLong = null;
        try {
            userIdLong = userId != null && !userId.isEmpty() ? Long.parseLong(userId) : null;
        } catch (NumberFormatException e) {
            // User ID is not a valid number, skip
        }
        
        // Log to file
        logger.error("Unhandled exception - Correlation ID: {}, Path: {}", correlationId, request.getRequestURI(), ex);
        LoggerUtil.logError(logger, "Unhandled exception in " + request.getMethod() + " " + request.getRequestURI(), ex);
        
        // Log to database
        String payload = String.format("method=%s,path=%s,correlationId=%s", 
            request.getMethod(), request.getRequestURI(), correlationId);
        databaseLoggerService.logError(
            "GLOBAL_EXCEPTION",
            String.format("Unhandled exception in %s %s: %s", request.getMethod(), request.getRequestURI(), ex.getMessage()),
            ex,
            userIdLong,
            payload
        );
        
        ErrorResponse errorResponse = new ErrorResponse(
            "Internal Server Error",
            ex.getMessage() != null ? ex.getMessage() : ex.getClass().getName(),
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
    
    /**
     * Handle NoResourceFoundException (404 errors)
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @Order(1) // Higher priority than general Exception handler
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(NoResourceFoundException ex, HttpServletRequest request) {
        String correlationId = LoggerUtil.getCurrentCorrelationId();
        String userId = LoggerUtil.getCurrentUserId();
        Long userIdLong = null;
        try {
            userIdLong = userId != null && !userId.isEmpty() ? Long.parseLong(userId) : null;
        } catch (NumberFormatException e) {
            // User ID is not a valid number, skip
        }
        
        // Log to file
        logger.warn("Resource not found - Correlation ID: {}, Path: {}, Message: {}", 
            correlationId, request.getRequestURI(), ex.getMessage());
        
        // Log to database
        String payload = String.format("method=%s,path=%s,correlationId=%s", 
            request.getMethod(), request.getRequestURI(), correlationId);
        databaseLoggerService.logError(
            "RESOURCE_NOT_FOUND",
            String.format("Resource not found: %s %s - %s", request.getMethod(), request.getRequestURI(), ex.getMessage()),
            ex,
            userIdLong,
            payload
        );
        
        ErrorResponse errorResponse = new ErrorResponse(
            "Resource Not Found",
            ex.getMessage(),
            HttpStatus.NOT_FOUND.value(),
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }
    
    /**
     * Handle IllegalArgumentException
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
        String correlationId = LoggerUtil.getCurrentCorrelationId();
        String userId = LoggerUtil.getCurrentUserId();
        Long userIdLong = null;
        try {
            userIdLong = userId != null && !userId.isEmpty() ? Long.parseLong(userId) : null;
        } catch (NumberFormatException e) {
            // User ID is not a valid number, skip
        }
        
        // Log to file
        logger.warn("Illegal argument - Correlation ID: {}, Path: {}, Message: {}", 
            correlationId, request.getRequestURI(), ex.getMessage());
        
        // Log to database
        String payload = String.format("method=%s,path=%s,correlationId=%s", 
            request.getMethod(), request.getRequestURI(), correlationId);
        databaseLoggerService.logError(
            "VALIDATION_ERROR",
            String.format("Illegal argument in %s %s: %s", request.getMethod(), request.getRequestURI(), ex.getMessage()),
            ex,
            userIdLong,
            payload
        );
        
        ErrorResponse errorResponse = new ErrorResponse(
            "Bad Request",
            ex.getMessage(),
            HttpStatus.BAD_REQUEST.value(),
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    
    /**
     * Handle RuntimeException
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex, HttpServletRequest request) {
        String correlationId = LoggerUtil.getCurrentCorrelationId();
        String userId = LoggerUtil.getCurrentUserId();
        Long userIdLong = null;
        try {
            userIdLong = userId != null && !userId.isEmpty() ? Long.parseLong(userId) : null;
        } catch (NumberFormatException e) {
            // User ID is not a valid number, skip
        }
        
        // Log to file
        logger.error("Runtime exception - Correlation ID: {}, Path: {}", correlationId, request.getRequestURI(), ex);
        
        // Log to database
        String payload = String.format("method=%s,path=%s,correlationId=%s", 
            request.getMethod(), request.getRequestURI(), correlationId);
        databaseLoggerService.logError(
            "RUNTIME_ERROR",
            String.format("Runtime exception in %s %s: %s", request.getMethod(), request.getRequestURI(), ex.getMessage()),
            ex,
            userIdLong,
            payload
        );
        
        ErrorResponse errorResponse = new ErrorResponse(
            "Internal Server Error",
            ex.getMessage() != null ? ex.getMessage() : "A runtime error occurred",
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}

