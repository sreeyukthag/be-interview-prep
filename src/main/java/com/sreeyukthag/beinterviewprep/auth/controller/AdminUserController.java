package com.sreeyukthag.beinterviewprep.auth.controller;

import com.sreeyukthag.beinterviewprep.auth.dto.response.UserResponse;
import com.sreeyukthag.beinterviewprep.auth.service.UserService;
import com.sreeyukthag.beinterviewprep.common.web.ApiResponse;
import com.sreeyukthag.beinterviewprep.common.web.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
class AdminUserController {

    private final UserService userService;

    @GetMapping
    ApiResponse<PageResponse<UserResponse>> list(@PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(userService.listUsers(pageable));
    }
}
