package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.entity.AnnounceConfig;
import edu.bgu.semscanapi.repository.AnnounceConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/announcement")
public class AnnouncementController {

    private static final Logger logger = LoggerFactory.getLogger(AnnouncementController.class);

    @Autowired
    private AnnounceConfigRepository announceConfigRepository;

    /**
     * Get the current announcement configuration.
     * Returns announcement details if active, or just isActive=false if not.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAnnouncement() {
        logger.info("Fetching announcement config");

        Map<String, Object> response = new HashMap<>();

        try {
            AnnounceConfig config = announceConfigRepository.findById(1).orElse(null);

            if (config == null) {
                // Create default config if not exists
                config = new AnnounceConfig();
                config.setId(1);
                config.setIsActive(false);
                config.setVersion(1);
                config.setTitle("System Message");
                config.setMessage("No active announcements.");
                config.setIsBlocking(false);
                config = announceConfigRepository.save(config);
                logger.info("Created default announcement config");
            }

            response.put("isActive", config.getIsActive());
            response.put("version", config.getVersion());
            response.put("title", config.getTitle());
            response.put("message", config.getMessage());
            response.put("isBlocking", config.getIsBlocking());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error fetching announcement config", e);
            response.put("isActive", false);
            response.put("version", 0);
            response.put("error", "Failed to fetch announcement");
            return ResponseEntity.ok(response); // Return ok with isActive=false so app doesn't crash
        }
    }
}
