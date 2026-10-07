package com.sreeyukthag.beinterviewprep.urlshortener.mapper;

import com.sreeyukthag.beinterviewprep.urlshortener.dto.response.ShortUrlResponse;
import com.sreeyukthag.beinterviewprep.urlshortener.dto.response.ShortUrlStatsResponse;
import com.sreeyukthag.beinterviewprep.urlshortener.entity.ShortUrl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ShortUrlMapper {

    private final String baseUrl;

    public ShortUrlMapper(@Value("${app.short-url.base-url}") String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    public ShortUrlResponse toResponse(ShortUrl shortUrl) {
        return new ShortUrlResponse(
                shortUrl.getCode(), shortUrlFor(shortUrl), shortUrl.getOriginalUrl(), shortUrl.getExpiresAt());
    }

    public ShortUrlStatsResponse toStatsResponse(ShortUrl shortUrl) {
        return new ShortUrlStatsResponse(
                shortUrl.getCode(),
                shortUrlFor(shortUrl),
                shortUrl.getOriginalUrl(),
                shortUrl.getVisitCount(),
                shortUrl.getCreatedAt(),
                shortUrl.getExpiresAt());
    }

    private String shortUrlFor(ShortUrl shortUrl) {
        return baseUrl + shortUrl.getCode();
    }
}
