package edu.bgu.semscanapi.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload to check if a user exists by BGU username.
 */
public class UserExistsRequest {

    @NotBlank(message = "username is required")
    private String username;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}


