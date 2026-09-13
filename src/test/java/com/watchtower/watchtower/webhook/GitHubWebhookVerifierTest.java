package com.watchtower.watchtower.webhook;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubWebhookVerifierTest {

    private String sign(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void isValidSignature_withMatchingSignature_returnsTrue() throws Exception {
        GitHubWebhookVerifier verifier = new GitHubWebhookVerifier("test-secret");
        String payload = "{\"action\":\"completed\"}";

        assertThat(verifier.isValidSignature(payload, sign("test-secret", payload))).isTrue();
    }

    @Test
    void isValidSignature_withWrongSecret_returnsFalse() throws Exception {
        GitHubWebhookVerifier verifier = new GitHubWebhookVerifier("test-secret");
        String payload = "{\"action\":\"completed\"}";

        assertThat(verifier.isValidSignature(payload, sign("wrong-secret", payload))).isFalse();
    }

    @Test
    void isValidSignature_withTamperedPayload_returnsFalse() throws Exception {
        GitHubWebhookVerifier verifier = new GitHubWebhookVerifier("test-secret");
        String signature = sign("test-secret", "{\"action\":\"completed\"}");

        assertThat(verifier.isValidSignature("{\"action\":\"tampered\"}", signature)).isFalse();
    }

    @Test
    void isValidSignature_withoutSha256Prefix_returnsFalse() {
        GitHubWebhookVerifier verifier = new GitHubWebhookVerifier("test-secret");

        assertThat(verifier.isValidSignature("{}", "deadbeef")).isFalse();
    }

    @Test
    void isValidSignature_withMissingHeader_returnsFalse() {
        GitHubWebhookVerifier verifier = new GitHubWebhookVerifier("test-secret");

        assertThat(verifier.isValidSignature("{}", null)).isFalse();
    }

    @Test
    void isValidSignature_whenNotConfigured_rejectsEvenAValidLookingSignature() {
        GitHubWebhookVerifier verifier = new GitHubWebhookVerifier("");

        assertThat(verifier.isValidSignature("{}", "sha256=" + "a".repeat(64))).isFalse();
    }

    @Test
    void isConfigured_withBlankSecret_isFalse() {
        assertThat(new GitHubWebhookVerifier("").isConfigured()).isFalse();
        assertThat(new GitHubWebhookVerifier(null).isConfigured()).isFalse();
        assertThat(new GitHubWebhookVerifier("real-secret").isConfigured()).isTrue();
    }
}
