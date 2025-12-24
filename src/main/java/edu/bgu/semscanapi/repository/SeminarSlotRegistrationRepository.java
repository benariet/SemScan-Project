package edu.bgu.semscanapi.repository;

import edu.bgu.semscanapi.entity.ApprovalStatus;
import edu.bgu.semscanapi.entity.SeminarSlotRegistration;
import edu.bgu.semscanapi.entity.SeminarSlotRegistrationId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SeminarSlotRegistrationRepository extends JpaRepository<SeminarSlotRegistration, SeminarSlotRegistrationId> {

    List<SeminarSlotRegistration> findByIdPresenterUsername(String presenterUsername);

    List<SeminarSlotRegistration> findByIdSlotId(Long slotId);

    List<SeminarSlotRegistration> findByIdSlotIdIn(Collection<Long> slotIds);

    boolean existsByIdPresenterUsername(String presenterUsername);

    boolean existsByIdSlotIdAndIdPresenterUsername(Long slotId, String presenterUsername);

    long countByIdSlotId(Long slotId);

    Optional<SeminarSlotRegistration> findByApprovalToken(String approvalToken);

    List<SeminarSlotRegistration> findByApprovalStatus(ApprovalStatus approvalStatus);

    @Query("SELECT r FROM SeminarSlotRegistration r WHERE r.approvalStatus = :status AND r.approvalTokenExpiresAt < :now")
    List<SeminarSlotRegistration> findExpiredPendingRegistrations(@Param("status") ApprovalStatus status, @Param("now") LocalDateTime now);

    List<SeminarSlotRegistration> findByIdSlotIdAndApprovalStatus(Long slotId, ApprovalStatus approvalStatus);

    long countByIdSlotIdAndApprovalStatus(Long slotId, ApprovalStatus approvalStatus);

    /**
     * Find registrations pending supervisor approval with valid tokens
     * Used for daily supervisor reminder emails
     */
    @Query("SELECT r FROM SeminarSlotRegistration r WHERE r.approvalStatus = :status " +
           "AND r.approvalToken IS NOT NULL AND r.approvalTokenExpiresAt > :now")
    List<SeminarSlotRegistration> findByApprovalStatusAndApprovalTokenIsNotNullAndApprovalTokenExpiresAtAfter(
        @Param("status") ApprovalStatus status, @Param("now") LocalDateTime now);

    /**
     * Find registrations pending supervisor approval with valid tokens (string version for scheduler)
     */
    @Query("SELECT r FROM SeminarSlotRegistration r WHERE CAST(r.approvalStatus AS string) = :status " +
           "AND r.approvalToken IS NOT NULL AND r.approvalTokenExpiresAt > :now")
    List<SeminarSlotRegistration> findByApprovalStatusStringAndApprovalTokenIsNotNullAndApprovalTokenExpiresAtAfter(
        @Param("status") String status, @Param("now") LocalDateTime now);

    /**
     * Find PENDING registrations with tokens expiring within a time window
     * Used for expiration warning emails - only sends to pending registrations
     */
    @Query("SELECT r FROM SeminarSlotRegistration r WHERE r.approvalStatus = :status " +
           "AND r.approvalToken IS NOT NULL AND r.approvalTokenExpiresAt BETWEEN :start AND :end")
    List<SeminarSlotRegistration> findPendingByApprovalTokenExpiresAtBetween(
        @Param("status") ApprovalStatus status, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}


