package com.watchtower.watchtower.dto;

public record RemediationProposal(Long incidentId, String status, String action, double confidence, String rationale) {
}
