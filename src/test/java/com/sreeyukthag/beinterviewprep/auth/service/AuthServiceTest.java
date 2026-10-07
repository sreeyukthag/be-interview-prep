package com.sreeyukthag.beinterviewprep.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sreeyukthag.beinterviewprep.auth.dto.request.LoginRequest;
import com.sreeyukthag.beinterviewprep.auth.dto.request.RegisterRequest;
import com.sreeyukthag.beinterviewprep.auth.dto.response.TokenResponse;
import com.sreeyukthag.beinterviewprep.auth.entity.Role;
import com.sreeyukthag.beinterviewprep.auth.entity.User;
import com.sreeyukthag.beinterviewprep.auth.exception.InvalidCredentialsException;
import com.sreeyukthag.beinterviewprep.auth.repository.UserRepository;
import com.sreeyukthag.beinterviewprep.common.exception.ConflictException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String PASSWORD = "correct-horse";

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenService tokenService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, tokenService);
    }

    @Test
    void registerStoresBcryptHashNeverTheRawPassword() {
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);

        authService.register(new RegisterRequest("  Alice@Example.com ", PASSWORD));

        verify(userRepository).saveAndFlush(saved.capture());
        User user = saved.getValue();
        assertThat(user.getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("$2a$");
        assertThat(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void registerRejectsAnEmailThatIsAlreadyTaken() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);
        RegisterRequest request = new RegisterRequest("alice@example.com", PASSWORD);

        ConflictException ex = assertThrows(ConflictException.class, () -> authService.register(request));

        assertThat(ex.getErrorCode()).isEqualTo("EMAIL_TAKEN");
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerTurnsALostInsertRaceIntoConflict() {
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("uk_users_email"));
        RegisterRequest request = new RegisterRequest("alice@example.com", PASSWORD);

        assertThrows(ConflictException.class, () -> authService.register(request));
    }

    @Test
    void loginIssuesTokenForCorrectPassword() {
        User user = new User("alice@example.com", passwordEncoder.encode(PASSWORD), Role.USER);
        TokenResponse token = TokenResponse.bearer("token", 900);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(tokenService.issue(user)).thenReturn(token);

        TokenResponse response = authService.login(new LoginRequest("Alice@Example.com", PASSWORD));

        assertThat(response).isEqualTo(token);
    }

    @Test
    void loginRejectsWrongPasswordWithoutIssuingToken() {
        User user = new User("alice@example.com", passwordEncoder.encode(PASSWORD), Role.USER);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        LoginRequest request = new LoginRequest("alice@example.com", "wrong-password");

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));

        verify(tokenService, never()).issue(any());
    }

    @Test
    void loginRejectsUnknownEmailWithTheSameError() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        LoginRequest request = new LoginRequest("nobody@example.com", PASSWORD);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
    }
}
