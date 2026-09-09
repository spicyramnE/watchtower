package com.watchtower.watchtower.dto;

public record DiagnosisResult(
        Long incidentId,
        String incidentStatus,
        int iterationsUsed,
        boolean concluded,
        String summary) {
}
