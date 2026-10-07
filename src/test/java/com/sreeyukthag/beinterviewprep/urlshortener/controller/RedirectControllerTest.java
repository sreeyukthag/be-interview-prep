package com.sreeyukthag.beinterviewprep.urlshortener.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sreeyukthag.beinterviewprep.common.exception.ResourceNotFoundException;
import com.sreeyukthag.beinterviewprep.urlshortener.exception.ShortUrlExpiredException;
import com.sreeyukthag.beinterviewprep.urlshortener.service.ShortUrlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RedirectController.class)
class RedirectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShortUrlService shortUrlService;

    @Test
    void knownCodeRedirectsWith302ToOriginalUrl() throws Exception {
        when(shortUrlService.resolveAndCountVisit("abc1234")).thenReturn("https://example.com/page?q=1");

        mockMvc.perform(get("/r/abc1234"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/page?q=1"));
    }

    @Test
    void unknownCodeReturns404() throws Exception {
        when(shortUrlService.resolveAndCountVisit("missing"))
                .thenThrow(new ResourceNotFoundException("Short URL", "missing"));

        mockMvc.perform(get("/r/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void expiredCodeReturns410() throws Exception {
        when(shortUrlService.resolveAndCountVisit("old1234")).thenThrow(new ShortUrlExpiredException("old1234"));

        mockMvc.perform(get("/r/old1234"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.errorCode").value("SHORT_URL_EXPIRED"));
    }
}
