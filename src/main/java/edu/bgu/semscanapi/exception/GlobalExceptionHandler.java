package edu.bgu.semscanapi.exception;

import edu.bgu.semscanapi.dto.ErrorResponse;
import edu.bgu.semscanapi.service.DatabaseLoggerService;
import edu.bgu.semscanapi.util.LoggerUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.hibernate.exception.LockAcquisitionException;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.hibernate.exception.ConstraintViolationException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

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
        // IMMEDIATE console logging (bypasses all logging frameworks)
        System.err.println("=========================================");
        System.err.println("GLOBAL EXCEPTION HANDLER TRIGGERED");
        System.err.println("=========================================");
        System.err.println("Exception Type: " + ex.getClass().getName());
        System.err.println("Exception Message: " + (ex.getMessage() != null ? ex.getMessage() : "null"));
        System.err.println("Request URI: " + request.getRequestURI());
        System.err.println("Request Method: " + request.getMethod());
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        System.err.println("Stack Trace:\n" + sw.toString());
        System.err.println("=========================================");
        
        String correlationId = LoggerUtil.getCurrentCorrelationId();
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = LoggerUtil.generateAndSetCorrelationId();
        }
        String bguUsername = LoggerUtil.getCurrentBguUsername();
        
        // Log to file logger
        logger.error("Unhandled exception - Correlation ID: {}, Path: {}", correlationId, request.getRequestURI(), ex);
        
        // Log to app_logs table (with try-catch to ensure it doesn't fail silently)
        try {
            databaseLoggerService.logError(
                    "GLOBAL_EXCEPTION",
                    ex.getMessage() != null ? ex.getMessage() : ex.getClass().getName(),
                    ex,
                    bguUsername,
                    String.format("correlationId=%s,uri=%s,method=%s", correlationId, request.getRequestURI(), request.getMethod()));
        } catch (Exception logEx) {
            System.err.println("FAILED to log to app_logs: " + logEx.getMessage());
            logEx.printStackTrace();
            // Still continue to return error response
        }
        
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
        String bguUsername = LoggerUtil.getCurrentBguUsername();
        logger.error("Resource not found - Correlation ID: {}, Path: {}", correlationId, request.getRequestURI(), ex);
        databaseLoggerService.logError(
                "GLOBAL_EXCEPTION",
                ex.getMessage(),
                ex,
                bguUsername,
                String.format("correlationId=%s", correlationId));
        
        ErrorResponse errorResponse = new ErrorResponse(
            "Resource Not Found",
            ex.getMessage(),
            HttpStatus.NOT_FOUND.value(),
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }
    
    /**
     * Handle MethodArgumentNotValidException (validation failures from @Valid)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @Order(1)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String correlationId = LoggerUtil.getCurrentCorrelationId();
        String bguUsername = LoggerUtil.getCurrentBguUsername();
        
        // Extract validation error messages
        StringBuilder errorMessages = new StringBuilder();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            if (errorMessages.length() > 0) {
                errorMessages.append("; ");
            }
            errorMessages.append(error.getField()).append(": ").append(error.getDefaultMessage());
        });
        
        String errorMessage = errorMessages.length() > 0 ? errorMessages.toString() : "Validation failed";
        logger.error("Validation error - Correlation ID: {}, Path: {}, Errors: {}", correlationId, request.getRequestURI(), errorMessage);
        databaseLoggerService.logError(
                "VALIDATION_ERROR",
                "Request validation failed: " + errorMessage,
                ex,
                bguUsername,
                String.format("correlationId=%s,path=%s", correlationId, request.getRequestURI()));
        
        ErrorResponse errorResponse = new ErrorResponse(
            "Validation Failed",
            errorMessage,
            HttpStatus.BAD_REQUEST.value(),
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    
    /**
     * Handle HttpMessageNotReadableException (malformed JSON, null request body, etc.)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @Order(1)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex, HttpServletRequest request) {
        String correlationId = LoggerUtil.getCurrentCorrelationId();
        String bguUsername = LoggerUtil.getCurrentBguUsername();
        
        String errorMessage = "Invalid request body. Please check your JSON format.";
        if (ex.getMessage() != null && ex.getMessage().contains("Required request body is missing")) {
            errorMessage = "Request body is required but was missing.";
        } else if (ex.getMessage() != null && ex.getMessage().contains("JSON parse error")) {
            errorMessage = "Invalid JSON format in request body.";
        }
        
        logger.error("Invalid request body - Correlation ID: {}, Path: {}, Error: {}", correlationId, request.getRequestURI(), ex.getMessage());
        databaseLoggerService.logError(
                "INVALID_REQUEST_BODY",
                "Request body validation failed: " + (ex.getMessage() != null ? ex.getMessage() : "Unknown error"),
                ex,
                bguUsername,
                String.format("correlationId=%s,path=%s", correlationId, request.getRequestURI()));
        
        ErrorResponse errorResponse = new ErrorResponse(
            "Invalid Request",
            errorMessage,
            HttpStatus.BAD_REQUEST.value(),
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    
    /**
     * Handle IllegalArgumentException
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @Order(2)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
        String correlationId = LoggerUtil.getCurrentCorrelationId();
        String bguUsername = LoggerUtil.getCurrentBguUsername();
        logger.error("Illegal argument - Correlation ID: {}, Path: {}", correlationId, request.getRequestURI(), ex);
        databaseLoggerService.logError(
                "VALIDATION_ERROR",
                ex.getMessage(),
                ex,
                bguUsername,
                String.format("correlationId=%s", correlationId));
        
        ErrorResponse errorResponse = new ErrorResponse(
            "Bad Request",
            ex.getMessage(),
            HttpStatus.BAD_REQUEST.value(),
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    
    /**
     * Handle Constraint Violation Exceptions (duplicate email, username, etc.)
     */
    @ExceptionHandler({ConstraintViolationException.class, org.hibernate.exception.DataException.class})
    @Order(2)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(Exception ex, HttpServletRequest request) {
        String correlationId = LoggerUtil.getCurrentCorrelationId();
        String bguUsername = LoggerUtil.getCurrentBguUsername();
        
        String errorMessage = "Data validation failed";
        String userMessage = "The provided data conflicts with existing records. Please check your input and try again.";
        HttpStatus status = HttpStatus.CONFLICT;
        
        // Extract constraint violation details
        Throwable rootCause = ex.getCause();
        if (rootCause instanceof SQLIntegrityConstraintViolationException) {
            SQLIntegrityConstraintViolationException sqlEx = (SQLIntegrityConstraintViolationException) rootCause;
            String sqlMessage = sqlEx.getMessage();
            
            if (sqlMessage != null) {
                if (sqlMessage.contains("users.email")) {
                    errorMessage = "Email already exists";
                    userMessage = "This email address is already registered to another user. Please use a different email address.";
                } else if (sqlMessage.contains("users.bgu_username")) {
                    errorMessage = "Username already exists";
                    userMessage = "This username is already taken. Please choose a different username.";
                } else if (sqlMessage.contains("Duplicate entry")) {
                    errorMessage = "Duplicate entry";
                    userMessage = "This information already exists in the system. Please check your input.";
                }
            }
        }
        
        logger.error("Constraint violation - Correlation ID: {}, Path: {}, Error: {}", correlationId, request.getRequestURI(), errorMessage, ex);
        databaseLoggerService.logError(
                "CONSTRAINT_VIOLATION",
                errorMessage + ": " + ex.getMessage(),
                ex,
                bguUsername,
                String.format("correlationId=%s,path=%s", correlationId, request.getRequestURI()));
        
        ErrorResponse errorResponse = new ErrorResponse(
            errorMessage,
            userMessage,
            status.value(),
            request.getRequestURI()
        );
        
        return ResponseEntity.status(status).body(errorResponse);
    }
    
    /**
     * Handle RuntimeException
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex, HttpServletRequest request) {
        String correlationId = LoggerUtil.getCurrentCorrelationId();
        String bguUsername = LoggerUtil.getCurrentBguUsername();
        logger.error("Runtime exception - Correlation ID: {}, Path: {}", correlationId, request.getRequestURI(), ex);
        databaseLoggerService.logError(
                "GLOBAL_EXCEPTION",
                ex.getMessage(),
                ex,
                bguUsername,
                String.format("correlationId=%s", correlationId));
        
        ErrorResponse errorResponse = new ErrorResponse(
            "Internal Server Error",
            ex.getMessage() != null ? ex.getMessage() : "A runtime error occurred",
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
    
    /**
     * Handle Database Deadlock Exceptions
     * MySQL Error Code 1213: Deadlock found when trying to get lock
     */
    @ExceptionHandler(DeadlockLoserDataAccessException.class)
    @Order(2) // Higher priority than general Exception handler
    public ResponseEntity<ErrorResponse> handleDeadlockException(DeadlockLoserDataAccessException ex, HttpServletRequest request) {
        String correlationId = LoggerUtil.getCurrentCorrelationId();
        String bguUsername = LoggerUtil.getCurrentBguUsername();
        logger.error("Database deadlock detected - Correlation ID: {}, Path: {}", correlationId, request.getRequestURI(), ex);
        databaseLoggerService.logError(
                "DATABASE_DEADLOCK",
                "Database deadlock occurred. Transaction was rolled back. User should retry.",
                ex,
                bguUsername,
                String.format("correlationId=%s,path=%s", correlationId, request.getRequestURI()));
        
        ErrorResponse errorResponse = new ErrorResponse(
            "Database conflict occurred. Please try again.",
            "The operation could not be completed due to a database conflict. This usually happens when multiple users access the same data simultaneously. Please wait a moment and try again.",
            HttpStatus.CONFLICT.value(),
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }
    
    /**
     * Handle Database Lock Acquisition Exceptions
     * MySQL Error Code 1205: Lock wait timeout exceeded
     */
    @ExceptionHandler(CannotAcquireLockException.class)
    @Order(2) // Higher priority than general Exception handler
    public ResponseEntity<ErrorResponse> handleCannotAcquireLockException(CannotAcquireLockException ex, HttpServletRequest request) {
        String correlationId = LoggerUtil.getCurrentCorrelationId();
        String bguUsername = LoggerUtil.getCurrentBguUsername();
        logger.error("Database lock timeout - Correlation ID: {}, Path: {}", correlationId, request.getRequestURI(), ex);
        databaseLoggerService.logError(
                "DATABASE_LOCK_TIMEOUT",
                "Database lock could not be acquired within timeout period. User should retry.",
                ex,
                bguUsername,
                String.format("correlationId=%s,path=%s", correlationId, request.getRequestURI()));
        
        ErrorResponse errorResponse = new ErrorResponse(
            "Database is temporarily busy. Please try again.",
            "The database is currently processing other requests. Please wait a moment and try again.",
            HttpStatus.SERVICE_UNAVAILABLE.value(),
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
    }
    
    /**
     * Handle Query Timeout Exceptions
     */
    @ExceptionHandler(QueryTimeoutException.class)
    @Order(2) // Higher priority than general Exception handler
    public ResponseEntity<ErrorResponse> handleQueryTimeoutException(QueryTimeoutException ex, HttpServletRequest request) {
        String correlationId = LoggerUtil.getCurrentCorrelationId();
        String bguUsername = LoggerUtil.getCurrentBguUsername();
        logger.error("Database query timeout - Correlation ID: {}, Path: {}", correlationId, request.getRequestURI(), ex);
        databaseLoggerService.logError(
                "DATABASE_QUERY_TIMEOUT",
                "Database query exceeded timeout limit. User should retry.",
                ex,
                bguUsername,
                String.format("correlationId=%s,path=%s", correlationId, request.getRequestURI()));
        
        ErrorResponse errorResponse = new ErrorResponse(
            "Request timed out. Please try again.",
            "The database query took too long to complete. This may happen during high load. Please try again.",
            HttpStatus.REQUEST_TIMEOUT.value(),
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(errorResponse);
    }
    
    /**
     * Handle Hibernate Lock Acquisition Exceptions
     */
    @ExceptionHandler(LockAcquisitionException.class)
    @Order(2) // Higher priority than general Exception handler
    public ResponseEntity<ErrorResponse> handleLockAcquisitionException(LockAcquisitionException ex, HttpServletRequest request) {
        String correlationId = LoggerUtil.getCurrentCorrelationId();
        String bguUsername = LoggerUtil.getCurrentBguUsername();
        logger.error("Hibernate lock acquisition failed - Correlation ID: {}, Path: {}", correlationId, request.getRequestURI(), ex);
        databaseLoggerService.logError(
                "DATABASE_LOCK_ACQUISITION_FAILED",
                "Hibernate could not acquire database lock. User should retry.",
                ex,
                bguUsername,
                String.format("correlationId=%s,path=%s", correlationId, request.getRequestURI()));
        
        ErrorResponse errorResponse = new ErrorResponse(
            "Database is temporarily busy. Please try again.",
            "The database is currently processing other requests. Please wait a moment and try again.",
            HttpStatus.SERVICE_UNAVAILABLE.value(),
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
    }
    
    /**
     * Handle DataAccessException and check for SQL lock errors
     * This catches SQLExceptions with MySQL error codes 1205 (lock timeout) and 1213 (deadlock)
     */
    @ExceptionHandler(DataAccessException.class)
    @Order(2) // Higher priority than general Exception handler
    public ResponseEntity<ErrorResponse> handleDataAccessException(DataAccessException ex, HttpServletRequest request) {
        String correlationId = LoggerUtil.getCurrentCorrelationId();
        String bguUsername = LoggerUtil.getCurrentBguUsername();
        
        // Check if it's a SQLException with lock-related error codes
        Throwable rootCause = ex.getRootCause();
        if (rootCause instanceof SQLException) {
            SQLException sqlEx = (SQLException) rootCause;
            int errorCode = sqlEx.getErrorCode();
            
            // MySQL Error Code 1213: Deadlock found when trying to get lock
            if (errorCode == 1213) {
                logger.error("Database deadlock (SQL 1213) - Correlation ID: {}, Path: {}", correlationId, request.getRequestURI(), ex);
                databaseLoggerService.logError(
                        "DATABASE_DEADLOCK_SQL",
                        "Database deadlock detected (SQL Error 1213). Transaction was rolled back. User should retry.",
                        ex,
                        bguUsername,
                        String.format("correlationId=%s,path=%s,sqlErrorCode=1213", correlationId, request.getRequestURI()));
                
                ErrorResponse errorResponse = new ErrorResponse(
                    "Database conflict occurred. Please try again.",
                    "The operation could not be completed due to a database conflict. This usually happens when multiple users access the same data simultaneously. Please wait a moment and try again.",
                    HttpStatus.CONFLICT.value(),
                    request.getRequestURI()
                );
                
                return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
            }
            
            // MySQL Error Code 1205: Lock wait timeout exceeded
            if (errorCode == 1205) {
                logger.error("Database lock timeout (SQL 1205) - Correlation ID: {}, Path: {}", correlationId, request.getRequestURI(), ex);
                databaseLoggerService.logError(
                        "DATABASE_LOCK_TIMEOUT_SQL",
                        "Database lock timeout detected (SQL Error 1205). User should retry.",
                        ex,
                        bguUsername,
                        String.format("correlationId=%s,path=%s,sqlErrorCode=1205", correlationId, request.getRequestURI()));
                
                ErrorResponse errorResponse = new ErrorResponse(
                    "Database is temporarily busy. Please try again.",
                    "The database is currently processing other requests. Please wait a moment and try again.",
                    HttpStatus.SERVICE_UNAVAILABLE.value(),
                    request.getRequestURI()
                );
                
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
            }
        }
        
        // Generic DataAccessException - log and return 500
        logger.error("Data access exception - Correlation ID: {}, Path: {}", correlationId, request.getRequestURI(), ex);
        databaseLoggerService.logError(
                "DATABASE_ACCESS_ERROR",
                ex.getMessage(),
                ex,
                bguUsername,
                String.format("correlationId=%s,path=%s", correlationId, request.getRequestURI()));
        
        ErrorResponse errorResponse = new ErrorResponse(
            "Database error occurred",
            "An error occurred while accessing the database. Please try again later.",
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}

