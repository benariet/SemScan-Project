package edu.bgu.semscanapi.repository;

import edu.bgu.semscanapi.entity.SeminarSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SeminarSlotRepository extends JpaRepository<SeminarSlot, Long> {

    List<SeminarSlot> findBySlotDateGreaterThanEqualOrderBySlotDateAscStartTimeAsc(LocalDate fromDate);

    List<SeminarSlot> findAllByOrderBySlotDateAscStartTimeAsc();
}


