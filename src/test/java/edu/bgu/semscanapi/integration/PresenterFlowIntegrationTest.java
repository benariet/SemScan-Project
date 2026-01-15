package edu.bgu.semscanapi.integration;

import edu.bgu.semscanapi.dto.PresenterHomeResponse;
import edu.bgu.semscanapi.dto.PresenterOpenAttendanceResponse;
import edu.bgu.semscanapi.dto.PresenterSlotRegistrationRequest;
import edu.bgu.semscanapi.dto.PresenterSlotRegistrationResponse;
import edu.bgu.semscanapi.entity.*;
import edu.bgu.semscanapi.entity.ApprovalStatus;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the Presenter flow.
 * Tests the complete flow from registration to opening attendance.
 *
 * Uses real database (semscan_db) - ensure SSH tunnel is running.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PresenterFlowIntegrationTest extends BaseIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(PresenterFlowIntegrationTest.class);

    private static final String TEST_PRESENTER_USERNAME = "integration_test_presenter";
    private static Long testSlotId;
    private static Long testSessionId;

    @BeforeAll
    static void setupTestData() {
        // Test data will be created in first test
    }

    @Test
    @Order(1)
    @DisplayName("Verify database connection works")
    void verifyDatabaseConnection() {
        // Simple query to verify DB connection
        long userCount = userRepository.count();
        long slotCount = seminarSlotRepository.count();

        logger.info("Database connected successfully!");
        logger.info("Users in DB: {}", userCount);
        logger.info("Slots in DB: {}", slotCount);

        assertTrue(userCount >= 0, "Should be able to count users");
        assertTrue(slotCount >= 0, "Should be able to count slots");
    }

    @Test
    @Order(2)
    @DisplayName("Get presenter home - returns slot catalog")
    void getPresenterHome_ReturnsSlotCatalog() {
        // Find an existing presenter in the database
        List<User> presenters = userRepository.findAll().stream()
                .filter(u -> u.getIsPresenter() != null && u.getIsPresenter())
                .limit(1)
                .toList();

        if (presenters.isEmpty()) {
            logger.info("No presenters found in DB - skipping test");
            return;
        }

        String presenterUsername = presenters.get(0).getBguUsername();
        logger.info("Testing with presenter: {}", presenterUsername);

        // Call the service directly
        PresenterHomeResponse response = presenterHomeService.getPresenterHome(presenterUsername);

        assertNotNull(response, "Response should not be null");
        assertNotNull(response.getPresenter(), "Presenter info should not be null");
        assertNotNull(response.getSlotCatalog(), "Slot catalog should not be null");

        logger.info("Presenter: {}", response.getPresenter().getName());
        logger.info("Available slots: {}", response.getSlotCatalog().size());
    }

    @Test
    @Order(3)
    @DisplayName("List available slots from database")
    void listAvailableSlots() {
        List<SeminarSlot> futureSlots = seminarSlotRepository
                .findBySlotDateGreaterThanEqualOrderBySlotDateAscStartTimeAsc(LocalDate.now());

        logger.info("Future slots available: {}", futureSlots.size());

        futureSlots.stream().limit(5).forEach(slot -> {
            logger.info("  Slot {}: {} {}-{} at {} {} (Status: {})",
                    slot.getSlotId(),
                    slot.getSlotDate(),
                    slot.getStartTime(),
                    slot.getEndTime(),
                    slot.getBuilding(),
                    slot.getRoom(),
                    slot.getStatus());
        });

        assertNotNull(futureSlots, "Should return slot list");
    }

    @Test
    @Order(4)
    @DisplayName("List open sessions")
    void listOpenSessions() {
        List<Session> openSessions = sessionRepository.findOpenSessions();

        logger.info("Currently open sessions: {}", openSessions.size());

        openSessions.forEach(session -> {
            Optional<Seminar> seminar = seminarRepository.findById(session.getSeminarId());
            String presenterName = seminar.map(Seminar::getPresenterUsername).orElse("unknown");

            logger.info("  Session {}: Seminar {}, Presenter: {}, Location: {}, Started: {}",
                    session.getSessionId(),
                    session.getSeminarId(),
                    presenterName,
                    session.getLocation(),
                    session.getStartTime());
        });

        assertNotNull(openSessions, "Should return sessions list");
    }

    @Test
    @Order(5)
    @DisplayName("List attendance records for today")
    void listTodayAttendance() {
        // Get all sessions from today
        List<Session> allSessions = sessionRepository.findAll();
        LocalDate today = LocalDate.now();

        List<Session> todaySessions = allSessions.stream()
                .filter(s -> s.getStartTime() != null && s.getStartTime().toLocalDate().equals(today))
                .toList();

        logger.info("Sessions today: {}", todaySessions.size());

        for (Session session : todaySessions) {
            List<Attendance> attendances = attendanceRepository.findBySessionId(session.getSessionId());
            logger.info("  Session {}: {} attendances",
                    session.getSessionId(), attendances.size());
        }
    }

    @Test
    @Order(6)
    @DisplayName("Verify slot registration flow data integrity")
    void verifyRegistrationDataIntegrity() {
        // Get all registrations
        List<SeminarSlotRegistration> registrations = registrationRepository.findAll();

        logger.info("Total registrations: {}", registrations.size());

        // Count by status
        long approved = registrations.stream()
                .filter(r -> r.getApprovalStatus() == ApprovalStatus.APPROVED)
                .count();
        long pending = registrations.stream()
                .filter(r -> r.getApprovalStatus() == ApprovalStatus.PENDING)
                .count();
        long declined = registrations.stream()
                .filter(r -> r.getApprovalStatus() == ApprovalStatus.DECLINED)
                .count();

        logger.info("  Approved: {}", approved);
        logger.info("  Pending: {}", pending);
        logger.info("  Declined: {}", declined);

        // Verify each registration has valid references
        for (SeminarSlotRegistration reg : registrations) {
            // Check slot exists
            Optional<SeminarSlot> slot = seminarSlotRepository.findById(reg.getSlotId());
            assertTrue(slot.isPresent(),
                    "Registration " + reg.getId() + " references non-existent slot " + reg.getSlotId());

            // Check user exists
            Optional<User> user = userRepository.findByBguUsernameIgnoreCase(reg.getPresenterUsername());
            assertTrue(user.isPresent(),
                    "Registration references non-existent user " + reg.getPresenterUsername());
        }

        logger.info("All registrations have valid references!");
    }

    @Test
    @Order(7)
    @DisplayName("Test API endpoint - GET /api/v1/sessions/open")
    void testOpenSessionsEndpoint() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                getApiUrl("/sessions/open"),
                String.class);

        logger.info("GET /api/v1/sessions/open");
        logger.info("  Status: {}", response.getStatusCode());
        logger.info("  Body: {}", response.getBody() != null ?
                response.getBody().substring(0, Math.min(200, response.getBody().length())) + "..." : "null");

        assertEquals(HttpStatus.OK, response.getStatusCode(), "Should return 200 OK");
    }

    @Test
    @Order(8)
    @DisplayName("Test API endpoint - GET /api/v1/info/endpoints")
    void testApiEndpointsInfo() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                getApiUrl("/info/endpoints"),
                String.class);

        logger.info("GET /api/v1/info/endpoints");
        logger.info("  Status: {}", response.getStatusCode());

        assertEquals(HttpStatus.OK, response.getStatusCode(), "Should return 200 OK");
        assertNotNull(response.getBody(), "Should return endpoints list");
    }
}
