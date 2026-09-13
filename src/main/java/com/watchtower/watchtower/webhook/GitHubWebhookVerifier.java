package com.watchtower.watchtower.webhook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Verifies GitHub's X-Hub-Signature-256 header against the raw request body.
 * Fails closed: an unconfigured secret rejects every signature rather than
 * accepting unverified payloads, matching this project's pattern of
 * declaring "not configured" instead of silently degrading into something
 * insecure.
 */
@Component
public class GitHubWebhookVerifier {

    private static final String SIGNATURE_PREFIX = "sha256=";

    private final String secret;

    public GitHubWebhookVerifier(@Value("${github.webhook.secret}") String secret) {
        this.secret = secret;
    }

    public boolean isConfigured() {
        return secret != null && !secret.isBlank();
    }

    public boolean isValidSignature(String payload, String signatureHeader) {
        if (!isConfigured() || signatureHeader == null || !signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            return false;
        }

        String computedHex = computeHmac(payload);
        String providedHex = signatureHeader.substring(SIGNATURE_PREFIX.length());
        return MessageDigest.isEqual(
                computedHex.getBytes(StandardCharsets.UTF_8),
                providedHex.getBytes(StandardCharsets.UTF_8));
    }

    private String computeHmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
