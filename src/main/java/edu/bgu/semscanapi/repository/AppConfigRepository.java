package edu.bgu.semscanapi.repository;

import edu.bgu.semscanapi.entity.AppConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppConfigRepository extends JpaRepository<AppConfig, Long> {

    Optional<AppConfig> findByConfigKey(String configKey);

    List<AppConfig> findByTargetSystemIn(List<AppConfig.TargetSystem> targetSystems);

    List<AppConfig> findByTagsContaining(String tag);
}
