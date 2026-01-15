package edu.bgu.semscanapi;

import edu.bgu.semscanapi.util.SessionLoggerUtil;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * Test class to demonstrate session-specific logging functionality.
 * This test creates session log files with different session IDs.
 * Note: This test doesn't need Spring context - SessionLoggerUtil is a static utility.
 */
public class SessionLoggingTest {

    private static final Logger logger = LoggerFactory.getLogger(SessionLoggingTest.class);

    @Test
    public void testSessionSpecificLogging() {
        logger.info("Testing session-specific logging...");

        // Test 1: Create a session and log various events
        Long sessionId1 = 1L;
        logger.info("Creating session: {}", sessionId1);

        SessionLoggerUtil.logSessionCreated(sessionId1, 1L, "presenter.one");
        SessionLoggerUtil.logSessionActivity(sessionId1, "SESSION_STARTED", "Session started successfully");

        // Simulate some attendance
        SessionLoggerUtil.logAttendance(sessionId1, "student.3", "QR_SCAN", LocalDateTime.now().toString());
        SessionLoggerUtil.logAttendance(sessionId1, "student.4", "QR_SCAN", LocalDateTime.now().toString());
        SessionLoggerUtil.logAttendance(sessionId1, "student.5", "MANUAL", LocalDateTime.now().toString());

        // Log session statistics
        SessionLoggerUtil.logSessionStatistics(sessionId1, 25, 3, 12.0);

        // Change session status
        SessionLoggerUtil.logSessionStatusChange(sessionId1, "OPEN", "CLOSED");

        // Close session
        SessionLoggerUtil.logSessionClosed(sessionId1, 90, 3);

        // Test 2: Create another session
        Long sessionId2 = 2L;
        logger.info("Creating session: {}", sessionId2);

        SessionLoggerUtil.logSessionCreated(sessionId2, 2L, "presenter.two");
        SessionLoggerUtil.logSessionActivity(sessionId2, "SESSION_STARTED", "Another session started");

        // Simulate attendance for second session
        SessionLoggerUtil.logAttendance(sessionId2, "student.6", "QR_SCAN", LocalDateTime.now().toString());
        SessionLoggerUtil.logAttendance(sessionId2, "student.7", "QR_SCAN", LocalDateTime.now().toString());

        // Log statistics for second session
        SessionLoggerUtil.logSessionStatistics(sessionId2, 30, 2, 6.67);

        // Close second session
        SessionLoggerUtil.logSessionClosed(sessionId2, 75, 2);

        // Test 3: Test error logging
        Long sessionId3 = 3L;
        logger.info("Creating session with error: {}", sessionId3);

        SessionLoggerUtil.logSessionCreated(sessionId3, 3L, "presenter.three");
        SessionLoggerUtil.logSessionError(sessionId3, "Test error occurred", new RuntimeException("This is a test error"));

        logger.info("Session-specific logging test completed!");
        logger.info("Check the logs/sessions directory for individual session log files.");
    }
}
