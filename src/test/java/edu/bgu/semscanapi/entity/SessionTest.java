package edu.bgu.semscanapi.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test for Session entity
 * Tests basic entity functionality
 */
class SessionTest {

    @Test
    void testSessionCreation() {
        // Given
        Session session = new Session();
        session.setSessionId("session-001");
        session.setSeminarId("seminar-001");
        session.setStartTime(LocalDateTime.now());
        session.setStatus(Session.SessionStatus.OPEN);

        // When & Then
        assertNotNull(session);
        assertEquals("session-001", session.getSessionId());
        assertEquals("seminar-001", session.getSeminarId());
        assertNotNull(session.getStartTime());
        assertEquals(Session.SessionStatus.OPEN, session.getStatus());
    }

    @Test
    void testSessionStatusEnum() {
        // Test that all enum values exist
        assertNotNull(Session.SessionStatus.OPEN);
        assertNotNull(Session.SessionStatus.CLOSED);
    }

    @Test
    void testSessionEqualsAndHashCode() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        
        Session session1 = new Session();
        session1.setSessionId("session-001");
        session1.setSeminarId("seminar-001");
        session1.setStartTime(now);

        Session session2 = new Session();
        session2.setSessionId("session-001");
        session2.setSeminarId("seminar-001");
        session2.setStartTime(now);

        Session session3 = new Session();
        session3.setSessionId("session-002");
        session3.setSeminarId("seminar-001");
        session3.setStartTime(now);

        // When & Then
        assertEquals(session1, session2);
        assertNotEquals(session1, session3);
        assertEquals(session1.hashCode(), session2.hashCode());
        assertNotEquals(session1.hashCode(), session3.hashCode());
    }

    @Test
    void testSessionToString() {
        // Given
        Session session = new Session();
        session.setSessionId("session-001");
        session.setSeminarId("seminar-001");
        session.setStartTime(LocalDateTime.now());
        session.setStatus(Session.SessionStatus.OPEN);

        // When
        String sessionString = session.toString();

        // Then
        assertNotNull(sessionString);
        assertTrue(sessionString.contains("session-001"));
        assertTrue(sessionString.contains("seminar-001"));
        assertTrue(sessionString.contains("OPEN"));
    }
}
