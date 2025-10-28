package edu.bgu.semscanapi.repository;

import edu.bgu.semscanapi.entity.PresenterSeminarSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PresenterSeminarSlotRepository extends JpaRepository<PresenterSeminarSlot, Long> {
    List<PresenterSeminarSlot> findByPresenterSeminarIdOrderByWeekdayAscStartHourAsc(Long presenterSeminarId);
    boolean existsByPresenterSeminarIdAndWeekdayAndStartHourAndEndHour(Long presenterSeminarId, Integer weekday, Integer startHour, Integer endHour);
}


