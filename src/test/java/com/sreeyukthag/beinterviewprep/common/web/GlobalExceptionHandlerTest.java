package com.sreeyukthag.beinterviewprep.common.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sreeyukthag.beinterviewprep.common.exception.ConflictException;
import com.sreeyukthag.beinterviewprep.common.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.data.util.TypeInformation;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
    void unsupportedContentTypeReturns415() throws Exception {
        mockMvc.perform(post("/stub").contentType(MediaType.TEXT_PLAIN).content("name"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.message").value("Content-Type 'text/plain' is not supported"));
    }

    @Test
    void unparseableFieldValueReturns400NamingTheField() throws Exception {
        String body = "{\"name\":\"x\",\"level\":\"EXTREME\"}";

        mockMvc.perform(post("/stub").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("level"))
                .andExpect(jsonPath("$.errors[0].message").value("has an invalid value"));
    }

    @Test
    void unknownSortPropertyReturns400() throws Exception {
        mockMvc.perform(get("/stub/unknown-sort"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sort"))
                .andExpect(jsonPath("$.errors[0].message").value("has an unknown property: nope"));
    }

    @Test
    void optimisticLockConflictReturns409() throws Exception {
        mockMvc.perform(get("/stub/stale"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("CONCURRENT_MODIFICATION"));
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

    enum Level {
        LOW,
        HIGH
    }

    record StubRequest(@NotBlank String name, Level level) {}

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
                case "stale" -> throw new ObjectOptimisticLockingFailureException(StubRequest.class, id);
                case "unknown-sort" ->
                    throw new PropertyReferenceException("nope", TypeInformation.of(StubRequest.class), List.of());
                case "boom" -> throw new IllegalStateException("database password leaked here");
                default -> ApiResponse.ok(id);
            };
        }
    }
}
