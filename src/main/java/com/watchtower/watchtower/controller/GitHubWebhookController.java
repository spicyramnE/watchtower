package com.watchtower.watchtower.controller;

import com.watchtower.watchtower.entity.Incident;
import com.watchtower.watchtower.entity.Severity;
import com.watchtower.watchtower.entity.WebhookDelivery;
import com.watchtower.watchtower.repository.IncidentRepository;
import com.watchtower.watchtower.repository.WebhookDeliveryRepository;
import com.watchtower.watchtower.webhook.GitHubWebhookVerifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Real GitHub webhook ingestion for workflow_run events - the replacement
 * for the Simulate button as the primary way incidents enter the system.
 * Open in SecurityConfig (GitHub can't send a JWT); the HMAC signature is
 * what actually gates this endpoint, not Spring Security.
 */
@RestController
public class GitHubWebhookController {

    private final IncidentRepository incidentRepository;
    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final GitHubWebhookVerifier verifier;
    private final ObjectMapper objectMapper;

    public GitHubWebhookController(IncidentRepository incidentRepository,
                                    WebhookDeliveryRepository webhookDeliveryRepository,
                                    GitHubWebhookVerifier verifier,
                                    ObjectMapper objectMapper) {
        this.incidentRepository = incidentRepository;
        this.webhookDeliveryRepository = webhookDeliveryRepository;
        this.verifier = verifier;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/webhooks/github")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId,
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType) {

        if (!verifier.isValidSignature(rawBody, signature)) {
            return ResponseEntity.status(401).build();
        }

        if (deliveryId == null || webhookDeliveryRepository.existsById(deliveryId)) {
            return ResponseEntity.ok().build();
        }
        webhookDeliveryRepository.save(new WebhookDelivery(deliveryId));

        if (!"workflow_run".equals(eventType)) {
            return ResponseEntity.ok().build();
        }

        JsonNode root = objectMapper.readTree(rawBody);
        String action = root.path("action").asText("");
        JsonNode workflowRun = root.path("workflow_run");
        String conclusion = workflowRun.path("conclusion").asText("");

        if (!"completed".equals(action) || !"failure".equals(conclusion)) {
            return ResponseEntity.ok().build();
        }

        String repoName = root.path("repository").path("full_name").asText("unknown/unknown");
        Incident incident = new Incident("github-webhook", repoName, Severity.HIGH, rawBody);
        incidentRepository.save(incident);

        return ResponseEntity.ok().build();
    }
}
