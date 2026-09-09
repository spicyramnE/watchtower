package com.watchtower.watchtower.security;

import com.watchtower.watchtower.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    @Test
    void generateThenParse_roundTripsUsernameAndRole() {
        JwtService jwtService = new JwtService("test-secret", 60);

        String token = jwtService.generateToken("approver", Role.APPROVER);
        Claims claims = jwtService.parseToken(token);

        assertThat(claims.getSubject()).isEqualTo("approver");
        assertThat(claims.get("role", String.class)).isEqualTo("APPROVER");
    }

    @Test
    void parseToken_withExpiredToken_throws() {
        JwtService jwtService = new JwtService("test-secret", 0);

        String token = jwtService.generateToken("viewer", Role.VIEWER);

        assertThatThrownBy(() -> jwtService.parseToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void parseToken_withTamperedToken_throws() {
        JwtService jwtService = new JwtService("test-secret", 60);
        String token = jwtService.generateToken("viewer", Role.VIEWER);
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("a") ? "b" : "a");

        assertThatThrownBy(() -> jwtService.parseToken(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void parseToken_signedWithDifferentSecret_throws() {
        JwtService signer = new JwtService("secret-one", 60);
        JwtService verifier = new JwtService("secret-two", 60);
        String token = signer.generateToken("viewer", Role.VIEWER);

        assertThatThrownBy(() -> verifier.parseToken(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void twoServicesWithBlankSecret_generateIndependentRandomKeys() {
        JwtService serviceA = new JwtService("", 60);
        JwtService serviceB = new JwtService(null, 60);
        String token = serviceA.generateToken("viewer", Role.VIEWER);

        assertThatThrownBy(() -> serviceB.parseToken(token))
                .isInstanceOf(JwtException.class);
    }
}
