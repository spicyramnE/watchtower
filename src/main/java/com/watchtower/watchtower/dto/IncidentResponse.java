package com.watchtower.watchtower.dto;

import com.watchtower.watchtower.entity.Incident;

import java.time.Instant;

public record IncidentResponse(
        Long id,
        String source,
        String serviceName,
        String severity,
        String rawPayload,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static IncidentResponse from(Incident incident) {
        return new IncidentResponse(
                incident.getId(),
                incident.getSource(),
                incident.getServiceName(),
                incident.getSeverity().name(),
                incident.getRawPayload(),
                incident.getStatus().name(),
                incident.getCreatedAt(),
                incident.getUpdatedAt()
        );
    }
}
