package edu.bgu.semscanapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify database connection to server
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class DatabaseConnectionTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void testDatabaseConnection() throws Exception {
        assertNotNull(dataSource, "DataSource should be configured");
        
        try (Connection connection = dataSource.getConnection()) {
            assertNotNull(connection, "Connection should not be null");
            assertFalse(connection.isClosed(), "Connection should be open");
            
            DatabaseMetaData metaData = connection.getMetaData();
            String url = metaData.getURL();
            String username = metaData.getUserName();
            
            System.out.println("✅ Database connection successful!");
            System.out.println("   URL: " + url);
            System.out.println("   Username: " + username);
            System.out.println("   Database: " + metaData.getDatabaseProductName() + " " + metaData.getDatabaseProductVersion());
        }
    }
}


