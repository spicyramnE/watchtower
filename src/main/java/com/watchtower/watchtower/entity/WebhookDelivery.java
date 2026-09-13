package com.watchtower.watchtower.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Records GitHub's X-GitHub-Delivery id for every webhook processed.
 * GitHub retries deliveries that don't get a timely 2xx, so the same event
 * can arrive twice - this is the dedup guard against creating a duplicate
 * Incident for it.
 */
@Entity
@Table(name = "webhook_deliveries")
public class WebhookDelivery {

    @Id
    private String id;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected WebhookDelivery() {
        // JPA
    }

    public WebhookDelivery(String id) {
        this.id = id;
        this.receivedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
