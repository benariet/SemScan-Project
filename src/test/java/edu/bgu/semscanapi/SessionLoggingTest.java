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
        String sessionId1 = "test-session-001";
        System.out.println("Creating session: " + sessionId1);
        
        SessionLoggerUtil.logSessionCreated(sessionId1, "seminar-001", "presenter-001");
        SessionLoggerUtil.logSessionActivity(sessionId1, "SESSION_STARTED", "Session started successfully");
        
        // Simulate some attendance
        SessionLoggerUtil.logAttendance(sessionId1, "student-001", "QR_SCAN", LocalDateTime.now().toString());
        SessionLoggerUtil.logAttendance(sessionId1, "student-002", "QR_SCAN", LocalDateTime.now().toString());
        SessionLoggerUtil.logAttendance(sessionId1, "student-003", "MANUAL", LocalDateTime.now().toString());
        
        // Log session statistics
        SessionLoggerUtil.logSessionStatistics(sessionId1, 25, 3, 12.0);
        
        // Change session status
        SessionLoggerUtil.logSessionStatusChange(sessionId1, "OPEN", "CLOSED");
        
        // Close session
        SessionLoggerUtil.logSessionClosed(sessionId1, 90, 3);
        
        // Test 2: Create another session
        String sessionId2 = "test-session-002";
        System.out.println("Creating session: " + sessionId2);
        
        SessionLoggerUtil.logSessionCreated(sessionId2, "seminar-002", "presenter-002");
        SessionLoggerUtil.logSessionActivity(sessionId2, "SESSION_STARTED", "Another session started");
        
        // Simulate attendance for second session
        SessionLoggerUtil.logAttendance(sessionId2, "student-004", "QR_SCAN", LocalDateTime.now().toString());
        SessionLoggerUtil.logAttendance(sessionId2, "student-005", "QR_SCAN", LocalDateTime.now().toString());
        
        // Log statistics for second session
        SessionLoggerUtil.logSessionStatistics(sessionId2, 30, 2, 6.67);
        
        // Close second session
        SessionLoggerUtil.logSessionClosed(sessionId2, 75, 2);
        
        // Test 3: Test error logging
        String sessionId3 = "test-session-003";
        System.out.println("Creating session with error: " + sessionId3);
        
        SessionLoggerUtil.logSessionCreated(sessionId3, "seminar-003", "presenter-003");
        SessionLoggerUtil.logSessionError(sessionId3, "Test error occurred", new RuntimeException("This is a test error"));
        
        System.out.println("Session-specific logging test completed!");
        System.out.println("Check the logs/sessions directory for individual session log files.");
    }
}
