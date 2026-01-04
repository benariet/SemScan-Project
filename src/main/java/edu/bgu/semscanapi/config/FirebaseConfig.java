package edu.bgu.semscanapi.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * Firebase configuration for push notifications
 * Requires FIREBASE_SERVICE_ACCOUNT_KEY environment variable pointing to service account JSON file
 */
@Configuration
public class FirebaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${firebase.service-account-key:}")
    private String serviceAccountKeyPath;

    private boolean initialized = false;

    @PostConstruct
    public void initialize() {
        if (serviceAccountKeyPath == null || serviceAccountKeyPath.trim().isEmpty()) {
            logger.warn("Firebase service account key path not configured. Push notifications will be disabled.");
            logger.warn("Set FIREBASE_SERVICE_ACCOUNT_KEY environment variable or firebase.service-account-key property.");
            return;
        }

        try {
            if (FirebaseApp.getApps().isEmpty()) {
                FileInputStream serviceAccount = new FileInputStream(serviceAccountKeyPath);

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();

                FirebaseApp.initializeApp(options);
                initialized = true;
                logger.info("Firebase initialized successfully for push notifications");
            } else {
                initialized = true;
                logger.info("Firebase already initialized");
            }
        } catch (IOException e) {
            logger.error("Failed to initialize Firebase: {}", e.getMessage());
            logger.error("Push notifications will be disabled. Check FIREBASE_SERVICE_ACCOUNT_KEY path: {}", serviceAccountKeyPath);
        }
    }

    public boolean isInitialized() {
        return initialized;
    }
}
