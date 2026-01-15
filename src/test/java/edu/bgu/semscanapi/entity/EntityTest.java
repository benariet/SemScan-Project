package edu.bgu.semscanapi.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for entity classes.
 * Covers getters/setters, equals/hashCode, toString, and enum values.
 */
class EntityTest {

    // ==================== User Entity Tests ====================
    @Nested
    class UserEntityTest {

        @Test
        void testUserGettersAndSetters() {
            User user = new User();
            LocalDateTime now = LocalDateTime.now();

            user.setId(1L);
            user.setFirstName("John");
            user.setLastName("Doe");
            user.setEmail("john.doe@bgu.ac.il");
            user.setBguUsername("johndoe");
            user.setDegree(User.Degree.PhD);
            user.setIsPresenter(true);
            user.setIsParticipant(false);
            user.setNationalIdNumber("123456789");
            user.setSeminarAbstract("Test abstract");
            user.setCreatedAt(now);
            user.setUpdatedAt(now);

            assertEquals(1L, user.getId());
            assertEquals("John", user.getFirstName());
            assertEquals("Doe", user.getLastName());
            assertEquals("john.doe@bgu.ac.il", user.getEmail());
            assertEquals("johndoe", user.getBguUsername());
            assertEquals(User.Degree.PhD, user.getDegree());
            assertTrue(user.getIsPresenter());
            assertFalse(user.getIsParticipant());
            assertEquals("123456789", user.getNationalIdNumber());
            assertEquals("Test abstract", user.getSeminarAbstract());
            assertEquals(now, user.getCreatedAt());
            assertEquals(now, user.getUpdatedAt());
        }

        @Test
        void testUserDeprecatedMethods() {
            User user = new User();
            user.setUserId(5L);
            assertEquals(5L, user.getUserId());
            assertEquals(5L, user.getId());
        }

        @Test
        void testUserSupervisor() {
            User user = new User();
            Supervisor supervisor = new Supervisor();
            supervisor.setId(1L);
            supervisor.setName("Prof. Smith");

            user.setSupervisor(supervisor);

            assertNotNull(user.getSupervisor());
            assertEquals("Prof. Smith", user.getSupervisor().getName());
        }

        @Test
        void testUserEqualsAndHashCode() {
            User user1 = new User();
            user1.setId(1L);

            User user2 = new User();
            user2.setId(1L);

            User user3 = new User();
            user3.setId(2L);

            User user4 = new User();
            user4.setId(null);

            assertEquals(user1, user1);
            assertEquals(user1, user2);
            assertNotEquals(user1, user3);
            assertNotEquals(user1, null);
            assertNotEquals(user1, "not a user");

            assertEquals(user1.hashCode(), user2.hashCode());
            assertNotEquals(user1.hashCode(), user3.hashCode());
        }

        @Test
        void testUserToString() {
            User user = new User();
            user.setId(1L);
            user.setFirstName("John");
            user.setLastName("Doe");
            user.setEmail("john@bgu.ac.il");
            user.setBguUsername("johndoe");
            user.setDegree(User.Degree.MSc);
            user.setIsPresenter(true);
            user.setIsParticipant(false);

            String result = user.toString();

            assertNotNull(result);
            assertTrue(result.contains("John"));
            assertTrue(result.contains("Doe"));
            assertTrue(result.contains("john@bgu.ac.il"));
            assertTrue(result.contains("johndoe"));
            assertTrue(result.contains("MSc"));
        }
    }

    // ==================== User.Degree Enum Tests ====================
    @Nested
    class DegreeEnumTest {

        @Test
        void testDegreeEnumValues() {
            assertEquals(2, User.Degree.values().length);
            assertNotNull(User.Degree.MSc);
            assertNotNull(User.Degree.PhD);
        }

        @Test
        void testDegreeValueOf() {
            assertEquals(User.Degree.MSc, User.Degree.valueOf("MSc"));
            assertEquals(User.Degree.PhD, User.Degree.valueOf("PhD"));
        }

        @Test
        void testDegreeToString() {
            assertEquals("MSc", User.Degree.MSc.toString());
            assertEquals("PhD", User.Degree.PhD.toString());
        }
    }

    // ==================== SeminarSlot Entity Tests ====================
    @Nested
    class SeminarSlotEntityTest {

        @Test
        void testSeminarSlotGettersAndSetters() {
            SeminarSlot slot = new SeminarSlot();
            LocalDate date = LocalDate.of(2026, 1, 15);
            LocalTime startTime = LocalTime.of(10, 0);
            LocalTime endTime = LocalTime.of(11, 0);
            LocalDateTime now = LocalDateTime.now();

            slot.setSlotId(1L);
            slot.setSemesterLabel("Spring 2026");
            slot.setSlotDate(date);
            slot.setStartTime(startTime);
            slot.setEndTime(endTime);
            slot.setBuilding("Building 34");
            slot.setRoom("Room 101");
            slot.setCapacity(3);
            slot.setStatus(SeminarSlot.SlotStatus.FREE);
            slot.setAttendanceOpenedAt(now);
            slot.setAttendanceClosesAt(now.plusMinutes(15));
            slot.setAttendanceOpenedBy("johndoe");
            slot.setLegacySeminarId(100L);
            slot.setLegacySessionId(200L);
            slot.setCreatedAt(now);
            slot.setUpdatedAt(now);

            assertEquals(1L, slot.getSlotId());
            assertEquals("Spring 2026", slot.getSemesterLabel());
            assertEquals(date, slot.getSlotDate());
            assertEquals(startTime, slot.getStartTime());
            assertEquals(endTime, slot.getEndTime());
            assertEquals("Building 34", slot.getBuilding());
            assertEquals("Room 101", slot.getRoom());
            assertEquals(3, slot.getCapacity());
            assertEquals(SeminarSlot.SlotStatus.FREE, slot.getStatus());
            assertEquals(now, slot.getAttendanceOpenedAt());
            assertEquals(now.plusMinutes(15), slot.getAttendanceClosesAt());
            assertEquals("johndoe", slot.getAttendanceOpenedBy());
            assertEquals(100L, slot.getLegacySeminarId());
            assertEquals(200L, slot.getLegacySessionId());
            assertEquals(now, slot.getCreatedAt());
            assertEquals(now, slot.getUpdatedAt());
        }

        @Test
        void testSeminarSlotNullValues() {
            SeminarSlot slot = new SeminarSlot();

            assertNull(slot.getSlotId());
            assertNull(slot.getSemesterLabel());
            assertNull(slot.getBuilding());
            assertNull(slot.getRoom());
            assertNull(slot.getAttendanceOpenedAt());
            assertNull(slot.getAttendanceOpenedBy());
        }
    }

    // ==================== SeminarSlot.SlotStatus Enum Tests ====================
    @Nested
    class SlotStatusEnumTest {

        @Test
        void testSlotStatusEnumValues() {
            assertEquals(3, SeminarSlot.SlotStatus.values().length);
            assertNotNull(SeminarSlot.SlotStatus.FREE);
            assertNotNull(SeminarSlot.SlotStatus.SEMI);
            assertNotNull(SeminarSlot.SlotStatus.FULL);
        }

        @Test
        void testSlotStatusValueOf() {
            assertEquals(SeminarSlot.SlotStatus.FREE, SeminarSlot.SlotStatus.valueOf("FREE"));
            assertEquals(SeminarSlot.SlotStatus.SEMI, SeminarSlot.SlotStatus.valueOf("SEMI"));
            assertEquals(SeminarSlot.SlotStatus.FULL, SeminarSlot.SlotStatus.valueOf("FULL"));
        }

        @Test
        void testSlotStatusToString() {
            assertEquals("FREE", SeminarSlot.SlotStatus.FREE.toString());
            assertEquals("SEMI", SeminarSlot.SlotStatus.SEMI.toString());
            assertEquals("FULL", SeminarSlot.SlotStatus.FULL.toString());
        }
    }

    // ==================== SeminarSlotRegistrationId Tests ====================
    @Nested
    class SeminarSlotRegistrationIdTest {

        @Test
        void testDefaultConstructor() {
            SeminarSlotRegistrationId id = new SeminarSlotRegistrationId();
            assertNull(id.getSlotId());
            assertNull(id.getPresenterUsername());
        }

        @Test
        void testParameterizedConstructor() {
            SeminarSlotRegistrationId id = new SeminarSlotRegistrationId(1L, "johndoe");

            assertEquals(1L, id.getSlotId());
            assertEquals("johndoe", id.getPresenterUsername());
        }

        @Test
        void testGettersAndSetters() {
            SeminarSlotRegistrationId id = new SeminarSlotRegistrationId();

            id.setSlotId(5L);
            id.setPresenterUsername("testuser");

            assertEquals(5L, id.getSlotId());
            assertEquals("testuser", id.getPresenterUsername());
        }

        @Test
        void testEqualsAndHashCode() {
            SeminarSlotRegistrationId id1 = new SeminarSlotRegistrationId(1L, "user1");
            SeminarSlotRegistrationId id2 = new SeminarSlotRegistrationId(1L, "user1");
            SeminarSlotRegistrationId id3 = new SeminarSlotRegistrationId(1L, "user2");
            SeminarSlotRegistrationId id4 = new SeminarSlotRegistrationId(2L, "user1");
            SeminarSlotRegistrationId id5 = new SeminarSlotRegistrationId(null, null);

            assertEquals(id1, id1);
            assertEquals(id1, id2);
            assertNotEquals(id1, id3);
            assertNotEquals(id1, id4);
            assertNotEquals(id1, null);
            assertNotEquals(id1, "not an id");

            assertEquals(id1.hashCode(), id2.hashCode());
            assertNotEquals(id1.hashCode(), id3.hashCode());
            assertNotEquals(id1.hashCode(), id4.hashCode());
        }

        @Test
        void testEqualsWithNullFields() {
            SeminarSlotRegistrationId id1 = new SeminarSlotRegistrationId(null, "user1");
            SeminarSlotRegistrationId id2 = new SeminarSlotRegistrationId(null, "user1");
            SeminarSlotRegistrationId id3 = new SeminarSlotRegistrationId(1L, null);
            SeminarSlotRegistrationId id4 = new SeminarSlotRegistrationId(1L, null);

            assertEquals(id1, id2);
            assertEquals(id3, id4);
            assertNotEquals(id1, id3);
        }
    }

    // ==================== SeminarSlotRegistration Tests ====================
    @Nested
    class SeminarSlotRegistrationTest {

        @Test
        void testGettersAndSetters() {
            SeminarSlotRegistration reg = new SeminarSlotRegistration();
            SeminarSlotRegistrationId id = new SeminarSlotRegistrationId(1L, "johndoe");
            LocalDateTime now = LocalDateTime.now();

            reg.setId(id);
            reg.setDegree(User.Degree.MSc);
            reg.setTopic("Test Topic");
            reg.setSeminarAbstract("Test Abstract");
            reg.setSupervisorName("Prof. Smith");
            reg.setSupervisorEmail("smith@bgu.ac.il");
            reg.setRegisteredAt(now);
            reg.setApprovalStatus(ApprovalStatus.PENDING);
            reg.setApprovalToken("token123");
            reg.setApprovalTokenExpiresAt(now.plusDays(14));
            reg.setSupervisorApprovedAt(now.plusDays(1));
            reg.setSupervisorDeclinedAt(now.plusDays(2));
            reg.setSupervisorDeclinedReason("Reason");
            reg.setLastReminderSentAt(now.plusHours(48));

            assertEquals(id, reg.getId());
            assertEquals(User.Degree.MSc, reg.getDegree());
            assertEquals("Test Topic", reg.getTopic());
            assertEquals("Test Abstract", reg.getSeminarAbstract());
            assertEquals("Prof. Smith", reg.getSupervisorName());
            assertEquals("smith@bgu.ac.il", reg.getSupervisorEmail());
            assertEquals(now, reg.getRegisteredAt());
            assertEquals(ApprovalStatus.PENDING, reg.getApprovalStatus());
            assertEquals("token123", reg.getApprovalToken());
            assertEquals(now.plusDays(14), reg.getApprovalTokenExpiresAt());
            assertEquals(now.plusDays(1), reg.getSupervisorApprovedAt());
            assertEquals(now.plusDays(2), reg.getSupervisorDeclinedAt());
            assertEquals("Reason", reg.getSupervisorDeclinedReason());
            assertEquals(now.plusHours(48), reg.getLastReminderSentAt());
        }

        @Test
        void testGetSlotIdAndPresenterUsername() {
            SeminarSlotRegistration reg = new SeminarSlotRegistration();

            assertNull(reg.getSlotId());
            assertNull(reg.getPresenterUsername());

            SeminarSlotRegistrationId id = new SeminarSlotRegistrationId(5L, "testuser");
            reg.setId(id);

            assertEquals(5L, reg.getSlotId());
            assertEquals("testuser", reg.getPresenterUsername());
        }

        @Test
        void testDefaultApprovalStatus() {
            SeminarSlotRegistration reg = new SeminarSlotRegistration();
            reg.onCreate();

            assertEquals(ApprovalStatus.PENDING, reg.getApprovalStatus());
            assertNotNull(reg.getRegisteredAt());
        }

        @Test
        void testOnCreateDoesNotOverrideExistingValues() {
            SeminarSlotRegistration reg = new SeminarSlotRegistration();
            LocalDateTime customTime = LocalDateTime.of(2025, 1, 1, 10, 0);
            reg.setRegisteredAt(customTime);
            reg.setApprovalStatus(ApprovalStatus.APPROVED);

            reg.onCreate();

            assertEquals(customTime, reg.getRegisteredAt());
            assertEquals(ApprovalStatus.APPROVED, reg.getApprovalStatus());
        }
    }

    // ==================== WaitingListEntry Tests ====================
    @Nested
    class WaitingListEntryTest {

        @Test
        void testGettersAndSetters() {
            WaitingListEntry entry = new WaitingListEntry();
            LocalDateTime now = LocalDateTime.now();

            entry.setWaitingListId(1L);
            entry.setSlotId(5L);
            entry.setPresenterUsername("johndoe");
            entry.setDegree(User.Degree.PhD);
            entry.setTopic("Test Topic");
            entry.setSupervisorName("Prof. Smith");
            entry.setSupervisorEmail("smith@bgu.ac.il");
            entry.setPosition(1);
            entry.setAddedAt(now);
            entry.setPromotionToken("promo123");
            entry.setPromotionTokenExpiresAt(now.plusHours(48));
            entry.setPromotionOfferedAt(now);

            assertEquals(1L, entry.getWaitingListId());
            assertEquals(5L, entry.getSlotId());
            assertEquals("johndoe", entry.getPresenterUsername());
            assertEquals(User.Degree.PhD, entry.getDegree());
            assertEquals("Test Topic", entry.getTopic());
            assertEquals("Prof. Smith", entry.getSupervisorName());
            assertEquals("smith@bgu.ac.il", entry.getSupervisorEmail());
            assertEquals(1, entry.getPosition());
            assertEquals(now, entry.getAddedAt());
            assertEquals("promo123", entry.getPromotionToken());
            assertEquals(now.plusHours(48), entry.getPromotionTokenExpiresAt());
            assertEquals(now, entry.getPromotionOfferedAt());
        }

        @Test
        void testHasPromotionPending() {
            WaitingListEntry entry = new WaitingListEntry();

            // No promotion token
            assertFalse(entry.hasPromotionPending());

            // Has token but no expiry
            entry.setPromotionToken("token");
            assertFalse(entry.hasPromotionPending());

            // Has token and future expiry
            entry.setPromotionTokenExpiresAt(LocalDateTime.now().plusHours(1));
            assertTrue(entry.hasPromotionPending());

            // Has token but past expiry
            entry.setPromotionTokenExpiresAt(LocalDateTime.now().minusHours(1));
            assertFalse(entry.hasPromotionPending());
        }

        @Test
        void testOnCreate() {
            WaitingListEntry entry = new WaitingListEntry();
            assertNull(entry.getAddedAt());

            entry.onCreate();
            assertNotNull(entry.getAddedAt());
        }

        @Test
        void testOnCreateDoesNotOverrideExistingValue() {
            WaitingListEntry entry = new WaitingListEntry();
            LocalDateTime customTime = LocalDateTime.of(2025, 1, 1, 10, 0);
            entry.setAddedAt(customTime);

            entry.onCreate();

            assertEquals(customTime, entry.getAddedAt());
        }
    }

    // ==================== Session Entity Tests ====================
    @Nested
    class SessionEntityTest {

        @Test
        void testGettersAndSetters() {
            Session session = new Session();
            LocalDateTime now = LocalDateTime.now();

            session.setSessionId(1L);
            session.setSeminarId(5L);
            session.setStartTime(now);
            session.setEndTime(now.plusHours(1));
            session.setStatus(Session.SessionStatus.OPEN);
            session.setLocation("Room 101");
            session.setCreatedAt(now);
            session.setUpdatedAt(now);

            assertEquals(1L, session.getSessionId());
            assertEquals(5L, session.getSeminarId());
            assertEquals(now, session.getStartTime());
            assertEquals(now.plusHours(1), session.getEndTime());
            assertEquals(Session.SessionStatus.OPEN, session.getStatus());
            assertEquals("Room 101", session.getLocation());
            assertEquals(now, session.getCreatedAt());
            assertEquals(now, session.getUpdatedAt());
        }

        @Test
        void testEqualsAndHashCode() {
            Session session1 = new Session();
            session1.setSessionId(1L);

            Session session2 = new Session();
            session2.setSessionId(1L);

            Session session3 = new Session();
            session3.setSessionId(2L);

            assertEquals(session1, session1);
            assertEquals(session1, session2);
            assertNotEquals(session1, session3);
            assertNotEquals(session1, null);
            assertNotEquals(session1, "not a session");

            assertEquals(session1.hashCode(), session2.hashCode());
            assertNotEquals(session1.hashCode(), session3.hashCode());
        }

        @Test
        void testToString() {
            Session session = new Session();
            session.setSessionId(1L);
            session.setSeminarId(5L);
            session.setStartTime(LocalDateTime.of(2026, 1, 15, 10, 0));
            session.setEndTime(LocalDateTime.of(2026, 1, 15, 11, 0));
            session.setStatus(Session.SessionStatus.OPEN);

            String result = session.toString();

            assertNotNull(result);
            assertTrue(result.contains("1"));
            assertTrue(result.contains("5"));
            assertTrue(result.contains("OPEN"));
        }
    }

    // ==================== Session.SessionStatus Enum Tests ====================
    @Nested
    class SessionStatusEnumTest {

        @Test
        void testSessionStatusEnumValues() {
            assertEquals(2, Session.SessionStatus.values().length);
            assertNotNull(Session.SessionStatus.OPEN);
            assertNotNull(Session.SessionStatus.CLOSED);
        }

        @Test
        void testSessionStatusValueOf() {
            assertEquals(Session.SessionStatus.OPEN, Session.SessionStatus.valueOf("OPEN"));
            assertEquals(Session.SessionStatus.CLOSED, Session.SessionStatus.valueOf("CLOSED"));
        }

        @Test
        void testSessionStatusToString() {
            assertEquals("OPEN", Session.SessionStatus.OPEN.toString());
            assertEquals("CLOSED", Session.SessionStatus.CLOSED.toString());
        }
    }

    // ==================== Attendance Entity Tests ====================
    @Nested
    class AttendanceEntityTest {

        @Test
        void testGettersAndSetters() {
            Attendance attendance = new Attendance();
            LocalDateTime now = LocalDateTime.now();

            attendance.setAttendanceId(1L);
            attendance.setSessionId(5L);
            attendance.setAttendanceTime(now);
            attendance.setMethod(Attendance.AttendanceMethod.QR_SCAN);
            attendance.setRequestStatus(Attendance.RequestStatus.CONFIRMED);
            attendance.setManualReason("Phone battery died");
            attendance.setRequestedAt(now);
            attendance.setApprovedAt(now.plusMinutes(5));
            attendance.setDeviceId("device123");
            attendance.setAutoFlags("flag1,flag2");
            attendance.setNotes("Some notes");
            attendance.setCreatedAt(now);
            attendance.setUpdatedAt(now);
            attendance.setStudentUsername("johndoe");
            attendance.setApprovedByUsername("admin");

            assertEquals(1L, attendance.getAttendanceId());
            assertEquals(5L, attendance.getSessionId());
            assertEquals(now, attendance.getAttendanceTime());
            assertEquals(Attendance.AttendanceMethod.QR_SCAN, attendance.getMethod());
            assertEquals(Attendance.RequestStatus.CONFIRMED, attendance.getRequestStatus());
            assertEquals("Phone battery died", attendance.getManualReason());
            assertEquals(now, attendance.getRequestedAt());
            assertEquals(now.plusMinutes(5), attendance.getApprovedAt());
            assertEquals("device123", attendance.getDeviceId());
            assertEquals("flag1,flag2", attendance.getAutoFlags());
            assertEquals("Some notes", attendance.getNotes());
            assertEquals(now, attendance.getCreatedAt());
            assertEquals(now, attendance.getUpdatedAt());
            assertEquals("johndoe", attendance.getStudentUsername());
            assertEquals("admin", attendance.getApprovedByUsername());
        }

        @Test
        void testEqualsAndHashCode() {
            Attendance att1 = new Attendance();
            att1.setAttendanceId(1L);

            Attendance att2 = new Attendance();
            att2.setAttendanceId(1L);

            Attendance att3 = new Attendance();
            att3.setAttendanceId(2L);

            assertEquals(att1, att1);
            assertEquals(att1, att2);
            assertNotEquals(att1, att3);
            assertNotEquals(att1, null);
            assertNotEquals(att1, "not attendance");

            assertEquals(att1.hashCode(), att2.hashCode());
            assertNotEquals(att1.hashCode(), att3.hashCode());
        }

        @Test
        void testToString() {
            Attendance attendance = new Attendance();
            attendance.setAttendanceId(1L);
            attendance.setSessionId(5L);
            attendance.setAttendanceTime(LocalDateTime.of(2026, 1, 15, 10, 30));
            attendance.setMethod(Attendance.AttendanceMethod.MANUAL_REQUEST);
            attendance.setRequestStatus(Attendance.RequestStatus.PENDING_APPROVAL);
            attendance.setManualReason("Late arrival");
            attendance.setStudentUsername("johndoe");

            String result = attendance.toString();

            assertNotNull(result);
            assertTrue(result.contains("1"));
            assertTrue(result.contains("5"));
            assertTrue(result.contains("MANUAL_REQUEST"));
            assertTrue(result.contains("PENDING_APPROVAL"));
            assertTrue(result.contains("Late arrival"));
            assertTrue(result.contains("johndoe"));
        }
    }

    // ==================== Attendance.AttendanceMethod Enum Tests ====================
    @Nested
    class AttendanceMethodEnumTest {

        @Test
        void testAttendanceMethodEnumValues() {
            assertEquals(4, Attendance.AttendanceMethod.values().length);
            assertNotNull(Attendance.AttendanceMethod.QR_SCAN);
            assertNotNull(Attendance.AttendanceMethod.MANUAL);
            assertNotNull(Attendance.AttendanceMethod.MANUAL_REQUEST);
            assertNotNull(Attendance.AttendanceMethod.PROXY);
        }

        @Test
        void testAttendanceMethodValueOf() {
            assertEquals(Attendance.AttendanceMethod.QR_SCAN, Attendance.AttendanceMethod.valueOf("QR_SCAN"));
            assertEquals(Attendance.AttendanceMethod.MANUAL, Attendance.AttendanceMethod.valueOf("MANUAL"));
            assertEquals(Attendance.AttendanceMethod.MANUAL_REQUEST, Attendance.AttendanceMethod.valueOf("MANUAL_REQUEST"));
            assertEquals(Attendance.AttendanceMethod.PROXY, Attendance.AttendanceMethod.valueOf("PROXY"));
        }

        @Test
        void testAttendanceMethodToString() {
            assertEquals("QR_SCAN", Attendance.AttendanceMethod.QR_SCAN.toString());
            assertEquals("MANUAL", Attendance.AttendanceMethod.MANUAL.toString());
            assertEquals("MANUAL_REQUEST", Attendance.AttendanceMethod.MANUAL_REQUEST.toString());
            assertEquals("PROXY", Attendance.AttendanceMethod.PROXY.toString());
        }
    }

    // ==================== Attendance.RequestStatus Enum Tests ====================
    @Nested
    class RequestStatusEnumTest {

        @Test
        void testRequestStatusEnumValues() {
            assertEquals(3, Attendance.RequestStatus.values().length);
            assertNotNull(Attendance.RequestStatus.CONFIRMED);
            assertNotNull(Attendance.RequestStatus.PENDING_APPROVAL);
            assertNotNull(Attendance.RequestStatus.REJECTED);
        }

        @Test
        void testRequestStatusValueOf() {
            assertEquals(Attendance.RequestStatus.CONFIRMED, Attendance.RequestStatus.valueOf("CONFIRMED"));
            assertEquals(Attendance.RequestStatus.PENDING_APPROVAL, Attendance.RequestStatus.valueOf("PENDING_APPROVAL"));
            assertEquals(Attendance.RequestStatus.REJECTED, Attendance.RequestStatus.valueOf("REJECTED"));
        }

        @Test
        void testRequestStatusToString() {
            assertEquals("CONFIRMED", Attendance.RequestStatus.CONFIRMED.toString());
            assertEquals("PENDING_APPROVAL", Attendance.RequestStatus.PENDING_APPROVAL.toString());
            assertEquals("REJECTED", Attendance.RequestStatus.REJECTED.toString());
        }
    }

    // ==================== ApprovalStatus Enum Tests ====================
    @Nested
    class ApprovalStatusEnumTest {

        @Test
        void testApprovalStatusEnumValues() {
            assertEquals(4, ApprovalStatus.values().length);
            assertNotNull(ApprovalStatus.PENDING);
            assertNotNull(ApprovalStatus.APPROVED);
            assertNotNull(ApprovalStatus.DECLINED);
            assertNotNull(ApprovalStatus.EXPIRED);
        }

        @Test
        void testApprovalStatusValueOf() {
            assertEquals(ApprovalStatus.PENDING, ApprovalStatus.valueOf("PENDING"));
            assertEquals(ApprovalStatus.APPROVED, ApprovalStatus.valueOf("APPROVED"));
            assertEquals(ApprovalStatus.DECLINED, ApprovalStatus.valueOf("DECLINED"));
            assertEquals(ApprovalStatus.EXPIRED, ApprovalStatus.valueOf("EXPIRED"));
        }

        @Test
        void testApprovalStatusToString() {
            assertEquals("PENDING", ApprovalStatus.PENDING.toString());
            assertEquals("APPROVED", ApprovalStatus.APPROVED.toString());
            assertEquals("DECLINED", ApprovalStatus.DECLINED.toString());
            assertEquals("EXPIRED", ApprovalStatus.EXPIRED.toString());
        }

        @Test
        void testApprovalStatusOrdinal() {
            assertEquals(0, ApprovalStatus.PENDING.ordinal());
            assertEquals(1, ApprovalStatus.APPROVED.ordinal());
            assertEquals(2, ApprovalStatus.DECLINED.ordinal());
            assertEquals(3, ApprovalStatus.EXPIRED.ordinal());
        }
    }

    // ==================== Edge Case and Null Handling Tests ====================
    @Nested
    class EdgeCaseTests {

        @Test
        void testUserWithNullId() {
            User user1 = new User();
            User user2 = new User();

            assertEquals(user1, user2);
            assertEquals(user1.hashCode(), user2.hashCode());
        }

        @Test
        void testSeminarSlotRegistrationIdWithNullValues() {
            SeminarSlotRegistrationId id1 = new SeminarSlotRegistrationId(null, null);
            SeminarSlotRegistrationId id2 = new SeminarSlotRegistrationId(null, null);

            assertEquals(id1, id2);
            assertEquals(id1.hashCode(), id2.hashCode());
        }

        @Test
        void testSessionWithNullId() {
            Session session1 = new Session();
            Session session2 = new Session();

            assertEquals(session1, session2);
            assertEquals(session1.hashCode(), session2.hashCode());
        }

        @Test
        void testAttendanceWithNullId() {
            Attendance att1 = new Attendance();
            Attendance att2 = new Attendance();

            assertEquals(att1, att2);
            assertEquals(att1.hashCode(), att2.hashCode());
        }

        @Test
        void testEnumValuesNotNull() {
            for (User.Degree degree : User.Degree.values()) {
                assertNotNull(degree);
                assertNotNull(degree.name());
            }

            for (SeminarSlot.SlotStatus status : SeminarSlot.SlotStatus.values()) {
                assertNotNull(status);
                assertNotNull(status.name());
            }

            for (Session.SessionStatus status : Session.SessionStatus.values()) {
                assertNotNull(status);
                assertNotNull(status.name());
            }

            for (Attendance.AttendanceMethod method : Attendance.AttendanceMethod.values()) {
                assertNotNull(method);
                assertNotNull(method.name());
            }

            for (Attendance.RequestStatus status : Attendance.RequestStatus.values()) {
                assertNotNull(status);
                assertNotNull(status.name());
            }

            for (ApprovalStatus status : ApprovalStatus.values()) {
                assertNotNull(status);
                assertNotNull(status.name());
            }
        }
    }
}
