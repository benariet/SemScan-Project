package edu.bgu.semscanapi.repository;

import edu.bgu.semscanapi.entity.PresenterSeminar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PresenterSeminarRepository extends JpaRepository<PresenterSeminar, String> {
    List<PresenterSeminar> findByPresenterIdOrderByCreatedAtDesc(String presenterId);
}


