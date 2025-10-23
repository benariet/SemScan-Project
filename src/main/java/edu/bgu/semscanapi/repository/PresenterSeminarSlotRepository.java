package edu.bgu.semscanapi.repository;

import edu.bgu.semscanapi.entity.PresenterSeminarSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PresenterSeminarSlotRepository extends JpaRepository<PresenterSeminarSlot, Long> {
    List<PresenterSeminarSlot> findByPresenterSeminarIdOrderByWeekdayAscStartHourAsc(String presenterSeminarId);
    boolean existsByPresenterSeminarIdAndWeekdayAndStartHourAndEndHour(String presenterSeminarId, Integer weekday, Integer startHour, Integer endHour);
    
    /**
     * Find presenter seminar slot by presenter seminar slot ID (string ID)
     */
    Optional<PresenterSeminarSlot> findByPresenterSeminarSlotId(String presenterSeminarSlotId);
}


