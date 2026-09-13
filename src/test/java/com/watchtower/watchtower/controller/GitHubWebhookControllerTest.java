package com.watchtower.watchtower.controller;

import com.watchtower.watchtower.repository.IncidentRepository;
import com.watchtower.watchtower.repository.WebhookDeliveryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real webhook path end to end against real Postgres: HMAC
 * verification, delivery dedup, and event filtering - the same real
 * mechanics an actual GitHub delivery would hit, not simulated shortcuts.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithAnonymousUser // GitHub sends no JWT - this endpoint is gated by HMAC, not Spring Security
@TestPropertySource(properties = "github.webhook.secret=test-webhook-secret")
class GitHubWebhookControllerTest {

    private static final String SECRET = "test-webhook-secret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private WebhookDeliveryRepository webhookDeliveryRepository;

    private String sign(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private String workflowRunPayload(String action, String conclusion, String repoFullName) {
        return """
                {"action":"%s","workflow_run":{"conclusion":"%s"},"repository":{"full_name":"%s"}}
                """.formatted(action, conclusion, repoFullName).strip();
    }

    @Test
    void handleWebhook_withValidSignatureAndFailedRun_createsRealIncident() throws Exception {
        String payload = workflowRunPayload("completed", "failure", "octocat/hello-world");
        String deliveryId = UUID.randomUUID().toString();
        long before = incidentRepository.count();

        mockMvc.perform(post("/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", sign(payload))
                        .header("X-GitHub-Delivery", deliveryId)
                        .header("X-GitHub-Event", "workflow_run")
                        .content(payload))
                .andExpect(status().isOk());

        assertThat(incidentRepository.count()).isEqualTo(before + 1);
        var incident = incidentRepository.findAll().stream()
                .filter(i -> "github-webhook".equals(i.getSource()))
                .reduce((first, second) -> second) // most recently inserted
                .orElseThrow();
        assertThat(incident.getServiceName()).isEqualTo("octocat/hello-world");
    }

    @Test
    void handleWebhook_withInvalidSignature_returns401AndCreatesNothing() throws Exception {
        String payload = workflowRunPayload("completed", "failure", "octocat/hello-world");
        long before = incidentRepository.count();

        mockMvc.perform(post("/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", "sha256=" + "0".repeat(64))
                        .header("X-GitHub-Delivery", UUID.randomUUID().toString())
                        .header("X-GitHub-Event", "workflow_run")
                        .content(payload))
                .andExpect(status().isUnauthorized());

        assertThat(incidentRepository.count()).isEqualTo(before);
    }

    @Test
    void handleWebhook_withDuplicateDeliveryId_onlyCreatesOneIncident() throws Exception {
        String payload = workflowRunPayload("completed", "failure", "octocat/duplicate-test");
        String deliveryId = UUID.randomUUID().toString();
        long before = incidentRepository.count();

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/webhooks/github")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Hub-Signature-256", sign(payload))
                            .header("X-GitHub-Delivery", deliveryId)
                            .header("X-GitHub-Event", "workflow_run")
                            .content(payload))
                    .andExpect(status().isOk());
        }

        assertThat(incidentRepository.count()).isEqualTo(before + 1);
        assertThat(webhookDeliveryRepository.existsById(deliveryId)).isTrue();
    }

    @Test
    void handleWebhook_withSuccessfulRun_createsNoIncident() throws Exception {
        String payload = workflowRunPayload("completed", "success", "octocat/hello-world");
        long before = incidentRepository.count();

        mockMvc.perform(post("/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", sign(payload))
                        .header("X-GitHub-Delivery", UUID.randomUUID().toString())
                        .header("X-GitHub-Event", "workflow_run")
                        .content(payload))
                .andExpect(status().isOk());

        assertThat(incidentRepository.count()).isEqualTo(before);
    }

    @Test
    void handleWebhook_withNonWorkflowRunEvent_createsNoIncident() throws Exception {
        String payload = workflowRunPayload("completed", "failure", "octocat/hello-world");
        long before = incidentRepository.count();

        mockMvc.perform(post("/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", sign(payload))
                        .header("X-GitHub-Delivery", UUID.randomUUID().toString())
                        .header("X-GitHub-Event", "push")
                        .content(payload))
                .andExpect(status().isOk());

        assertThat(incidentRepository.count()).isEqualTo(before);
    }
}
