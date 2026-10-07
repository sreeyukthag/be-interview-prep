package com.sreeyukthag.beinterviewprep.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sreeyukthag.beinterviewprep.common.exception.ResourceNotFoundException;
import com.sreeyukthag.beinterviewprep.urlshortener.dto.request.ShortenUrlRequest;
import com.sreeyukthag.beinterviewprep.urlshortener.dto.response.ShortUrlResponse;
import com.sreeyukthag.beinterviewprep.urlshortener.dto.response.ShortUrlStatsResponse;
import com.sreeyukthag.beinterviewprep.urlshortener.entity.ShortUrl;
import com.sreeyukthag.beinterviewprep.urlshortener.mapper.ShortUrlMapper;
import com.sreeyukthag.beinterviewprep.urlshortener.repository.ShortUrlRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShortUrlServiceTest {

    private static final String URL = "https://example.com/a/very/long/path";

    @Mock
    private ShortUrlRepository repository;

    @Mock
    private ShortCodeGenerator codeGenerator;

    private ShortUrlService service;

    @BeforeEach
    void setUp() {
        service = new ShortUrlService(repository, codeGenerator, new ShortUrlMapper("http://sho.rt/r"));
    }

    @Test
    void shortenStoresNewMappingWithGeneratedCode() {
        when(repository.findFirstByOriginalUrlAndExpiresAtOrderByCreatedAtAsc(URL, null))
                .thenReturn(Optional.empty());
        when(codeGenerator.generate()).thenReturn("abc1234");
        when(repository.existsByCode("abc1234")).thenReturn(false);
        when(repository.save(any(ShortUrl.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShortUrlResponse response = service.shorten(new ShortenUrlRequest(URL, null));

        assertThat(response.code()).isEqualTo("abc1234");
        assertThat(response.shortUrl()).isEqualTo("http://sho.rt/r/abc1234");
        assertThat(response.originalUrl()).isEqualTo(URL);
        assertThat(response.expiresAt()).isNull();
    }

    @Test
    void shortenReturnsExistingMappingForSameUrlAndExpiry() {
        Instant expiresAt = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        ShortUrl existing = new ShortUrl("exist12", URL, expiresAt);
        when(repository.findFirstByOriginalUrlAndExpiresAtOrderByCreatedAtAsc(URL, expiresAt))
                .thenReturn(Optional.of(existing));

        ShortUrlResponse response = service.shorten(new ShortenUrlRequest(URL, expiresAt));

        assertThat(response.code()).isEqualTo("exist12");
        verify(repository, never()).save(any());
        verify(codeGenerator, never()).generate();
    }

    @Test
    void shortenRetriesWhenGeneratedCodeIsTaken() {
        when(repository.findFirstByOriginalUrlAndExpiresAtOrderByCreatedAtAsc(URL, null))
                .thenReturn(Optional.empty());
        when(codeGenerator.generate()).thenReturn("taken01", "free001");
        when(repository.existsByCode("taken01")).thenReturn(true);
        when(repository.existsByCode("free001")).thenReturn(false);
        when(repository.save(any(ShortUrl.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.shorten(new ShortenUrlRequest(URL, null));

        ArgumentCaptor<ShortUrl> saved = ArgumentCaptor.forClass(ShortUrl.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getCode()).isEqualTo("free001");
    }

    @Test
    void shortenGivesUpAfterMaxCollisions() {
        when(repository.findFirstByOriginalUrlAndExpiresAtOrderByCreatedAtAsc(URL, null))
                .thenReturn(Optional.empty());
        when(codeGenerator.generate()).thenReturn("taken01");
        when(repository.existsByCode(anyString())).thenReturn(true);
        ShortenUrlRequest request = new ShortenUrlRequest(URL, null);

        assertThrows(IllegalStateException.class, () -> service.shorten(request));

        verify(codeGenerator, times(ShortUrlService.MAX_CODE_ATTEMPTS)).generate();
        verify(repository, never()).save(any());
    }

    @Test
    void statsReturnsVisitCountAndOriginalUrl() {
        when(repository.findByCode("abc1234")).thenReturn(Optional.of(new ShortUrl("abc1234", URL, null)));

        ShortUrlStatsResponse stats = service.stats("abc1234");

        assertThat(stats.originalUrl()).isEqualTo(URL);
        assertThat(stats.visitCount()).isZero();
    }

    @Test
    void statsForUnknownCodeThrowsNotFound() {
        when(repository.findByCode("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.stats("missing"));
    }
}
