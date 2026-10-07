package com.sreeyukthag.beinterviewprep.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sreeyukthag.beinterviewprep.common.exception.ResourceNotFoundException;
import com.sreeyukthag.beinterviewprep.urlshortener.dto.request.ShortenUrlRequest;
import com.sreeyukthag.beinterviewprep.urlshortener.dto.response.ShortUrlResponse;
import com.sreeyukthag.beinterviewprep.urlshortener.dto.response.ShortUrlStatsResponse;
import com.sreeyukthag.beinterviewprep.urlshortener.entity.ShortUrl;
import com.sreeyukthag.beinterviewprep.urlshortener.exception.ShortCodeTakenException;
import com.sreeyukthag.beinterviewprep.urlshortener.exception.ShortCodeUnavailableException;
import com.sreeyukthag.beinterviewprep.urlshortener.exception.ShortUrlExpiredException;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ShortUrlServiceTest {

    private static final String URL = "https://example.com/a/very/long/path";
    private static final String DEDUPE_KEY = ShortUrl.dedupeKeyFor(URL, null);

    @Mock
    private ShortUrlRepository repository;

    @Mock
    private ShortCodeGenerator codeGenerator;

    @Mock
    private ShortUrlWriter writer;

    private ShortUrlService service;

    @BeforeEach
    void setUp() {
        service = new ShortUrlService(repository, codeGenerator, writer, new ShortUrlMapper("http://sho.rt/r"));
    }

    @Test
    void shortenStoresNewMappingWithGeneratedCode() {
        when(repository.findByDedupeKey(DEDUPE_KEY)).thenReturn(Optional.empty());
        when(codeGenerator.generate()).thenReturn("abc1234");
        when(writer.insert(any(ShortUrl.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShortUrlResponse response = service.shorten(new ShortenUrlRequest(URL, null, null));

        assertThat(response.code()).isEqualTo("abc1234");
        assertThat(response.shortUrl()).isEqualTo("http://sho.rt/r/abc1234");
        assertThat(response.originalUrl()).isEqualTo(URL);
        assertThat(response.expiresAt()).isNull();
    }

    @Test
    void shortenReturnsExistingMappingForSameUrlAndExpiry() {
        Instant expiresAt = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        ShortUrl existing = new ShortUrl("exist12", URL, expiresAt);
        when(repository.findByDedupeKey(ShortUrl.dedupeKeyFor(URL, expiresAt))).thenReturn(Optional.of(existing));

        ShortUrlResponse response = service.shorten(new ShortenUrlRequest(URL, expiresAt, null));

        assertThat(response.code()).isEqualTo("exist12");
        verifyNoInteractions(writer, codeGenerator);
    }

    @Test
    void shortenReturnsRowStoredByConcurrentRequestWhenInsertConflicts() {
        ShortUrl winner = new ShortUrl("winner1", URL, null);
        when(repository.findByDedupeKey(DEDUPE_KEY)).thenReturn(Optional.empty(), Optional.of(winner));
        when(codeGenerator.generate()).thenReturn("loser01");
        when(writer.insert(any(ShortUrl.class)))
                .thenThrow(new DataIntegrityViolationException("uk_short_urls_dedupe_key"));

        ShortUrlResponse response = service.shorten(new ShortenUrlRequest(URL, null, null));

        assertThat(response.code()).isEqualTo("winner1");
        verify(writer, times(1)).insert(any());
    }

    @Test
    void shortenRetriesWithNewCodeWhenCodeIsTaken() {
        when(repository.findByDedupeKey(DEDUPE_KEY)).thenReturn(Optional.empty());
        when(codeGenerator.generate()).thenReturn("taken01", "free001");
        when(writer.insert(any(ShortUrl.class)))
                .thenThrow(new DataIntegrityViolationException("uk_short_urls_code"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ShortUrlResponse response = service.shorten(new ShortenUrlRequest(URL, null, null));

        assertThat(response.code()).isEqualTo("free001");
        ArgumentCaptor<ShortUrl> attempts = ArgumentCaptor.forClass(ShortUrl.class);
        verify(writer, times(2)).insert(attempts.capture());
        assertThat(attempts.getAllValues()).extracting(ShortUrl::getCode).containsExactly("taken01", "free001");
    }

    @Test
    void shortenGivesUpWithServiceUnavailableAfterMaxAttempts() {
        when(repository.findByDedupeKey(DEDUPE_KEY)).thenReturn(Optional.empty());
        when(codeGenerator.generate()).thenReturn("taken01");
        when(writer.insert(any(ShortUrl.class))).thenThrow(new DataIntegrityViolationException("uk_short_urls_code"));
        ShortenUrlRequest request = new ShortenUrlRequest(URL, null, null);

        ShortCodeUnavailableException ex =
                assertThrows(ShortCodeUnavailableException.class, () -> service.shorten(request));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(ex.getErrorCode()).isEqualTo("SHORT_CODE_UNAVAILABLE");
        verify(writer, times(ShortUrlService.MAX_INSERT_ATTEMPTS)).insert(any());
    }

    @Test
    void shortenWithCustomCodeStoresAliasWithoutDedupeKey() {
        when(repository.findByCode("promo")).thenReturn(Optional.empty());
        when(writer.insert(any(ShortUrl.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShortUrlResponse response = service.shorten(new ShortenUrlRequest(URL, null, "promo"));

        assertThat(response.code()).isEqualTo("promo");
        assertThat(response.shortUrl()).isEqualTo("http://sho.rt/r/promo");
        ArgumentCaptor<ShortUrl> stored = ArgumentCaptor.forClass(ShortUrl.class);
        verify(writer).insert(stored.capture());
        assertThat(stored.getValue().getDedupeKey()).isNull();
        verifyNoInteractions(codeGenerator);
        verify(repository, never()).findByDedupeKey(anyString());
    }

    @Test
    void shortenWithCustomCodeAlreadyPointingAtSameUrlReturnsItWithoutInsert() {
        when(repository.findByCode("promo")).thenReturn(Optional.of(ShortUrl.alias("promo", URL, null)));

        ShortUrlResponse response = service.shorten(new ShortenUrlRequest(URL, null, "promo"));

        assertThat(response.code()).isEqualTo("promo");
        verifyNoInteractions(writer);
    }

    @Test
    void shortenWithCustomCodeTakenByAnotherUrlThrowsConflict() {
        when(repository.findByCode("promo"))
                .thenReturn(Optional.of(ShortUrl.alias("promo", "https://example.com/other", null)));
        ShortenUrlRequest request = new ShortenUrlRequest(URL, null, "promo");

        ShortCodeTakenException ex = assertThrows(ShortCodeTakenException.class, () -> service.shorten(request));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getErrorCode()).isEqualTo("SHORT_CODE_TAKEN");
        verifyNoInteractions(writer);
    }

    @Test
    void shortenWithCustomCodeTakenWithDifferentExpiryThrowsConflict() {
        Instant expiresAt = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        when(repository.findByCode("promo")).thenReturn(Optional.of(ShortUrl.alias("promo", URL, null)));
        ShortenUrlRequest request = new ShortenUrlRequest(URL, expiresAt, "promo");

        assertThrows(ShortCodeTakenException.class, () -> service.shorten(request));
    }

    @Test
    void shortenWithCustomCodeReturnsWinnerWhenConcurrentRetryInsertedSameMapping() {
        when(repository.findByCode("promo"))
                .thenReturn(Optional.empty(), Optional.of(ShortUrl.alias("promo", URL, null)));
        when(writer.insert(any(ShortUrl.class))).thenThrow(new DataIntegrityViolationException("uk_short_urls_code"));

        ShortUrlResponse response = service.shorten(new ShortenUrlRequest(URL, null, "promo"));

        assertThat(response.code()).isEqualTo("promo");
        verify(writer, times(1)).insert(any());
    }

    @Test
    void shortenWithCustomCodeThrowsConflictWhenConcurrentRequestClaimedItForAnotherUrl() {
        when(repository.findByCode("promo"))
                .thenReturn(Optional.empty(), Optional.of(ShortUrl.alias("promo", "https://example.com/other", null)));
        when(writer.insert(any(ShortUrl.class))).thenThrow(new DataIntegrityViolationException("uk_short_urls_code"));
        ShortenUrlRequest request = new ShortenUrlRequest(URL, null, "promo");

        assertThrows(ShortCodeTakenException.class, () -> service.shorten(request));

        verify(writer, times(1)).insert(any());
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

    @Test
    void resolveAndCountVisitIncrementsAndReturnsOriginalUrl() {
        when(repository.findByCode("abc1234")).thenReturn(Optional.of(new ShortUrl("abc1234", URL, null)));

        String target = service.resolveAndCountVisit("abc1234");

        assertThat(target).isEqualTo(URL);
        verify(repository).incrementVisitCount("abc1234");
    }

    @Test
    void resolveAndCountVisitForExpiredCodeThrowsGoneWithoutCounting() {
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        when(repository.findByCode("old1234")).thenReturn(Optional.of(new ShortUrl("old1234", URL, past)));

        assertThrows(ShortUrlExpiredException.class, () -> service.resolveAndCountVisit("old1234"));

        verify(repository, never()).incrementVisitCount(anyString());
    }

    @Test
    void resolveAndCountVisitForUnknownCodeThrowsNotFoundWithoutCounting() {
        when(repository.findByCode("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.resolveAndCountVisit("missing"));

        verify(repository, never()).incrementVisitCount(anyString());
    }
}
