package edu.bgu.semscanapi;

import edu.bgu.semscanapi.util.SessionLoggerUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

/**
 * Test class to demonstrate session-specific logging functionality.
 * This test creates session log files with different session IDs.
 */
@SpringBootTest
public class SessionLoggingTest {

    @Test
    public void testSessionSpecificLogging() {
        System.out.println("Testing session-specific logging...");
        
        // Test 1: Create a session and log various events
        Long sessionId1 = 1L;
        System.out.println("Creating session: " + sessionId1);
        
        SessionLoggerUtil.logSessionCreated(sessionId1, 1L, 2L);
        SessionLoggerUtil.logSessionActivity(sessionId1, "SESSION_STARTED", "Session started successfully");
        
        // Simulate some attendance
        SessionLoggerUtil.logAttendance(sessionId1, 3L, "QR_SCAN", LocalDateTime.now().toString());
        SessionLoggerUtil.logAttendance(sessionId1, 4L, "QR_SCAN", LocalDateTime.now().toString());
        SessionLoggerUtil.logAttendance(sessionId1, 5L, "MANUAL", LocalDateTime.now().toString());
        
        // Log session statistics
        SessionLoggerUtil.logSessionStatistics(sessionId1, 25, 3, 12.0);
        
        // Change session status
        SessionLoggerUtil.logSessionStatusChange(sessionId1, "OPEN", "CLOSED");
        
        // Close session
        SessionLoggerUtil.logSessionClosed(sessionId1, 90, 3);
        
        // Test 2: Create another session
        Long sessionId2 = 2L;
        System.out.println("Creating session: " + sessionId2);
        
        SessionLoggerUtil.logSessionCreated(sessionId2, 2L, 3L);
        SessionLoggerUtil.logSessionActivity(sessionId2, "SESSION_STARTED", "Another session started");
        
        // Simulate attendance for second session
        SessionLoggerUtil.logAttendance(sessionId2, 6L, "QR_SCAN", LocalDateTime.now().toString());
        SessionLoggerUtil.logAttendance(sessionId2, 7L, "QR_SCAN", LocalDateTime.now().toString());
        
        // Log statistics for second session
        SessionLoggerUtil.logSessionStatistics(sessionId2, 30, 2, 6.67);
        
        // Close second session
        SessionLoggerUtil.logSessionClosed(sessionId2, 75, 2);
        
        // Test 3: Test error logging
        Long sessionId3 = 3L;
        System.out.println("Creating session with error: " + sessionId3);
        
        SessionLoggerUtil.logSessionCreated(sessionId3, 3L, 4L);
        SessionLoggerUtil.logSessionError(sessionId3, "Test error occurred", new RuntimeException("This is a test error"));
        
        System.out.println("Session-specific logging test completed!");
        System.out.println("Check the logs/sessions directory for individual session log files.");
    }
}
