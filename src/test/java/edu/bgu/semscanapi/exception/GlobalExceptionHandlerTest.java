package edu.bgu.semscanapi.exception;

import edu.bgu.semscanapi.dto.ErrorResponse;
import edu.bgu.semscanapi.service.DatabaseLoggerService;
import jakarta.servlet.http.HttpServletRequest;
import org.hibernate.exception.LockAcquisitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GlobalExceptionHandler
 * Includes Test Case 37: Backend Returns 500 Error / Database Lock Detection
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private DatabaseLoggerService databaseLoggerService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private GlobalExceptionHandler globalExceptionHandler;

    @BeforeEach
    void setUp() {
        // Mock LoggerUtil static methods
        when(request.getRequestURI()).thenReturn("/api/v1/test");
        
        // Mock database logger to avoid NPE
        doNothing().when(databaseLoggerService).logError(anyString(), anyString(), any(), anyString(), anyString());
    }

    // ==================== Test Case 37: Backend Returns 500 Error ====================

    @Test
    void handleException_WithGenericException_Returns500Error() {
        // Given - Backend has internal error
        Exception ex = new Exception("Internal server error");
        
        // When
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleException(ex, request);
        
        // Then - Error message shown, no app crash, user can retry
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(500, body.getStatus());
        assertEquals("Internal Server Error", body.getError());
        assertNotNull(body.getMessage());
        
        // Verify error was logged
        verify(databaseLoggerService).logError(eq("GLOBAL_EXCEPTION"), anyString(), eq(ex), anyString(), anyString());
    }

    @Test
    void handleException_WithNullMessage_Returns500Error() {
        // Given - Exception with null message
        Exception ex = new Exception();
        
        // When
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleException(ex, request);
        
        // Then - No app crash, returns 500
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(500, body.getStatus());
    }

    // ==================== Database Lock Detection Tests ====================

    @Test
    void handleDeadlockException_Returns409Conflict() {
        // Given - Database deadlock
        DeadlockLoserDataAccessException ex = new DeadlockLoserDataAccessException(
            "Deadlock found when trying to get lock", 
            new SQLException("Deadlock", "40001", 1213));
        
        // When
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleDeadlockException(ex, request);
        
        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(409, body.getStatus());
        assertEquals("Database conflict occurred. Please try again.", body.getError());
        assertTrue(body.getMessage().contains("try again"));
        
        // Verify error was logged with specific code
        verify(databaseLoggerService).logError(eq("DATABASE_DEADLOCK"), anyString(), eq(ex), anyString(), anyString());
    }

    @Test
    void handleCannotAcquireLockException_Returns503ServiceUnavailable() {
        // Given - Database lock timeout
        CannotAcquireLockException ex = new CannotAcquireLockException(
            "Lock wait timeout exceeded", 
            new SQLException("Lock wait timeout", "HY000", 1205));
        
        // When
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleCannotAcquireLockException(ex, request);
        
        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(503, body.getStatus());
        assertEquals("Database is temporarily busy. Please try again.", body.getError());
        assertTrue(body.getMessage().contains("try again"));
        
        // Verify error was logged
        verify(databaseLoggerService).logError(eq("DATABASE_LOCK_TIMEOUT"), anyString(), eq(ex), anyString(), anyString());
    }

    @Test
    void handleQueryTimeoutException_Returns408RequestTimeout() {
        // Given - Query timeout
        QueryTimeoutException ex = new QueryTimeoutException("Query timeout");
        
        // When
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleQueryTimeoutException(ex, request);
        
        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.REQUEST_TIMEOUT, response.getStatusCode());
        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(408, body.getStatus());
        assertEquals("Request timed out. Please try again.", body.getError());
        assertTrue(body.getMessage().contains("try again"));
        
        // Verify error was logged
        verify(databaseLoggerService).logError(eq("DATABASE_QUERY_TIMEOUT"), anyString(), eq(ex), anyString(), anyString());
    }

    @Test
    void handleLockAcquisitionException_Returns503ServiceUnavailable() {
        // Given - Hibernate lock acquisition failed
        LockAcquisitionException ex = new LockAcquisitionException(
            "Could not acquire lock", 
            new SQLException("Lock acquisition failed", "HY000", 1205));
        
        // When
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleLockAcquisitionException(ex, request);
        
        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(503, body.getStatus());
        assertEquals("Database is temporarily busy. Please try again.", body.getError());
        
        // Verify error was logged
        verify(databaseLoggerService).logError(eq("DATABASE_LOCK_ACQUISITION_FAILED"), anyString(), eq(ex), anyString(), anyString());
    }

    @Test
    void handleDataAccessException_WithSQLDeadlock_Returns409Conflict() {
        // Given - DataAccessException with SQL deadlock (error code 1213)
        SQLException sqlEx = new SQLException("Deadlock found", "40001", 1213);
        DataAccessException ex = new DataAccessException("Database deadlock", sqlEx) {};
        
        // When
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleDataAccessException(ex, request);
        
        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(409, body.getStatus());
        assertEquals("Database conflict occurred. Please try again.", body.getError());
        
        // Verify error was logged with SQL error code
        verify(databaseLoggerService).logError(eq("DATABASE_DEADLOCK_SQL"), anyString(), eq(ex), anyString(), contains("sqlErrorCode=1213"));
    }

    @Test
    void handleDataAccessException_WithSQLLockTimeout_Returns503ServiceUnavailable() {
        // Given - DataAccessException with SQL lock timeout (error code 1205)
        SQLException sqlEx = new SQLException("Lock wait timeout", "HY000", 1205);
        DataAccessException ex = new DataAccessException("Lock timeout", sqlEx) {};
        
        // When
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleDataAccessException(ex, request);
        
        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(503, body.getStatus());
        assertEquals("Database is temporarily busy. Please try again.", body.getError());
        
        // Verify error was logged with SQL error code
        verify(databaseLoggerService).logError(eq("DATABASE_LOCK_TIMEOUT_SQL"), anyString(), eq(ex), anyString(), contains("sqlErrorCode=1205"));
    }

    @Test
    void handleDataAccessException_WithGenericError_Returns500InternalServerError() {
        // Given - Generic DataAccessException without lock-related error codes
        SQLException sqlEx = new SQLException("General database error", "HY000", 1000);
        DataAccessException ex = new DataAccessException("Database error", sqlEx) {};
        
        // When
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleDataAccessException(ex, request);
        
        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(500, body.getStatus());
        assertEquals("Database error occurred", body.getError());
        
        // Verify error was logged
        verify(databaseLoggerService).logError(eq("DATABASE_ACCESS_ERROR"), anyString(), eq(ex), anyString(), anyString());
    }

    @Test
    void handleDataAccessException_WithoutSQLException_Returns500InternalServerError() {
        // Given - DataAccessException without SQLException root cause
        DataAccessException ex = new DataAccessException("Database error") {};
        
        // When
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleDataAccessException(ex, request);
        
        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(500, body.getStatus());
        
        // Verify error was logged
        verify(databaseLoggerService).logError(eq("DATABASE_ACCESS_ERROR"), anyString(), eq(ex), anyString(), anyString());
    }

    // ==================== Additional Error Handling Tests ====================

    @Test
    void handleRuntimeException_Returns500InternalServerError() {
        // Given
        RuntimeException ex = new RuntimeException("Runtime error");
        
        // When
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleRuntimeException(ex, request);
        
        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(500, body.getStatus());
        assertEquals("Internal Server Error", body.getError());
        
        // Verify error was logged
        verify(databaseLoggerService).logError(eq("GLOBAL_EXCEPTION"), anyString(), eq(ex), anyString(), anyString());
    }

    @Test
    void handleIllegalArgumentException_Returns400BadRequest() {
        // Given
        IllegalArgumentException ex = new IllegalArgumentException("Invalid argument");
        
        // When
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleIllegalArgumentException(ex, request);
        
        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(400, body.getStatus());
        assertEquals("Bad Request", body.getError());
        
        // Verify error was logged
        verify(databaseLoggerService).logError(eq("GLOBAL_EXCEPTION"), anyString(), eq(ex), anyString(), anyString());
    }

    // ==================== Test Case 37: User Can Retry ====================

    @Test
    void handleException_AllErrorsAllowRetry() {
        // Given - Various error types
        Exception genericEx = new Exception("Error");
        DeadlockLoserDataAccessException deadlockEx = new DeadlockLoserDataAccessException(
            "Deadlock", new SQLException("Deadlock", "40001", 1213));
        CannotAcquireLockException lockEx = new CannotAcquireLockException(
            "Lock timeout", new SQLException("Timeout", "HY000", 1205));
        
        // When - All return proper error responses
        ResponseEntity<ErrorResponse> response1 = globalExceptionHandler.handleException(genericEx, request);
        ResponseEntity<ErrorResponse> response2 = globalExceptionHandler.handleDeadlockException(deadlockEx, request);
        ResponseEntity<ErrorResponse> response3 = globalExceptionHandler.handleCannotAcquireLockException(lockEx, request);
        
        // Then - All responses are valid and allow retry (no crash)
        assertNotNull(response1);
        assertNotNull(response2);
        assertNotNull(response3);
        
        // All have proper error messages that suggest retry
        ErrorResponse body2 = response2.getBody();
        ErrorResponse body3 = response3.getBody();
        assertNotNull(body2);
        assertNotNull(body3);
        assertTrue(body2.getMessage().toLowerCase().contains("try again"));
        assertTrue(body3.getMessage().toLowerCase().contains("try again"));
        
        // No exceptions thrown - app doesn't crash
        assertDoesNotThrow(() -> {
            globalExceptionHandler.handleException(genericEx, request);
        });
    }
}

