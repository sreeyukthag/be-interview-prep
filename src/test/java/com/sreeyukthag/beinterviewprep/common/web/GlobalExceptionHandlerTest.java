package com.sreeyukthag.beinterviewprep.common.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sreeyukthag.beinterviewprep.common.exception.ConflictException;
import com.sreeyukthag.beinterviewprep.common.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new StubController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void invalidBodyReturns400WithFieldErrors() throws Exception {
        String body = "{\"name\":\"\"}";

        mockMvc.perform(post("/stub").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void malformedBodyReturns400() throws Exception {
        mockMvc.perform(post("/stub").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"));
    }

    @Test
    void apiExceptionUsesItsOwnStatusAndCode() throws Exception {
        mockMvc.perform(get("/stub/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Stub not found: missing"));

        mockMvc.perform(get("/stub/taken"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TAKEN"));
    }

    @Test
    void unexpectedExceptionReturns500WithoutInternals() throws Exception {
        mockMvc.perform(get("/stub/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    record StubRequest(@NotBlank String name) {}

    @RestController
    static class StubController {

        @PostMapping("/stub")
        ApiResponse<String> create(@Valid @RequestBody StubRequest request) {
            return ApiResponse.ok(request.name());
        }

        @GetMapping("/stub/{id}")
        ApiResponse<String> find(@PathVariable String id) {
            return switch (id) {
                case "missing" -> throw new ResourceNotFoundException("Stub", id);
                case "taken" -> throw new ConflictException("TAKEN", "Stub already exists");
                case "boom" -> throw new IllegalStateException("database password leaked here");
                default -> ApiResponse.ok(id);
            };
        }
    }
}
