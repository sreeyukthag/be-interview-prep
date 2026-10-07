package com.sreeyukthag.beinterviewprep.auth.controller;

import com.sreeyukthag.beinterviewprep.auth.dto.request.LoginRequest;
import com.sreeyukthag.beinterviewprep.auth.dto.request.RegisterRequest;
import com.sreeyukthag.beinterviewprep.auth.dto.response.TokenResponse;
import com.sreeyukthag.beinterviewprep.auth.dto.response.UserResponse;
import com.sreeyukthag.beinterviewprep.auth.service.AuthService;
import com.sreeyukthag.beinterviewprep.common.web.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request), "User registered");
    }

    @PostMapping("/login")
    ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }
}
