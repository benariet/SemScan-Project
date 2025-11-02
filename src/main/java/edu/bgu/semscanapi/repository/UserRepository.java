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
     * Find user by student ID
     */
    Optional<User> findByStudentId(String studentId);
    
    /**
     * Find user by BGU username
     */
    Optional<User> findByBguUsername(String bguUsername);

    /**
     * Find users by role
     */
    List<User> findByRole(User.UserRole role);
    
    /**
     * Find presenters (users with PRESENTER role)
     */
    @Query("SELECT u FROM User u WHERE u.role = 'PRESENTER'")
    List<User> findPresenters();
    
    /**
     * Find students (users with STUDENT role)
     */
    @Query("SELECT u FROM User u WHERE u.role = 'STUDENT'")
    List<User> findStudents();
    
    /**
     * Check if email exists
     */
    boolean existsByEmail(String email);
    
    /**
     * Check if student ID exists
     */
    boolean existsByStudentId(String studentId);
    
    /**
     * Check if BGU username exists
     */
    boolean existsByBguUsername(String bguUsername);
    
    /**
     * Find users by first name and last name
     */
    List<User> findByFirstNameAndLastName(String firstName, String lastName);

    @Query("SELECT u FROM User u WHERE LOWER(u.firstName) LIKE LOWER(CONCAT('%', :name, '%')) OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<User> searchByName(@Param("name") String name);
}
