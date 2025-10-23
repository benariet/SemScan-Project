package edu.bgu.semscanapi.repository;

import edu.bgu.semscanapi.entity.PresenterSeminar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PresenterSeminarRepository extends JpaRepository<PresenterSeminar, Long> {
    List<PresenterSeminar> findByPresenterIdOrderByCreatedAtDesc(String presenterId);
    
    /**
     * Find presenter seminar by presenter seminar ID (string ID)
     */
    Optional<PresenterSeminar> findByPresenterSeminarId(String presenterSeminarId);
}


