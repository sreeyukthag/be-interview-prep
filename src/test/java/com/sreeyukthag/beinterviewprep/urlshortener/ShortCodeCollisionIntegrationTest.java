package com.sreeyukthag.beinterviewprep.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sreeyukthag.beinterviewprep.urlshortener.entity.ShortUrl;
import com.sreeyukthag.beinterviewprep.urlshortener.repository.ShortUrlRepository;
import com.sreeyukthag.beinterviewprep.urlshortener.service.ShortCodeGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Forces the generator to draw a code already in the table so the insert hits {@code uk_short_urls_code}.
 * The spy gives this class its own Spring context, so it also gets its own in-memory database: Liquibase
 * cannot re-apply its changelog table to the shared lower-cased H2 database from a second context.
 */
@SpringBootTest(
        properties =
                "spring.datasource.url=jdbc:h2:mem:collisiondb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@WithMockUser
class ShortCodeCollisionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShortUrlRepository repository;

    @MockitoSpyBean
    private ShortCodeGenerator codeGenerator;

    @Test
    void takenCodeIsRetriedWithFreshCodeInsteadOfFailing() throws Exception {
        repository.saveAndFlush(new ShortUrl("clash01", "https://example.com/first-owner", null));
        doReturn("clash01", "fresh01").when(codeGenerator).generate();

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/second-owner\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("fresh01"));

        verify(codeGenerator, times(2)).generate();
        assertThat(repository.findByCode("clash01"))
                .get()
                .extracting(ShortUrl::getOriginalUrl)
                .isEqualTo("https://example.com/first-owner");
    }

    @Test
    void exhaustedAttemptsReturn503InSharedErrorShape() throws Exception {
        repository.saveAndFlush(new ShortUrl("clash02", "https://example.com/always-taken", null));
        doReturn("clash02").when(codeGenerator).generate();

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/never-stored\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("SHORT_CODE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").exists());

        assertThat(repository.findByDedupeKey(ShortUrl.dedupeKeyFor("https://example.com/never-stored", null)))
                .isEmpty();
    }
}
