package edu.bgu.semscanapi.repository;

import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for User entity operations
 * Provides data access methods with comprehensive logging
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Logger logger = LoggerUtil.getLogger(UserRepository.class);
    
    /**
     * Find user by email
     */
    Optional<User> findByEmail(String email);
    
    /**
     * Find user by BGU username
     */
    Optional<User> findByBguUsername(String bguUsername);

    /**
     * Find presenters (users flagged as presenters)
     */
    List<User> findByIsPresenterTrue();

    /**
     * Find participants (users flagged as participants)
     */
    List<User> findByIsParticipantTrue();
    
    /**
     * Check if email exists
     */
    boolean existsByEmailIgnoreCase(String email);
    
    /**
     * Check if BGU username exists
     */
    boolean existsByBguUsername(String bguUsername);

    boolean existsByBguUsernameIgnoreCase(String bguUsername);
    
    Optional<User> findByBguUsernameIgnoreCase(String bguUsername);
    
    List<User> findByBguUsernameIn(List<String> usernames);
    
    /**
     * Find users by first name and last name
     */
    List<User> findByFirstNameAndLastName(String firstName, String lastName);

    @Query("SELECT u FROM User u WHERE LOWER(u.firstName) LIKE LOWER(CONCAT('%', :name, '%')) OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<User> searchByName(@Param("name") String name);
}
