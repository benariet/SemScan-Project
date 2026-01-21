package edu.bgu.semscanapi.dto;

import edu.bgu.semscanapi.entity.Session;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * DTO for Session with enriched presenter and topic information.
 * Used for open sessions endpoint to provide mobile app with all needed data.
 */
public class SessionDTO {

    private static final ZoneId ISRAEL_TIMEZONE = ZoneId.of("Asia/Jerusalem");

    private Long sessionId;
    private Long seminarId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private String location;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Enriched fields
    private String presenterName;
    private String presenterUsername;
    private String topic;
    private Long startTimeEpoch;  // Epoch milliseconds for mobile

    public SessionDTO() {
    }

    /**
     * Create DTO from Session entity with enriched data
     */
    public static SessionDTO fromSession(Session session, String presenterName, String presenterUsername, String topic) {
        SessionDTO dto = new SessionDTO();
        dto.setSessionId(session.getSessionId());
        dto.setSeminarId(session.getSeminarId());
        dto.setStartTime(session.getStartTime());
        dto.setEndTime(session.getEndTime());
        dto.setStatus(session.getStatus() != null ? session.getStatus().name() : null);
        dto.setLocation(session.getLocation());
        dto.setCreatedAt(session.getCreatedAt());
        dto.setUpdatedAt(session.getUpdatedAt());

        // Enriched fields
        dto.setPresenterName(presenterName);
        dto.setPresenterUsername(presenterUsername);
        dto.setTopic(topic);

        // Convert startTime to epoch milliseconds using Israel timezone
        if (session.getStartTime() != null) {
            ZonedDateTime zonedDateTime = session.getStartTime().atZone(ISRAEL_TIMEZONE);
            dto.setStartTimeEpoch(zonedDateTime.toInstant().toEpochMilli());
        }

        return dto;
    }

    // Getters and setters

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public Long getSeminarId() {
        return seminarId;
    }

    public void setSeminarId(Long seminarId) {
        this.seminarId = seminarId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getPresenterName() {
        return presenterName;
    }

    public void setPresenterName(String presenterName) {
        this.presenterName = presenterName;
    }

    public String getPresenterUsername() {
        return presenterUsername;
    }

    public void setPresenterUsername(String presenterUsername) {
        this.presenterUsername = presenterUsername;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public Long getStartTimeEpoch() {
        return startTimeEpoch;
    }

    public void setStartTimeEpoch(Long startTimeEpoch) {
        this.startTimeEpoch = startTimeEpoch;
    }
}
