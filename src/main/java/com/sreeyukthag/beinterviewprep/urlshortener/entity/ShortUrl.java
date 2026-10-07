package com.sreeyukthag.beinterviewprep.urlshortener.entity;

import com.sreeyukthag.beinterviewprep.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    /** Null for a custom-code alias, so aliases never collide with the generated code for the same URL. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "dedupe_key", unique = true, length = 64)
    private String dedupeKey;

    @Column(name = "visit_count", nullable = false)
    private long visitCount;

    public ShortUrl(String code, String originalUrl, Instant expiresAt) {
        this.code = code;
        this.originalUrl = originalUrl;
        this.expiresAt = expiresAt;
        this.dedupeKey = dedupeKeyFor(originalUrl, expiresAt);
    }

    public static ShortUrl alias(String code, String originalUrl, Instant expiresAt) {
        ShortUrl alias = new ShortUrl(code, originalUrl, expiresAt);
        alias.dedupeKey = null;
        return alias;
    }

    /**
     * SHA-256 of the URL and expiry, so the database can enforce one row per pair with a unique index
     * that stays small for 2048-char URLs and treats "no expiry" as a value rather than a distinct NULL.
     */
    public static String dedupeKeyFor(String originalUrl, Instant expiresAt) {
        String source = originalUrl + "\n" + (expiresAt == null ? "" : expiresAt.toString());
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required on every Java platform", ex);
        }
    }

    public boolean pointsTo(String originalUrl, Instant expiresAt) {
        return this.originalUrl.equals(originalUrl) && Objects.equals(this.expiresAt, expiresAt);
    }

    public boolean isExpiredAt(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }
}
