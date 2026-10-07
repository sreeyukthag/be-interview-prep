package com.sreeyukthag.beinterviewprep.auth.dto.response;

import com.sreeyukthag.beinterviewprep.auth.entity.Role;
import com.sreeyukthag.beinterviewprep.auth.entity.User;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String email, Role role, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}
