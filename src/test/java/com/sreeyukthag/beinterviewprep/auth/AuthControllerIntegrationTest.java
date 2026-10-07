package com.sreeyukthag.beinterviewprep.auth;

import static com.sreeyukthag.beinterviewprep.auth.AuthTestClient.PASSWORD;
import static com.sreeyukthag.beinterviewprep.auth.AuthTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sreeyukthag.beinterviewprep.auth.repository.UserRepository;
import com.sreeyukthag.beinterviewprep.auth.security.JwtClaims;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private UserRepository userRepository;

    private AuthTestClient client;

    @BeforeEach
    void setUp() {
        client = new AuthTestClient(mockMvc, objectMapper);
    }

    @Test
    void registerCreatesUserWithUserRoleAndNeverReturnsThePassword() throws Exception {
        String email = uniqueEmail();

        client.register(email, PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        String storedHash = userRepository.findByEmail(email).orElseThrow().getPasswordHash();
        assertThat(storedHash).isNotEqualTo(PASSWORD).startsWith("$2a$");
    }

    @Test
    void registerWithTakenEmailReturns409EvenWithDifferentCase() throws Exception {
        String email = uniqueEmail();
        client.register(email, PASSWORD);

        client.register(email.toUpperCase(), PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_TAKEN"));
    }

    @Test
    void registerWithInvalidInputReturns400PerField() throws Exception {
        client.register("not-an-email", "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void loginReturnsBearerTokenThatExpiresIn15Minutes() throws Exception {
        String email = uniqueEmail();
        client.register(email, PASSWORD);
        String userId = userRepository.findByEmail(email).orElseThrow().getId().toString();

        String body = client.login(email, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(900))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Jwt jwt = jwtDecoder.decode(
                objectMapper.readTree(body).at("/data/accessToken").asText());
        assertThat(jwt.getSubject()).isEqualTo(userId);
        assertThat(jwt.getClaimAsStringList(JwtClaims.ROLES)).containsExactly("USER");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void loginWithWrongPasswordReturns401WithGenericMessage() throws Exception {
        String email = uniqueEmail();
        client.register(email, PASSWORD);

        client.login(email, "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void loginWithUnknownEmailReturnsTheSame401() throws Exception {
        client.login(uniqueEmail(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }
}
