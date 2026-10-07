package com.sreeyukthag.beinterviewprep.auth.service;

import com.sreeyukthag.beinterviewprep.auth.dto.response.UserResponse;
import com.sreeyukthag.beinterviewprep.auth.repository.UserRepository;
import com.sreeyukthag.beinterviewprep.common.exception.ResourceNotFoundException;
import com.sreeyukthag.beinterviewprep.common.web.PageResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    static final int MAX_PAGE_SIZE = 100;

    // Fixed order: stable paging, and no client-chosen sort on columns such as password_hash.
    private static final Sort ORDER = Sort.by("createdAt", "id");

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserResponse getProfile(UUID userId) {
        return userRepository
                .findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listUsers(Pageable pageable) {
        Pageable page =
                PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE), ORDER);
        return PageResponse.of(userRepository.findAll(page), UserResponse::from);
    }
}
