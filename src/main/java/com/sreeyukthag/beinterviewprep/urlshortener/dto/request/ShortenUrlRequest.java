package com.sreeyukthag.beinterviewprep.urlshortener.dto.request;

import com.sreeyukthag.beinterviewprep.urlshortener.validation.HttpUrl;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record ShortenUrlRequest(
        @NotBlank @Size(max = 2048) @HttpUrl String url,
        @Future Instant expiresAt,

        @Pattern(regexp = "[A-Za-z0-9_-]{3,8}", message = "must be 3 to 8 letters, digits, '-' or '_'")
        String customCode) {}
