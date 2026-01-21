package org.example.semscan.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * User profile DTO aligned with the username-first backend contract.
 */
public class User {

    @SerializedName("bguUsername")
    private String bguUsername;

    @SerializedName("email")
    private String email;

    @SerializedName("firstName")
    private String firstName;

    @SerializedName("lastName")
    private String lastName;

    @SerializedName("degree")
    private String degree; // MSc | PhD

    @SerializedName("participationPreference")
    private String participationPreference; // PARTICIPANT_ONLY | PRESENTER_ONLY | BOTH

    public User() {
    }

    public String getBguUsername() {
        return bguUsername;
    }

    public void setBguUsername(String bguUsername) {
        this.bguUsername = bguUsername;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getFullName() {
        if (firstName == null && lastName == null) {
            return null;
        }
        if (firstName == null) {
            return lastName;
        }
        if (lastName == null) {
            return firstName;
        }
        return firstName + " " + lastName;
    }

    public String getDegree() {
        return degree;
    }

    public void setDegree(String degree) {
        this.degree = degree;
    }

    public String getParticipationPreference() {
        return participationPreference;
    }

    public void setParticipationPreference(String participationPreference) {
        this.participationPreference = participationPreference;
    }
}
