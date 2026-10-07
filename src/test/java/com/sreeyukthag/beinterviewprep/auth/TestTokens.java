package com.sreeyukthag.beinterviewprep.auth;

import com.sreeyukthag.beinterviewprep.auth.security.JwtClaims;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

final class TestTokens {

    private TestTokens() {}

    static String signed(JwtEncoder encoder, String role, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(UUID.randomUUID().toString())
                .claim(JwtClaims.EMAIL, "someone@example.com")
                .claim(JwtClaims.ROLES, List.of(role))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    static String withRoleSwappedKeepingSignature(String token, String fromRole, String toRole) {
        String[] parts = token.split("\\.");
        Base64.Decoder decoder = Base64.getUrlDecoder();
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String payload = new String(decoder.decode(parts[1]), StandardCharsets.UTF_8)
                .replace("\"" + fromRole + "\"", "\"" + toRole + "\"");
        return parts[0] + "." + encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];
    }
}
