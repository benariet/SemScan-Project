package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.entity.Seminar;
import edu.bgu.semscanapi.entity.Session;
import edu.bgu.semscanapi.entity.Attendance;
import edu.bgu.semscanapi.entity.PresenterSeminar;
import edu.bgu.semscanapi.entity.PresenterSeminarSlot;
import edu.bgu.semscanapi.repository.UserRepository;
import edu.bgu.semscanapi.repository.SeminarRepository;
import edu.bgu.semscanapi.repository.SessionRepository;
import edu.bgu.semscanapi.repository.AttendanceRepository;
import edu.bgu.semscanapi.repository.PresenterSeminarRepository;
import edu.bgu.semscanapi.repository.PresenterSeminarSlotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service for mapping between String IDs (API) and Long IDs (Database)
 * This service handles the conversion between the dual ID system
 */
@Service
public class IdMappingService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private SeminarRepository seminarRepository;
    
    @Autowired
    private SessionRepository sessionRepository;
    
    @Autowired
    private AttendanceRepository attendanceRepository;
    
    @Autowired
    private PresenterSeminarRepository presenterSeminarRepository;
    
    @Autowired
    private PresenterSeminarSlotRepository presenterSeminarSlotRepository;
    
    /**
     * Convert String user ID to Long user ID
     */
    public Optional<Long> getUserIdFromStringId(String userId) {
        return userRepository.findByUserId(userId).map(User::getId);
    }
    
    /**
     * Convert String seminar ID to Long seminar ID
     */
    public Optional<Long> getSeminarIdFromStringId(String seminarId) {
        return seminarRepository.findBySeminarId(seminarId).map(Seminar::getId);
    }
    
    /**
     * Convert String session ID to Long session ID
     */
    public Optional<Long> getSessionIdFromStringId(String sessionId) {
        return sessionRepository.findBySessionId(sessionId).map(Session::getId);
    }
    
    /**
     * Convert String attendance ID to Long attendance ID
     */
    public Optional<Long> getAttendanceIdFromStringId(String attendanceId) {
        return attendanceRepository.findByAttendanceId(attendanceId).map(Attendance::getId);
    }
    
    /**
     * Convert String presenter seminar ID to Long presenter seminar ID
     */
    public Optional<Long> getPresenterSeminarIdFromStringId(String presenterSeminarId) {
        return presenterSeminarRepository.findByPresenterSeminarId(presenterSeminarId).map(PresenterSeminar::getId);
    }
    
    /**
     * Convert String presenter seminar slot ID to Long presenter seminar slot ID
     */
    public Optional<Long> getPresenterSeminarSlotIdFromStringId(String presenterSeminarSlotId) {
        return presenterSeminarSlotRepository.findByPresenterSeminarSlotId(presenterSeminarSlotId).map(PresenterSeminarSlot::getId);
    }
    
    /**
     * Convert Long user ID to String user ID
     */
    public Optional<String> getStringUserIdFromId(Long id) {
        return userRepository.findById(id).map(User::getUserId);
    }
    
    /**
     * Convert Long seminar ID to String seminar ID
     */
    public Optional<String> getStringSeminarIdFromId(Long id) {
        return seminarRepository.findById(id).map(Seminar::getSeminarId);
    }
    
    /**
     * Convert Long session ID to String session ID
     */
    public Optional<String> getStringSessionIdFromId(Long id) {
        return sessionRepository.findById(id).map(Session::getSessionId);
    }
    
    /**
     * Convert Long attendance ID to String attendance ID
     */
    public Optional<String> getStringAttendanceIdFromId(Long id) {
        return attendanceRepository.findById(id).map(Attendance::getAttendanceId);
    }
    
    /**
     * Convert Long presenter seminar ID to String presenter seminar ID
     */
    public Optional<String> getStringPresenterSeminarIdFromId(Long id) {
        return presenterSeminarRepository.findById(id).map(PresenterSeminar::getPresenterSeminarId);
    }
    
    /**
     * Convert Long presenter seminar slot ID to String presenter seminar slot ID
     */
    public Optional<String> getStringPresenterSeminarSlotIdFromId(Long id) {
        return presenterSeminarSlotRepository.findById(id).map(PresenterSeminarSlot::getPresenterSeminarSlotId);
    }
}
