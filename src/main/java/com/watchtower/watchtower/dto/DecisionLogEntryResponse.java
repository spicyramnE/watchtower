package com.watchtower.watchtower.dto;

import com.watchtower.watchtower.entity.AgentDecisionLog;

import java.time.Instant;

public record DecisionLogEntryResponse(
        int stepNumber,
        String toolName,
        String toolInput,
        String toolOutput,
        String reasoning,
        Instant createdAt) {

    public static DecisionLogEntryResponse from(AgentDecisionLog log) {
        return new DecisionLogEntryResponse(
                log.getStepNumber(),
                log.getToolName(),
                log.getToolInput(),
                log.getToolOutput(),
                log.getReasoning(),
                log.getCreatedAt());
    }
}
