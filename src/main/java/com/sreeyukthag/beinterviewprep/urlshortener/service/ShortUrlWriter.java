package com.sreeyukthag.beinterviewprep.urlshortener.service;

import com.sreeyukthag.beinterviewprep.urlshortener.entity.ShortUrl;
import com.sreeyukthag.beinterviewprep.urlshortener.repository.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inserts in a transaction of its own: PostgreSQL aborts a transaction after a failed statement, so a
 * unique-constraint violation must end this transaction, not the caller's, for the caller to read and retry.
 */
@Component
@RequiredArgsConstructor
public class ShortUrlWriter {

    private final ShortUrlRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ShortUrl insert(ShortUrl shortUrl) {
        return repository.saveAndFlush(shortUrl);
    }
}
