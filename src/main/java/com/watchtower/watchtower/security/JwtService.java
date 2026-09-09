package com.watchtower.watchtower.security;

import com.watchtower.watchtower.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Issues and validates the JWTs backing Watchtower's stateless auth. The
 * user's role travels as a claim so JwtAuthenticationFilter can populate
 * Spring Security's authorities without a database lookup on every request.
 */
@Component
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String ROLE_CLAIM = "role";

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(@Value("${jwt.secret}") String configuredSecret,
                       @Value("${jwt.expiration-minutes}") long expirationMinutes) {
        this.expirationMinutes = expirationMinutes;
        this.key = resolveKey(configuredSecret);
    }

    private SecretKey resolveKey(String configuredSecret) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            log.warn("JWT_SECRET not set; generating a random signing key for this process only - "
                    + "existing tokens won't validate after a restart. Set JWT_SECRET for anything beyond local dev.");
            return Keys.secretKeyFor(SignatureAlgorithm.HS256);
        }
        // Hash whatever string is configured so any length still yields a
        // valid 256-bit HS256 key, rather than requiring the operator to
        // remember a minimum-length secret.
        try {
            byte[] hashed = MessageDigest.getInstance("SHA-256").digest(configuredSecret.getBytes(StandardCharsets.UTF_8));
            return Keys.hmacShaKeyFor(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available in this JVM", e);
        }
    }

    public String generateToken(String username, Role role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim(ROLE_CLAIM, role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    /**
     * @throws io.jsonwebtoken.JwtException if the token is malformed, has an
     *                                      invalid signature, or has expired
     */
    public Claims parseToken(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
