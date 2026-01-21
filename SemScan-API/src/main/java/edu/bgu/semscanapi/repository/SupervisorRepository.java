package edu.bgu.semscanapi.repository;

import edu.bgu.semscanapi.entity.Supervisor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SupervisorRepository extends JpaRepository<Supervisor, Long> {

    Optional<Supervisor> findByEmail(String email);

    boolean existsByEmail(String email);
}
