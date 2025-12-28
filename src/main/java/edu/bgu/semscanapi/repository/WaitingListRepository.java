package edu.bgu.semscanapi.repository;

import edu.bgu.semscanapi.entity.WaitingListEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WaitingListRepository extends JpaRepository<WaitingListEntry, Long> {

    List<WaitingListEntry> findBySlotIdOrderByPositionAsc(Long slotId);

    Optional<WaitingListEntry> findBySlotIdAndPresenterUsername(Long slotId, String presenterUsername);

    boolean existsBySlotIdAndPresenterUsername(Long slotId, String presenterUsername);

    long countBySlotId(Long slotId);

    @Modifying
    @Query("UPDATE WaitingListEntry w SET w.position = w.position - 1 WHERE w.slotId = :slotId AND w.position > :position")
    void decrementPositionsAfter(@Param("slotId") Long slotId, @Param("position") Integer position);

    @Modifying
    @Query("DELETE FROM WaitingListEntry w WHERE w.slotId = :slotId AND w.presenterUsername = :presenterUsername")
    void deleteBySlotIdAndPresenterUsername(@Param("slotId") Long slotId, @Param("presenterUsername") String presenterUsername);

    // Check if user is on any waiting list (across all slots)
    boolean existsByPresenterUsername(String presenterUsername);

    // Get all waiting list entries for a user (across all slots)
    List<WaitingListEntry> findByPresenterUsername(String presenterUsername);

    // Find by promotion token
    Optional<WaitingListEntry> findByPromotionToken(String promotionToken);

    // Find entries with expired promotion offers (token expired but still on waiting list)
    @Query("SELECT w FROM WaitingListEntry w WHERE w.promotionToken IS NOT NULL AND w.promotionTokenExpiresAt < :now")
    List<WaitingListEntry> findExpiredPromotionOffers(@Param("now") LocalDateTime now);

    // Find entries that have a pending promotion offer (not expired)
    @Query("SELECT w FROM WaitingListEntry w WHERE w.promotionToken IS NOT NULL AND w.promotionTokenExpiresAt > :now")
    List<WaitingListEntry> findPendingPromotionOffers(@Param("now") LocalDateTime now);

    // Find next entry without pending promotion (for promoting next person)
    @Query("SELECT w FROM WaitingListEntry w WHERE w.slotId = :slotId AND (w.promotionToken IS NULL OR w.promotionTokenExpiresAt < :now) ORDER BY w.position ASC")
    List<WaitingListEntry> findAvailableForPromotion(@Param("slotId") Long slotId, @Param("now") LocalDateTime now);
}

