package com.sreeyukthag.beinterviewprep.urlshortener.dto.response;

import java.time.Instant;

public record ShortUrlResponse(String code, String shortUrl, String originalUrl, Instant expiresAt) {}
