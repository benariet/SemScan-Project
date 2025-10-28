package edu.bgu.semscanapi.repository;

import edu.bgu.semscanapi.entity.Seminar;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
/**
 * Repository interface for Seminar entity operations
 * Provides data access methods with comprehensive logging
 */
@Repository
public interface SeminarRepository extends JpaRepository<Seminar, Long> {
    
    Logger logger = LoggerUtil.getLogger(SeminarRepository.class);
    
    /**
     * Find seminars by presenter ID
     */
    List<Seminar> findByPresenterId(Long presenterId);
    
    /**
     * Find seminars by presenter ID and active status
     */
    /**
     * Find seminars by name containing (case-insensitive)
     */
    @Query("SELECT s FROM Seminar s WHERE LOWER(s.seminarName) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Seminar> findByNameContainingIgnoreCase(@Param("name") String name);
    
    /**
     * Count seminars by presenter
     */
    long countByPresenterId(Long presenterId);
    
    /**
     * Find all seminars with presenter users
     */
    @Query("SELECT s FROM Seminar s JOIN User u ON s.presenterId = u.userId WHERE u.role = 'PRESENTER'")
    List<Seminar> findAllWithPresenters();
}
