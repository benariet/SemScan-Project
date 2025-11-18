package edu.bgu.semscanapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Basic test class for SemScan API Application
 * Optimized to load minimal context for faster test execution
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class SemScanApiApplicationTests {

    @Test
    void contextLoads() {
        // This test verifies that the Spring context loads successfully
        // Using WebEnvironment.NONE to avoid loading web context (faster)
    }

}
