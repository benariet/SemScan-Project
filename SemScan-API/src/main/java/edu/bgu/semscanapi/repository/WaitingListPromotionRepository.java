package edu.bgu.semscanapi.repository;

import edu.bgu.semscanapi.entity.WaitingListPromotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WaitingListPromotionRepository extends JpaRepository<WaitingListPromotion, Long> {

    @Query("SELECT w FROM WaitingListPromotion w WHERE w.expiresAt < :now AND w.status = :status")
    List<WaitingListPromotion> findByExpiresAtBeforeAndStatus(@Param("now") LocalDateTime now, 
                                                                @Param("status") WaitingListPromotion.PromotionStatus status);

    Optional<WaitingListPromotion> findByRegistrationSlotIdAndRegistrationPresenterUsername(
            Long registrationSlotId, String registrationPresenterUsername);
}
