package com.watchtower.watchtower.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One step of the agent's reasoning loop for a given incident: which tool was
 * called, with what input, what it returned, and what the agent concluded.
 * This is the explainability artifact surfaced in the decision-log endpoint
 * and dashboard (Phases 5 and 8).
 */
@Entity
@Table(name = "agent_decision_logs")
public class AgentDecisionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Column(name = "step_number", nullable = false)
    private int stepNumber;

    @Column(name = "tool_name")
    private String toolName;

    @Column(name = "tool_input", columnDefinition = "TEXT")
    private String toolInput;

    @Column(name = "tool_output", columnDefinition = "TEXT")
    private String toolOutput;

    @Column(columnDefinition = "TEXT")
    private String reasoning;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AgentDecisionLog() {
        // JPA
    }

    public AgentDecisionLog(Incident incident, int stepNumber, String toolName,
                             String toolInput, String toolOutput, String reasoning) {
        this.incident = incident;
        this.stepNumber = stepNumber;
        this.toolName = toolName;
        this.toolInput = toolInput;
        this.toolOutput = toolOutput;
        this.reasoning = reasoning;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Incident getIncident() {
        return incident;
    }

    public int getStepNumber() {
        return stepNumber;
    }

    public String getToolName() {
        return toolName;
    }

    public String getToolInput() {
        return toolInput;
    }

    public String getToolOutput() {
        return toolOutput;
    }

    public String getReasoning() {
        return reasoning;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
