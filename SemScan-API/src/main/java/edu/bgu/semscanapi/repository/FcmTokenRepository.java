package edu.bgu.semscanapi.repository;

import edu.bgu.semscanapi.entity.FcmToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    Optional<FcmToken> findByBguUsername(String bguUsername);

    void deleteByBguUsername(String bguUsername);

    boolean existsByBguUsername(String bguUsername);
}
