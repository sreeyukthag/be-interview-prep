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
import java.util.concurrent.Callable;
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
    private static final int CONCURRENT_SHORTENS = 20;

    @Autowired
    private ShortUrlService shortUrlService;

    @Autowired
    private ShortUrlRepository repository;

    @Test
    void shorteningSameUrlTwiceReturnsSameCodeAndOneRow() {
        String url = "https://example.com/dedupe";
        Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);

        ShortUrlResponse first = shortUrlService.shorten(new ShortenUrlRequest(url, null, null));
        ShortUrlResponse second = shortUrlService.shorten(new ShortenUrlRequest(url, null, null));
        ShortUrlResponse withExpiry = shortUrlService.shorten(new ShortenUrlRequest(url, expiresAt, null));
        ShortUrlResponse withSameExpiry = shortUrlService.shorten(new ShortenUrlRequest(url, expiresAt, null));

        assertThat(second.code()).isEqualTo(first.code());
        assertThat(withExpiry.code()).isNotEqualTo(first.code());
        assertThat(withSameExpiry.code()).isEqualTo(withExpiry.code());
        assertThat(rowsFor(url)).hasSize(2);
    }

    @Test
    void databaseRejectsSecondRowForSameUrlWithoutExpiry() {
        String url = "https://example.com/db-enforced";
        repository.saveAndFlush(new ShortUrl("dbenf01", url, null));
        ShortUrl duplicate = new ShortUrl("dbenf02", url, null);

        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(duplicate));
    }

    @Test
    void simultaneousShortensOfSameNewUrlReturnOneCodeAndStoreOneRow() throws Exception {
        String url = "https://example.com/race/" + System.nanoTime();
        ShortenUrlRequest request = new ShortenUrlRequest(url, null, null);

        List<String> codes = runConcurrently(
                CONCURRENT_SHORTENS, () -> shortUrlService.shorten(request).code());

        assertThat(codes).hasSize(CONCURRENT_SHORTENS).containsOnly(codes.get(0));
        assertThat(rowsFor(url)).singleElement().extracting(ShortUrl::getCode).isEqualTo(codes.get(0));
    }

    @Test
    void simultaneousVisitsAreAllCounted() throws Exception {
        String code = shortUrlService
                .shorten(new ShortenUrlRequest("https://example.com/popular", null, null))
                .code();

        runConcurrently(CONCURRENT_VISITS, () -> shortUrlService.resolveAndCountVisit(code));

        long visitCount = shortUrlService.stats(code).visitCount();
        assertThat(visitCount).isEqualTo(CONCURRENT_VISITS);
    }

    private List<ShortUrl> rowsFor(String url) {
        return repository.findAll().stream()
                .filter(shortUrl -> shortUrl.getOriginalUrl().equals(url))
                .toList();
    }

    /** Releases every task at once through a latch so they genuinely race, then waits for all results. */
    private static <T> List<T> runConcurrently(int threads, Callable<T> task) throws Exception {
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        List<T> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    startGate.await();
                    return task.call();
                }));
            }
            startGate.countDown();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
        }
        return results;
    }
}
