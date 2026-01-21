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

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    Logger logger = LoggerUtil.getLogger(AttendanceRepository.class);

    /**
     * Find all attendance records for a specific session ID
     * CRITICAL: This must query by session_id directly, not by slot_id
     * This ensures that when multiple sessions exist for the same slot,
     * each session's attendance records are returned independently
     */
    @Query("SELECT a FROM Attendance a WHERE a.sessionId = :sessionId ORDER BY a.attendanceTime ASC")
    List<Attendance> findBySessionId(@Param("sessionId") Long sessionId);

    List<Attendance> findByStudentUsername(String studentUsername);

    Optional<Attendance> findBySessionIdAndStudentUsername(Long sessionId, String studentUsername);

    boolean existsBySessionIdAndStudentUsername(Long sessionId, String studentUsername);
    
    @Query("SELECT a FROM Attendance a WHERE a.sessionId = :sessionId AND LOWER(a.studentUsername) = LOWER(:studentUsername)")
    Optional<Attendance> findBySessionIdAndStudentUsernameIgnoreCase(@Param("sessionId") Long sessionId, @Param("studentUsername") String studentUsername);
    
    @Query("SELECT COUNT(a) > 0 FROM Attendance a WHERE a.sessionId = :sessionId AND LOWER(a.studentUsername) = LOWER(:studentUsername)")
    boolean existsBySessionIdAndStudentUsernameIgnoreCase(@Param("sessionId") Long sessionId, @Param("studentUsername") String studentUsername);

    @Query("SELECT a FROM Attendance a WHERE a.sessionId = :sessionId AND a.requestStatus = edu.bgu.semscanapi.entity.Attendance$RequestStatus.PENDING_APPROVAL")
    List<Attendance> findPendingRequestsBySessionId(@Param("sessionId") Long sessionId);

    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.sessionId = :sessionId AND a.requestStatus = edu.bgu.semscanapi.entity.Attendance$RequestStatus.PENDING_APPROVAL")
    long countPendingRequestsBySessionId(@Param("sessionId") Long sessionId);

    List<Attendance> findByMethod(Attendance.AttendanceMethod method);

    @Query("SELECT a FROM Attendance a WHERE a.attendanceTime BETWEEN :start AND :end")
    List<Attendance> findAttendanceBetweenDates(@Param("start") LocalDateTime start,
                                               @Param("end") LocalDateTime end);

    long countBySessionId(Long sessionId);

    long countByMethod(Attendance.AttendanceMethod method);
    
    @Query(value = "SELECT DISTINCT CAST(DATE(attendance_time) AS DATE) FROM attendance WHERE student_username = :studentUsername ORDER BY DATE(attendance_time) DESC", nativeQuery = true)
    List<Object> findDistinctAttendanceDatesByStudent(@Param("studentUsername") String studentUsername);
    
    long countByStudentUsername(String studentUsername);
    
    /**
     * Find attendance records with topic information for a student
     * Joins with sessions, seminars, slots, and slot_registration to get topic
     * Matches slot_registration by slot_id AND presenter_username to get the correct topic
     */
    @Query(value = "SELECT " +
            "a.attendance_id, a.session_id, a.student_username, a.attendance_time, " +
            "a.method, a.request_status, a.manual_reason, a.requested_at, a.approved_at, " +
            "a.approved_by_username, a.device_id, a.auto_flags, a.notes, a.created_at, a.updated_at, " +
            "reg.topic " +
            "FROM attendance a " +
            "INNER JOIN sessions s ON a.session_id = s.session_id " +
            "INNER JOIN seminars sem ON s.seminar_id = sem.seminar_id " +
            "LEFT JOIN slots sl ON s.session_id = sl.legacy_session_id " +
            "LEFT JOIN slot_registration reg ON sl.slot_id = reg.slot_id " +
            "    AND reg.presenter_username = sem.presenter_username " +
            "    AND reg.approval_status = 'APPROVED' " +
            "WHERE a.student_username = :studentUsername " +
            "ORDER BY a.attendance_time DESC", nativeQuery = true)
    List<Object[]> findAttendanceWithDetailsByStudent(@Param("studentUsername") String studentUsername);
}
