package edu.bgu.semscanapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class SeminarSlotRegistrationId implements Serializable {

    @Column(name = "slot_id")
    private Long slotId;

    @Column(name = "presenter_username")
    private String presenterUsername;

    public SeminarSlotRegistrationId() {
    }

    public SeminarSlotRegistrationId(Long slotId, String presenterUsername) {
        this.slotId = slotId;
        this.presenterUsername = presenterUsername;
    }

    public Long getSlotId() {
        return slotId;
    }

    public void setSlotId(Long slotId) {
        this.slotId = slotId;
    }

    public String getPresenterUsername() {
        return presenterUsername;
    }

    public void setPresenterUsername(String presenterUsername) {
        this.presenterUsername = presenterUsername;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SeminarSlotRegistrationId that = (SeminarSlotRegistrationId) o;
        return Objects.equals(slotId, that.slotId) && Objects.equals(presenterUsername, that.presenterUsername);
    }

    @Override
    public int hashCode() {
        return Objects.hash(slotId, presenterUsername);
    }
}


