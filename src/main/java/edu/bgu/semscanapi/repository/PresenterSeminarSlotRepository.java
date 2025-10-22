package edu.bgu.semscanapi.repository;

import edu.bgu.semscanapi.entity.PresenterSeminarSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PresenterSeminarSlotRepository extends JpaRepository<PresenterSeminarSlot, String> {
    List<PresenterSeminarSlot> findByPresenterSeminarIdOrderByWeekdayAscStartHourAsc(String presenterSeminarId);
    boolean existsByPresenterSeminarIdAndWeekdayAndStartHourAndEndHour(String presenterSeminarId, Integer weekday, Integer startHour, Integer endHour);
}


