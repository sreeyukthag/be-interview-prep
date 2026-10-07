package com.sreeyukthag.beinterviewprep.urlshortener.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sreeyukthag.beinterviewprep.common.exception.ResourceNotFoundException;
import com.sreeyukthag.beinterviewprep.urlshortener.dto.response.ShortUrlResponse;
import com.sreeyukthag.beinterviewprep.urlshortener.dto.response.ShortUrlStatsResponse;
import com.sreeyukthag.beinterviewprep.urlshortener.service.ShortUrlService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ShortUrlController.class)
class ShortUrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShortUrlService shortUrlService;

    @Test
    void shortenReturns201WithCodeAndShortUrl() throws Exception {
        ShortUrlResponse response =
                new ShortUrlResponse("abc1234", "http://sho.rt/r/abc1234", "https://example.com/page", null);
        when(shortUrlService.shorten(any())).thenReturn(response);
        String body = "{\"url\":\"https://example.com/page\"}";

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("abc1234"))
                .andExpect(jsonPath("$.data.shortUrl").value("http://sho.rt/r/abc1234"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not a url", "ftp://example.com/file", "javascript:alert(1)", "/relative/path", "https://"})
    void shortenRejectsInvalidUrlWithFieldError(String url) throws Exception {
        String body = "{\"url\":\"" + url + "\"}";

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("url"));
        verifyNoInteractions(shortUrlService);
    }

    @Test
    void shortenRejectsUrlLongerThan2048Chars() throws Exception {
        String body = "{\"url\":\"https://example.com/" + "a".repeat(2048) + "\"}";

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("url"));
    }

    @Test
    void shortenRejectsExpiryInThePast() throws Exception {
        String body = "{\"url\":\"https://example.com\",\"expiresAt\":\"2020-01-01T00:00:00Z\"}";

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("expiresAt"));
    }

    @Test
    void statsReturnsVisitCountOriginalUrlAndCreatedDate() throws Exception {
        ShortUrlStatsResponse stats = new ShortUrlStatsResponse(
                "abc1234",
                "http://sho.rt/r/abc1234",
                "https://example.com/page",
                42,
                Instant.parse("2026-10-01T10:00:00Z"),
                null);
        when(shortUrlService.stats("abc1234")).thenReturn(stats);

        mockMvc.perform(get("/api/v1/urls/abc1234/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalUrl").value("https://example.com/page"))
                .andExpect(jsonPath("$.data.visitCount").value(42))
                .andExpect(jsonPath("$.data.createdAt").value("2026-10-01T10:00:00Z"));
    }

    @Test
    void statsForUnknownCodeReturns404() throws Exception {
        when(shortUrlService.stats("missing")).thenThrow(new ResourceNotFoundException("Short URL", "missing"));

        mockMvc.perform(get("/api/v1/urls/missing/stats"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }
}
