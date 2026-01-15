package edu.bgu.semscanapi.integration;

import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

import edu.bgu.semscanapi.repository.*;
import edu.bgu.semscanapi.service.*;

import java.net.Socket;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Base class for integration tests.
 * Uses the real semscan_db database via SSH tunnel.
 *
 * IMPORTANT: Ensure SSH tunnel is running before running tests:
 * ssh -L 3307:127.0.0.1:3306 webmaster@132.72.50.53
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(BaseIntegrationTest.class);

    private static boolean databaseAvailable = false;

    @BeforeAll
    static void checkDatabaseConnection() {
        // Check if SSH tunnel is running (port 3307)
        try (Socket socket = new Socket("127.0.0.1", 3307)) {
            databaseAvailable = true;
            logger.info("✓ Database connection available (SSH tunnel running)");
        } catch (Exception e) {
            databaseAvailable = false;
            logger.info("✗ Database not available - SSH tunnel not running");
            logger.info("  Start tunnel with: ssh -L 3307:127.0.0.1:3306 webmaster@132.72.50.53");
        }
        assumeTrue(databaseAvailable, "SSH tunnel must be running for integration tests");
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    // Repositories
    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected SeminarSlotRepository seminarSlotRepository;

    @Autowired
    protected SeminarSlotRegistrationRepository registrationRepository;

    @Autowired
    protected SessionRepository sessionRepository;

    @Autowired
    protected SeminarRepository seminarRepository;

    @Autowired
    protected AttendanceRepository attendanceRepository;

    // Services
    @Autowired
    protected PresenterHomeService presenterHomeService;

    @Autowired
    protected AttendanceService attendanceService;

    @Autowired
    protected SessionService sessionService;

    protected String getBaseUrl() {
        return "http://localhost:" + port;
    }

    protected String getApiUrl(String path) {
        return getBaseUrl() + "/api/v1" + path;
    }
}
