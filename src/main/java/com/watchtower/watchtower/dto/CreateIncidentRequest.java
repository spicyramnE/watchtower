package com.watchtower.watchtower.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateIncidentRequest(

        @NotBlank(message = "source is required")
        String source,

        @NotBlank(message = "serviceName is required")
        String serviceName,

        @NotBlank(message = "severity is required")
        @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL", message = "severity must be one of LOW, MEDIUM, HIGH, CRITICAL")
        String severity,

        @NotBlank(message = "rawPayload is required")
        String rawPayload
) {
}
