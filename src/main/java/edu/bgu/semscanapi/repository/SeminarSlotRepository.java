package edu.bgu.semscanapi.repository;

import edu.bgu.semscanapi.entity.SeminarSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SeminarSlotRepository extends JpaRepository<SeminarSlot, Long> {

    List<SeminarSlot> findBySlotDateGreaterThanEqualOrderBySlotDateAscStartTimeAsc(LocalDate fromDate);

    List<SeminarSlot> findAllByOrderBySlotDateAscStartTimeAsc();

    Optional<SeminarSlot> findByLegacySessionId(Long legacySessionId);

    /**
     * Find slots where attendance_closes_at has passed but attendance fields are still set.
     * Used by SessionAutoCloseJob to clean up expired sessions.
     */
    @Query("SELECT s FROM SeminarSlot s WHERE s.attendanceClosesAt IS NOT NULL AND s.attendanceClosesAt < :now")
    List<SeminarSlot> findSlotsWithExpiredAttendance(@Param("now") LocalDateTime now);
}


