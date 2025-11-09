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

    List<Attendance> findBySessionId(Long sessionId);

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
}
