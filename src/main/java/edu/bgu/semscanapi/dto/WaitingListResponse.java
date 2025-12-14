package edu.bgu.semscanapi.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for waiting list response
 */
public class WaitingListResponse {

    private boolean ok; // Mobile compatibility
    private String message; // Mobile compatibility
    private String code; // Mobile compatibility
    private Integer position; // Mobile compatibility - user's position in waiting list
    private Long slotId;
    private List<WaitingListEntryDto> entries = new ArrayList<>();

    public Long getSlotId() {
        return slotId;
    }

    public void setSlotId(Long slotId) {
        this.slotId = slotId;
    }

    public List<WaitingListEntryDto> getEntries() {
        return entries;
    }

    public void setEntries(List<WaitingListEntryDto> entries) {
        this.entries = entries;
    }

    public static class WaitingListEntryDto {
        private String presenterUsername;
        private String degree;
        private String topic;
        private Integer position;
        private String addedAt;

        public String getPresenterUsername() {
            return presenterUsername;
        }

        public void setPresenterUsername(String presenterUsername) {
            this.presenterUsername = presenterUsername;
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

        public Integer getPosition() {
            return position;
        }

        public void setPosition(Integer position) {
            this.position = position;
        }

        public String getAddedAt() {
            return addedAt;
        }

    public void setAddedAt(String addedAt) {
        this.addedAt = addedAt;
    }
    }

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }
}

