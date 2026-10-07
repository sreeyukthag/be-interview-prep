package com.sreeyukthag.beinterviewprep.urlshortener.dto.response;

import java.time.Instant;

public record ShortUrlStatsResponse(
        String code, String shortUrl, String originalUrl, long visitCount, Instant createdAt, Instant expiresAt) {}
