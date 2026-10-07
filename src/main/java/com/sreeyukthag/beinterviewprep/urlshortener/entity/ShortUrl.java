package com.sreeyukthag.beinterviewprep.urlshortener.entity;

import com.sreeyukthag.beinterviewprep.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "short_urls")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShortUrl extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 8)
    private String code;

    @Column(name = "original_url", nullable = false, length = 2048)
    private String originalUrl;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "visit_count", nullable = false)
    private long visitCount;

    public ShortUrl(String code, String originalUrl, Instant expiresAt) {
        this.code = code;
        this.originalUrl = originalUrl;
        this.expiresAt = expiresAt;
    }

    public boolean isExpiredAt(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }
}
