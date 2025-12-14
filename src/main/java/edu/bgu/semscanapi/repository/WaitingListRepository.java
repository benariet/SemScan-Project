package edu.bgu.semscanapi.repository;

import edu.bgu.semscanapi.entity.WaitingListEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}

