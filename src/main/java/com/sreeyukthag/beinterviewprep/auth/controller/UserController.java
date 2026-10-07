package com.sreeyukthag.beinterviewprep.auth.controller;

import com.sreeyukthag.beinterviewprep.auth.dto.response.UserResponse;
import com.sreeyukthag.beinterviewprep.auth.service.UserService;
import com.sreeyukthag.beinterviewprep.common.web.ApiResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
class UserController {

    private final UserService userService;

    @GetMapping("/me")
    ApiResponse<UserResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(userService.getProfile(UUID.fromString(jwt.getSubject())));
    }
}
