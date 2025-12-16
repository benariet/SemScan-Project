package edu.bgu.semscanapi.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO returned by the presenter home API. Models the three primary sections of the
 * presenter experience: registration catalog, current registration, and attendance controls.
 */
public class PresenterHomeResponse {

    private PresenterSummary presenter;
    private MySlotSummary mySlot;
    private List<SlotCard> slotCatalog = new ArrayList<>();
    private AttendancePanel attendance;

    public PresenterSummary getPresenter() {
        return presenter;
    }

    public void setPresenter(PresenterSummary presenter) {
        this.presenter = presenter;
    }

    public MySlotSummary getMySlot() {
        return mySlot;
    }

    public void setMySlot(MySlotSummary mySlot) {
        this.mySlot = mySlot;
    }

    public List<SlotCard> getSlotCatalog() {
        return slotCatalog;
    }

    public void setSlotCatalog(List<SlotCard> slotCatalog) {
        this.slotCatalog = slotCatalog;
    }

    public AttendancePanel getAttendance() {
        return attendance;
    }

    public void setAttendance(AttendancePanel attendance) {
        this.attendance = attendance;
    }

    // -------------------------------------------------------------------------
    // Nested DTOs
    // -------------------------------------------------------------------------

    public static class PresenterSummary {
        private Long id;
        private String name;
        private String degree;
        private boolean alreadyRegistered;
        private String currentCycleId;
        private String bguUsername;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDegree() {
            return degree;
        }

        public void setDegree(String degree) {
            this.degree = degree;
        }

        public boolean isAlreadyRegistered() {
            return alreadyRegistered;
        }

        public void setAlreadyRegistered(boolean alreadyRegistered) {
            this.alreadyRegistered = alreadyRegistered;
        }

        public String getCurrentCycleId() {
            return currentCycleId;
        }

        public void setCurrentCycleId(String currentCycleId) {
            this.currentCycleId = currentCycleId;
        }

        public String getBguUsername() {
            return bguUsername;
        }

        public void setBguUsername(String bguUsername) {
            this.bguUsername = bguUsername;
        }
    }

    public static class MySlotSummary {
        private Long slotId;
        private String date;
        private String timeRange;
        private String room;
        private String building;
        private String dayOfWeek;
        private String semesterLabel;
        private List<RegisteredPresenter> coPresenters = new ArrayList<>();

        public Long getSlotId() {
            return slotId;
        }

        public void setSlotId(Long slotId) {
            this.slotId = slotId;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public String getTimeRange() {
            return timeRange;
        }

        public void setTimeRange(String timeRange) {
            this.timeRange = timeRange;
        }

        public String getRoom() {
            return room;
        }

        public void setRoom(String room) {
            this.room = room;
        }

        public String getBuilding() {
            return building;
        }

        public void setBuilding(String building) {
            this.building = building;
        }

        public String getDayOfWeek() {
            return dayOfWeek;
        }

        public void setDayOfWeek(String dayOfWeek) {
            this.dayOfWeek = dayOfWeek;
        }

        public String getSemesterLabel() {
            return semesterLabel;
        }

        public void setSemesterLabel(String semesterLabel) {
            this.semesterLabel = semesterLabel;
        }

        public List<RegisteredPresenter> getCoPresenters() {
            return coPresenters;
        }

        public void setCoPresenters(List<RegisteredPresenter> coPresenters) {
            this.coPresenters = coPresenters;
        }
    }

    public static class RegisteredPresenter {
        private String name;
        private String degree;
        private String topic;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDegree() {
            return degree;
        }

        public void setDegree(String degree) {
            this.degree = degree;
        }

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }
    }

    public static class SlotCard {
        private Long slotId;
        private String semesterLabel;
        private String date;
        private String dayOfWeek;
        private String timeRange;
        private String room;
        private String building;
        private SlotState state;
        private int capacity;
        private int enrolledCount;
        private int availableCount;
        private boolean canRegister;
        private String disableReason;
        private List<RegisteredPresenter> registered = new ArrayList<>();
        // Session status fields for client-side filtering
        private String attendanceOpenedAt;
        private String attendanceClosesAt;
        private Boolean hasClosedSession;
        // New fields for mobile compatibility
        private int approvedCount; // Number of approved registrations
        private int pendingCount; // Number of pending approvals
        private String approvalStatus; // Current user's approval status: "PENDING_APPROVAL", "APPROVED", "DECLINED", "EXPIRED", or null
        private boolean onWaitingList; // Is current user on waiting list
        private int waitingListCount; // Total number of people on waiting list for this slot (MUST be included for all slots)
        private String waitingListUserName; // Username of first person on waiting list (or null)

        public Long getSlotId() {
            return slotId;
        }

        public void setSlotId(Long slotId) {
            this.slotId = slotId;
        }

        public String getSemesterLabel() {
            return semesterLabel;
        }

        public void setSemesterLabel(String semesterLabel) {
            this.semesterLabel = semesterLabel;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public String getDayOfWeek() {
            return dayOfWeek;
        }

        public void setDayOfWeek(String dayOfWeek) {
            this.dayOfWeek = dayOfWeek;
        }

        public String getTimeRange() {
            return timeRange;
        }

        public void setTimeRange(String timeRange) {
            this.timeRange = timeRange;
        }

        public String getRoom() {
            return room;
        }

        public void setRoom(String room) {
            this.room = room;
        }

        public String getBuilding() {
            return building;
        }

        public void setBuilding(String building) {
            this.building = building;
        }

        public SlotState getState() {
            return state;
        }

        public void setState(SlotState state) {
            this.state = state;
        }

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public int getEnrolledCount() {
            return enrolledCount;
        }

        public void setEnrolledCount(int enrolledCount) {
            this.enrolledCount = enrolledCount;
        }

        public int getAvailableCount() {
            return availableCount;
        }

        public void setAvailableCount(int availableCount) {
            this.availableCount = availableCount;
        }

        public boolean isCanRegister() {
            return canRegister;
        }

        public void setCanRegister(boolean canRegister) {
            this.canRegister = canRegister;
        }

        public String getDisableReason() {
            return disableReason;
        }

        public void setDisableReason(String disableReason) {
            this.disableReason = disableReason;
        }

        public List<RegisteredPresenter> getRegistered() {
            return registered;
        }

        public void setRegistered(List<RegisteredPresenter> registered) {
            this.registered = registered;
        }

        public String getAttendanceOpenedAt() {
            return attendanceOpenedAt;
        }

        public void setAttendanceOpenedAt(String attendanceOpenedAt) {
            this.attendanceOpenedAt = attendanceOpenedAt;
        }

        public String getAttendanceClosesAt() {
            return attendanceClosesAt;
        }

        public void setAttendanceClosesAt(String attendanceClosesAt) {
            this.attendanceClosesAt = attendanceClosesAt;
        }

        public Boolean getHasClosedSession() {
            return hasClosedSession;
        }

        public void setHasClosedSession(Boolean hasClosedSession) {
            this.hasClosedSession = hasClosedSession;
        }

        public int getApprovedCount() {
            return approvedCount;
        }

        public void setApprovedCount(int approvedCount) {
            this.approvedCount = approvedCount;
        }

        public int getPendingCount() {
            return pendingCount;
        }

        public void setPendingCount(int pendingCount) {
            this.pendingCount = pendingCount;
        }

        public String getApprovalStatus() {
            return approvalStatus;
        }

        public void setApprovalStatus(String approvalStatus) {
            this.approvalStatus = approvalStatus;
        }

        public boolean isOnWaitingList() {
            return onWaitingList;
        }

        public void setOnWaitingList(boolean onWaitingList) {
            this.onWaitingList = onWaitingList;
        }

        public int getWaitingListCount() {
            return waitingListCount;
        }

        public void setWaitingListCount(int waitingListCount) {
            this.waitingListCount = waitingListCount;
        }

        public String getWaitingListUserName() {
            return waitingListUserName;
        }

        public void setWaitingListUserName(String waitingListUserName) {
            this.waitingListUserName = waitingListUserName;
        }
    }

    public static class AttendancePanel {
        private boolean canOpen;
        private String openQrUrl;
        private String status;
        private String warning;
        private String openedAt;
        private String closesAt;
        private boolean alreadyOpen;
        private Long sessionId;
        private String qrPayload;

        public boolean isCanOpen() {
            return canOpen;
        }

        public void setCanOpen(boolean canOpen) {
            this.canOpen = canOpen;
        }

        public String getOpenQrUrl() {
            return openQrUrl;
        }

        public void setOpenQrUrl(String openQrUrl) {
            this.openQrUrl = openQrUrl;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getWarning() {
            return warning;
        }

        public void setWarning(String warning) {
            this.warning = warning;
        }

        public String getOpenedAt() {
            return openedAt;
        }

        public void setOpenedAt(String openedAt) {
            this.openedAt = openedAt;
        }

        public String getClosesAt() {
            return closesAt;
        }

        public void setClosesAt(String closesAt) {
            this.closesAt = closesAt;
        }

        public boolean isAlreadyOpen() {
            return alreadyOpen;
        }

        public void setAlreadyOpen(boolean alreadyOpen) {
            this.alreadyOpen = alreadyOpen;
        }

        public Long getSessionId() {
            return sessionId;
        }

        public void setSessionId(Long sessionId) {
            this.sessionId = sessionId;
        }

        public String getQrPayload() {
            return qrPayload;
        }

        public void setQrPayload(String qrPayload) {
            this.qrPayload = qrPayload;
        }
    }

    public enum SlotState {
        FREE,
        SEMI,
        FULL
    }
}


