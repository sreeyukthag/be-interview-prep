package com.sreeyukthag.beinterviewprep.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

public record AuthTestClient(MockMvc mockMvc, ObjectMapper objectMapper) {

    public static final String PASSWORD = "test-password-123";

    public static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    public ResultActions register(String email, String password) throws Exception {
        return postJson("/api/v1/auth/register", Map.of("email", email, "password", password));
    }

    public ResultActions login(String email, String password) throws Exception {
        return postJson("/api/v1/auth/login", Map.of("email", email, "password", password));
    }

    public String registerAndLogin(String email) throws Exception {
        register(email, PASSWORD);
        return accessTokenFor(email, PASSWORD);
    }

    public String accessTokenFor(String email, String password) throws Exception {
        String body = login(email, password).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/accessToken").asText();
    }

    private ResultActions postJson(String path, Object body) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }
}
