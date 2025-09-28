package edu.bgu.semscanapi.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "presenter_api_keys")
public class PresenterApiKey {

    @Id
    @Column(name = "api_key_id", length = 36)
    private String apiKeyId;

    @Column(name = "presenter_id", length = 36, nullable = false)
    private String presenterId;

    @Column(name = "api_key", length = 255, unique = true, nullable = false)
    private String apiKey;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "is_active")
    private Boolean isActive;

    public PresenterApiKey() {
    }

    // Getters and Setters
    public String getApiKeyId() {
        return apiKeyId;
    }

    public void setApiKeyId(String apiKeyId) {
        this.apiKeyId = apiKeyId;
    }

    public String getPresenterId() {
        return presenterId;
    }

    public void setPresenterId(String presenterId) {
        this.presenterId = presenterId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PresenterApiKey that = (PresenterApiKey) o;
        return Objects.equals(apiKeyId, that.apiKeyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(apiKeyId);
    }

    @Override
    public String toString() {
        return "PresenterApiKey{" +
                "apiKeyId='" + apiKeyId + '\'' +
                ", presenterId='" + presenterId + '\'' +
                ", apiKey='" + apiKey + '\'' +
                ", createdAt=" + createdAt +
                ", isActive=" + isActive +
                '}';
    }
}
