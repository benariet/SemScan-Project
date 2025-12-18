package edu.bgu.semscanapi.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing a waiting list promotion with expiration tracking
 * Tracks when a user was promoted from waiting list and when their approval window expires
 */
@Entity
@Table(name = "waiting_list_promotions")
public class WaitingListPromotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "promotion_id")
    private Long promotionId;

    @Column(name = "slot_id", nullable = false)
    private Long slotId;

    @Column(name = "presenter_username", nullable = false, length = 50)
    private String presenterUsername;

    @Column(name = "registration_slot_id", nullable = false)
    private Long registrationSlotId;

    @Column(name = "registration_presenter_username", nullable = false, length = 50)
    private String registrationPresenterUsername;

    @Column(name = "promoted_at", nullable = false, updatable = false)
    private LocalDateTime promotedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PromotionStatus status;

    @PrePersist
    protected void onCreate() {
        if (promotedAt == null) {
            promotedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = PromotionStatus.PENDING;
        }
    }

    public enum PromotionStatus {
        PENDING,
        APPROVED,
        DECLINED,
        EXPIRED
    }

    // Getters and Setters
    public Long getPromotionId() {
        return promotionId;
    }

    public void setPromotionId(Long promotionId) {
        this.promotionId = promotionId;
    }

    public Long getSlotId() {
        return slotId;
    }

    public void setSlotId(Long slotId) {
        this.slotId = slotId;
    }

    public String getPresenterUsername() {
        return presenterUsername;
    }

    public void setPresenterUsername(String presenterUsername) {
        this.presenterUsername = presenterUsername;
    }

    public Long getRegistrationSlotId() {
        return registrationSlotId;
    }

    public void setRegistrationSlotId(Long registrationSlotId) {
        this.registrationSlotId = registrationSlotId;
    }

    public String getRegistrationPresenterUsername() {
        return registrationPresenterUsername;
    }

    public void setRegistrationPresenterUsername(String registrationPresenterUsername) {
        this.registrationPresenterUsername = registrationPresenterUsername;
    }

    public LocalDateTime getPromotedAt() {
        return promotedAt;
    }

    public void setPromotedAt(LocalDateTime promotedAt) {
        this.promotedAt = promotedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public PromotionStatus getStatus() {
        return status;
    }

    public void setStatus(PromotionStatus status) {
        this.status = status;
    }
}
