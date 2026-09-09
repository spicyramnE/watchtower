package com.watchtower.watchtower.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectIncidentRequest(
        @NotBlank(message = "reason is required")
        String reason
) {
}
