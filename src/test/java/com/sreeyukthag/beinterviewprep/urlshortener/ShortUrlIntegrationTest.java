package com.sreeyukthag.beinterviewprep.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sreeyukthag.beinterviewprep.urlshortener.dto.request.ShortenUrlRequest;
import com.sreeyukthag.beinterviewprep.urlshortener.dto.response.ShortUrlResponse;
import com.sreeyukthag.beinterviewprep.urlshortener.entity.ShortUrl;
import com.sreeyukthag.beinterviewprep.urlshortener.repository.ShortUrlRepository;
import com.sreeyukthag.beinterviewprep.urlshortener.service.ShortUrlService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest
class ShortUrlIntegrationTest {

    private static final int CONCURRENT_VISITS = 50;

    @Autowired
    private ShortUrlService shortUrlService;

    @Autowired
    private ShortUrlRepository repository;

    @Test
    void shorteningSameUrlTwiceReturnsSameCodeAndOneRow() {
        String url = "https://example.com/dedupe";
        Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);

        ShortUrlResponse first = shortUrlService.shorten(new ShortenUrlRequest(url, null));
        ShortUrlResponse second = shortUrlService.shorten(new ShortenUrlRequest(url, null));
        ShortUrlResponse withExpiry = shortUrlService.shorten(new ShortenUrlRequest(url, expiresAt));
        ShortUrlResponse withSameExpiry = shortUrlService.shorten(new ShortenUrlRequest(url, expiresAt));

        assertThat(second.code()).isEqualTo(first.code());
        assertThat(withExpiry.code()).isNotEqualTo(first.code());
        assertThat(withSameExpiry.code()).isEqualTo(withExpiry.code());
        assertThat(repository.findAll())
                .filteredOn(shortUrl -> shortUrl.getOriginalUrl().equals(url))
                .hasSize(2);
    }

    @Test
    void databaseRejectsSecondRowForSameUrlWithoutExpiry() {
        String url = "https://example.com/db-enforced";
        repository.saveAndFlush(new ShortUrl("dbenf01", url, null));
        ShortUrl duplicate = new ShortUrl("dbenf02", url, null);

        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(duplicate));
    }

    @Test
    void simultaneousVisitsAreAllCounted() throws Exception {
        String code = shortUrlService
                .shorten(new ShortenUrlRequest("https://example.com/popular", null))
                .code();
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<String>> visits = new ArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_VISITS)) {
            for (int i = 0; i < CONCURRENT_VISITS; i++) {
                visits.add(pool.submit(() -> {
                    startGate.await();
                    return shortUrlService.resolveAndCountVisit(code);
                }));
            }
            startGate.countDown();
            for (Future<String> visit : visits) {
                visit.get();
            }
        }

        long visitCount = shortUrlService.stats(code).visitCount();
        assertThat(visitCount).isEqualTo(CONCURRENT_VISITS);
    }
}
