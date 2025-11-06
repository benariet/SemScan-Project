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
        databaseLoggerService.logError(
                "GLOBAL_EXCEPTION",
                ex.getMessage(),
                ex,
                userId,
                String.format("correlationId=%s", LoggerUtil.getCurrentCorrelationId()));
        
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
        databaseLoggerService.logError(
                "GLOBAL_EXCEPTION",
                ex.getMessage(),
                ex,
                userId,
                String.format("correlationId=%s", LoggerUtil.getCurrentCorrelationId()));
        
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
        databaseLoggerService.logError(
                "GLOBAL_EXCEPTION",
                ex.getMessage(),
                ex,
                userId,
                String.format("correlationId=%s", LoggerUtil.getCurrentCorrelationId()));
        
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
        databaseLoggerService.logError(
                "GLOBAL_EXCEPTION",
                ex.getMessage(),
                ex,
                userId,
                String.format("correlationId=%s", LoggerUtil.getCurrentCorrelationId()));
        
        ErrorResponse errorResponse = new ErrorResponse(
            "Internal Server Error",
            ex.getMessage() != null ? ex.getMessage() : "A runtime error occurred",
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}

