package edu.bgu.semscanapi.repository;

import edu.bgu.semscanapi.entity.AnnounceConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnnounceConfigRepository extends JpaRepository<AnnounceConfig, Integer> {
    // Singleton table - always use findById(1)
}
