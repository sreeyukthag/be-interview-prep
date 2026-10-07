package com.sreeyukthag.beinterviewprep.urlshortener.service;

import com.sreeyukthag.beinterviewprep.common.exception.ResourceNotFoundException;
import com.sreeyukthag.beinterviewprep.urlshortener.dto.request.ShortenUrlRequest;
import com.sreeyukthag.beinterviewprep.urlshortener.dto.response.ShortUrlResponse;
import com.sreeyukthag.beinterviewprep.urlshortener.dto.response.ShortUrlStatsResponse;
import com.sreeyukthag.beinterviewprep.urlshortener.entity.ShortUrl;
import com.sreeyukthag.beinterviewprep.urlshortener.exception.ShortUrlExpiredException;
import com.sreeyukthag.beinterviewprep.urlshortener.mapper.ShortUrlMapper;
import com.sreeyukthag.beinterviewprep.urlshortener.repository.ShortUrlRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShortUrlService {

    static final int MAX_CODE_ATTEMPTS = 5;

    private final ShortUrlRepository repository;
    private final ShortCodeGenerator codeGenerator;
    private final ShortUrlMapper mapper;

    @Transactional
    public ShortUrlResponse shorten(ShortenUrlRequest request) {
        String originalUrl = request.url().trim();
        // The column stores microseconds; truncating keeps a repeat request equal to the stored expiry.
        Instant expiresAt =
                request.expiresAt() == null ? null : request.expiresAt().truncatedTo(ChronoUnit.MICROS);

        ShortUrl shortUrl = repository
                .findFirstByOriginalUrlAndExpiresAtOrderByCreatedAtAsc(originalUrl, expiresAt)
                .orElseGet(() -> repository.save(new ShortUrl(newUniqueCode(), originalUrl, expiresAt)));
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

    private String newUniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = codeGenerator.generate();
            if (!repository.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("No unique short code after " + MAX_CODE_ATTEMPTS + " attempts");
    }
}
