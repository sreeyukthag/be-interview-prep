package com.sreeyukthag.beinterviewprep.auth.service;

import com.sreeyukthag.beinterviewprep.auth.dto.request.LoginRequest;
import com.sreeyukthag.beinterviewprep.auth.dto.request.RegisterRequest;
import com.sreeyukthag.beinterviewprep.auth.dto.response.TokenResponse;
import com.sreeyukthag.beinterviewprep.auth.dto.response.UserResponse;
import com.sreeyukthag.beinterviewprep.auth.entity.Role;
import com.sreeyukthag.beinterviewprep.auth.entity.User;
import com.sreeyukthag.beinterviewprep.auth.exception.InvalidCredentialsException;
import com.sreeyukthag.beinterviewprep.auth.repository.UserRepository;
import com.sreeyukthag.beinterviewprep.common.exception.ConflictException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        return UserResponse.from(createUser(request.email(), request.password(), Role.USER));
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = userRepository
                .findByEmail(normalize(request.email()))
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        return tokenService.issue(user);
    }

    User createUser(String email, String rawPassword, Role role) {
        String normalizedEmail = normalize(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw emailTaken();
        }
        try {
            return userRepository.saveAndFlush(new User(normalizedEmail, passwordEncoder.encode(rawPassword), role));
        } catch (DataIntegrityViolationException ex) {
            // A concurrent registration won the race between the exists check and the insert.
            throw emailTaken();
        }
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static ConflictException emailTaken() {
        return new ConflictException("EMAIL_TAKEN", "An account with this email already exists");
    }
}
