package edu.bgu.semscanapi.repository;

import edu.bgu.semscanapi.entity.Attendance;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Attendance entity operations
 * Provides data access methods with comprehensive logging
 */
@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    
    Logger logger = LoggerUtil.getLogger(AttendanceRepository.class);
    
    /**
     * Find attendance records by session ID
     */
    List<Attendance> findBySessionId(String sessionId);
    
    /**
     * Find attendance by attendance ID (string ID)
     */
    Optional<Attendance> findByAttendanceId(String attendanceId);
    
    /**
     * Find attendance records by student ID
     */
    List<Attendance> findByStudentId(String studentId);
    
    /**
     * Find attendance record by session ID and student ID
     */
    Optional<Attendance> findBySessionIdAndStudentId(String sessionId, String studentId);
    
    /**
     * Check if student attended a specific session
     */
    boolean existsBySessionIdAndStudentId(String sessionId, String studentId);
    
    /**
     * Find attendance records by method
     */
    List<Attendance> findByMethod(Attendance.AttendanceMethod method);
    
    /**
     * Find attendance records within date range
     */
    @Query("SELECT a FROM Attendance a WHERE a.attendanceTime BETWEEN :startDate AND :endDate")
    List<Attendance> findAttendanceBetweenDates(@Param("startDate") LocalDateTime startDate, 
                                               @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find attendance records by session and method
     */
    List<Attendance> findBySessionIdAndMethod(String sessionId, Attendance.AttendanceMethod method);
    
    /**
     * Count attendance records by session
     */
    long countBySessionId(String sessionId);
    
    /**
     * Count attendance records by student
     */
    long countByStudentId(String studentId);
    
    /**
     * Count attendance records by method
     */
    long countByMethod(Attendance.AttendanceMethod method);
    
    /**
     * Find attendance records for a student within date range
     */
    @Query("SELECT a FROM Attendance a WHERE a.studentId = :studentId AND a.attendanceTime BETWEEN :startDate AND :endDate")
    List<Attendance> findStudentAttendanceBetweenDates(@Param("studentId") String studentId,
                                                      @Param("startDate") LocalDateTime startDate, 
                                                      @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find attendance records for a session within date range
     */
    @Query("SELECT a FROM Attendance a WHERE a.sessionId = :sessionId AND a.attendanceTime BETWEEN :startDate AND :endDate")
    List<Attendance> findSessionAttendanceBetweenDates(@Param("sessionId") String sessionId,
                                                      @Param("startDate") LocalDateTime startDate, 
                                                      @Param("endDate") LocalDateTime endDate);
    
    // Manual Attendance Request Methods
    
    /**
     * Find pending manual attendance requests for a session
     */
    @Query("SELECT a FROM Attendance a WHERE a.sessionId = :sessionId AND a.requestStatus = 'PENDING_APPROVAL'")
    List<Attendance> findPendingRequestsBySessionId(@Param("sessionId") String sessionId);
    
    /**
     * Find pending manual attendance requests for a student
     */
    @Query("SELECT a FROM Attendance a WHERE a.studentId = :studentId AND a.requestStatus = 'PENDING_APPROVAL'")
    List<Attendance> findPendingRequestsByStudentId(@Param("studentId") String studentId);
    
    /**
     * Check if student has pending request for a session
     */
    @Query("SELECT COUNT(a) > 0 FROM Attendance a WHERE a.sessionId = :sessionId AND a.studentId = :studentId AND a.requestStatus = 'PENDING_APPROVAL'")
    boolean hasPendingRequest(@Param("sessionId") String sessionId, @Param("studentId") String studentId);
    
    /**
     * Find attendance records by request status
     */
    List<Attendance> findByRequestStatus(Attendance.RequestStatus requestStatus);
    
    /**
     * Count pending requests for a session
     */
    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.sessionId = :sessionId AND a.requestStatus = 'PENDING_APPROVAL'")
    long countPendingRequestsBySessionId(@Param("sessionId") String sessionId);
    
    /**
     * Find attendance records with student information for pending requests
     */
    @Query("SELECT a FROM Attendance a JOIN User u ON a.studentId = u.userId WHERE a.sessionId = :sessionId AND a.requestStatus = 'PENDING_APPROVAL'")
    List<Attendance> findPendingRequestsWithStudentInfo(@Param("sessionId") String sessionId);
}
