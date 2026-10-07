package com.sreeyukthag.beinterviewprep.urlshortener.service;

import com.sreeyukthag.beinterviewprep.common.exception.ResourceNotFoundException;
import com.sreeyukthag.beinterviewprep.urlshortener.dto.request.ShortenUrlRequest;
import com.sreeyukthag.beinterviewprep.urlshortener.dto.response.ShortUrlResponse;
import com.sreeyukthag.beinterviewprep.urlshortener.dto.response.ShortUrlStatsResponse;
import com.sreeyukthag.beinterviewprep.urlshortener.entity.ShortUrl;
import com.sreeyukthag.beinterviewprep.urlshortener.exception.ShortCodeUnavailableException;
import com.sreeyukthag.beinterviewprep.urlshortener.exception.ShortUrlExpiredException;
import com.sreeyukthag.beinterviewprep.urlshortener.mapper.ShortUrlMapper;
import com.sreeyukthag.beinterviewprep.urlshortener.repository.ShortUrlRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortUrlService {

    static final int MAX_INSERT_ATTEMPTS = 5;

    private final ShortUrlRepository repository;
    private final ShortCodeGenerator codeGenerator;
    private final ShortUrlWriter writer;
    private final ShortUrlMapper mapper;

    /**
     * Deliberately not one transaction: each insert attempt commits or rolls back on its own, so after a
     * unique violation a fresh read can see the row another request committed.
     */
    public ShortUrlResponse shorten(ShortenUrlRequest request) {
        String originalUrl = request.url().trim();
        // The column stores microseconds; truncating keeps a repeat request equal to the stored expiry.
        Instant expiresAt =
                request.expiresAt() == null ? null : request.expiresAt().truncatedTo(ChronoUnit.MICROS);
        String dedupeKey = ShortUrl.dedupeKeyFor(originalUrl, expiresAt);

        ShortUrl shortUrl =
                repository.findByDedupeKey(dedupeKey).orElseGet(() -> insertWithFreshCode(originalUrl, expiresAt));
        return mapper.toResponse(shortUrl);
    }

    @Transactional(readOnly = true)
    public ShortUrlStatsResponse stats(String code) {
        return mapper.toStatsResponse(findByCode(code));
    }

    @Transactional
    public String resolveAndCountVisit(String code) {
        ShortUrl shortUrl = findByCode(code);
        if (shortUrl.isExpiredAt(Instant.now())) {
            throw new ShortUrlExpiredException(code);
        }
        repository.incrementVisitCount(code);
        return shortUrl.getOriginalUrl();
    }

    private ShortUrl findByCode(String code) {
        return repository.findByCode(code).orElseThrow(() -> new ResourceNotFoundException("Short URL", code));
    }

    /**
     * A unique violation means either another request just stored the same URL and expiry (return its
     * row) or the random code is taken (try a new one).
     */
    private ShortUrl insertWithFreshCode(String originalUrl, Instant expiresAt) {
        for (int attempt = 1; attempt <= MAX_INSERT_ATTEMPTS; attempt++) {
            ShortUrl candidate = new ShortUrl(codeGenerator.generate(), originalUrl, expiresAt);
            try {
                return writer.insert(candidate);
            } catch (DataIntegrityViolationException ex) {
                Optional<ShortUrl> stored = repository.findByDedupeKey(candidate.getDedupeKey());
                if (stored.isPresent()) {
                    return stored.orElseThrow();
                }
                log.warn(
                        "Short code {} already taken on attempt {}: {}",
                        candidate.getCode(),
                        attempt,
                        ex.getMostSpecificCause().getMessage());
            }
        }
        throw new ShortCodeUnavailableException(MAX_INSERT_ATTEMPTS);
    }
}
