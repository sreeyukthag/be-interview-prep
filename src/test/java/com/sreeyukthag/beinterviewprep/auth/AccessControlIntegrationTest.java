package com.sreeyukthag.beinterviewprep.auth;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sreeyukthag.beinterviewprep.auth.entity.Role;
import com.sreeyukthag.beinterviewprep.auth.entity.User;
import com.sreeyukthag.beinterviewprep.auth.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AccessControlIntegrationTest {

    private static final String PROTECTED = "/api/v1/users/me";
    private static final String ADMIN_USERS = "/api/v1/admin/users";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private AuthTestClient client;

    @BeforeEach
    void setUp() {
        client = new AuthTestClient(mockMvc, objectMapper);
    }

    @Test
    void userTokenOnAdminEndpointReturns403Json() throws Exception {
        String userToken = client.registerAndLogin(AuthTestClient.uniqueEmail());

        mockMvc.perform(get(ADMIN_USERS).header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void adminTokenOnAdminEndpointListsUsersPaged() throws Exception {
        String adminEmail = AuthTestClient.uniqueEmail();
        userRepository.save(new User(adminEmail, passwordEncoder.encode(AuthTestClient.PASSWORD), Role.ADMIN));
        client.register(AuthTestClient.uniqueEmail(), AuthTestClient.PASSWORD);
        String adminToken = client.accessTokenFor(adminEmail, AuthTestClient.PASSWORD);

        mockMvc.perform(get(ADMIN_USERS).param("size", "1").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(greaterThanOrEqualTo(2)));
    }

    @Test
    void loggedInUserSeesOwnProfileFromTokenSubject() throws Exception {
        String email = AuthTestClient.uniqueEmail();
        String token = client.registerAndLogin(email);

        mockMvc.perform(get(PROTECTED).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    void requestWithoutTokenReturns401Json() throws Exception {
        mockMvc.perform(get(PROTECTED))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void expiredTokenReturns401Json() throws Exception {
        Instant issuedAt = Instant.now().minus(Duration.ofHours(2));
        String expired = TestTokens.signed(jwtEncoder, "USER", issuedAt, issuedAt.plus(Duration.ofMinutes(15)));

        mockMvc.perform(get(PROTECTED).header(HttpHeaders.AUTHORIZATION, bearer(expired)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Access token is invalid or expired"));
    }

    @Test
    void tokenWithForgedRoleReturns401Json() throws Exception {
        Instant now = Instant.now();
        String userToken = TestTokens.signed(jwtEncoder, "USER", now, now.plus(Duration.ofMinutes(15)));
        String forged = TestTokens.withRoleSwappedKeepingSignature(userToken, "USER", "ADMIN");

        mockMvc.perform(get(ADMIN_USERS).header(HttpHeaders.AUTHORIZATION, bearer(forged)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void healthCheckIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/tasks", "/api/v1/urls/abc1234/stats"})
    void featureEndpointsRequireAToken(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void shorteningAUrlRequiresAToken() throws Exception {
        String body = "{\"url\":\"https://example.com\"}";

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loggedInUserCanUseFeatureEndpoints() throws Exception {
        String token = client.registerAndLogin(AuthTestClient.uniqueEmail());

        mockMvc.perform(get("/api/v1/tasks").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void shortLinkRedirectsArePublic() throws Exception {
        mockMvc.perform(get("/r/unknown1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
