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
        session.setSessionId(1L);
        session.setSeminarId(1L);
        session.setStartTime(LocalDateTime.now());
        session.setStatus(Session.SessionStatus.OPEN);

        // When & Then
        assertNotNull(session);
        assertEquals(1L, session.getSessionId());
        assertEquals(1L, session.getSeminarId());
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
        session1.setSessionId(1L);
        session1.setSeminarId(1L);
        session1.setStartTime(now);

        Session session2 = new Session();
        session2.setSessionId(1L);
        session2.setSeminarId(1L);
        session2.setStartTime(now);

        Session session3 = new Session();
        session3.setSessionId(2L);
        session3.setSeminarId(1L);
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
        session.setSessionId(1L);
        session.setSeminarId(1L);
        session.setStartTime(LocalDateTime.now());
        session.setStatus(Session.SessionStatus.OPEN);

        // When
        String sessionString = session.toString();

        // Then
        assertNotNull(sessionString);
        assertTrue(sessionString.contains("1"));
        assertTrue(sessionString.contains("1"));
        assertTrue(sessionString.contains("OPEN"));
    }
}
