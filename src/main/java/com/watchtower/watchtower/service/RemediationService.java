package com.watchtower.watchtower.service;

import com.watchtower.watchtower.dto.RemediationExecutionResult;
import com.watchtower.watchtower.dto.RemediationProposal;
import com.watchtower.watchtower.entity.AgentDecisionLog;
import com.watchtower.watchtower.entity.Incident;
import com.watchtower.watchtower.entity.IncidentStatus;
import com.watchtower.watchtower.exception.IncidentNotFoundException;
import com.watchtower.watchtower.repository.AgentDecisionLogRepository;
import com.watchtower.watchtower.repository.IncidentRepository;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Backs the propose_remediation and execute_remediation tools, and the
 * human approve/reject endpoints (Phase 6). A proposal is stored directly
 * on its Incident (one active proposal per incident, mirroring the
 * NEW -> ... -> AWAITING_APPROVAL -> RESOLVED/REJECTED lifecycle) rather
 * than in a separate table - there's no case yet where an incident needs
 * more than one live proposal.
 * <p>
 * execute_remediation enforces the AWAITING_APPROVAL guard - the same
 * enforcement approveRemediation relies on rather than duplicating, so
 * nothing can execute without that gate no matter which entry point is
 * used. Approve and reject are additionally written to AgentDecisionLog
 * alongside the agent's own steps, so the full incident history - agent
 * reasoning and human governance - lives in one place.
 */
@Service
public class RemediationService {

    private static final Set<IncidentStatus> TERMINAL_STATUSES = Set.of(IncidentStatus.RESOLVED, IncidentStatus.REJECTED);

    private final IncidentRepository incidentRepository;
    private final AgentDecisionLogRepository decisionLogRepository;

    public RemediationService(IncidentRepository incidentRepository, AgentDecisionLogRepository decisionLogRepository) {
        this.incidentRepository = incidentRepository;
        this.decisionLogRepository = decisionLogRepository;
    }

    public RemediationProposal proposeRemediation(Long incidentId, String action, double confidence, String rationale) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IncidentNotFoundException(incidentId));

        if (TERMINAL_STATUSES.contains(incident.getStatus())) {
            throw new IllegalStateException(
                    "Cannot propose remediation for incident " + incidentId + " in terminal status " + incident.getStatus());
        }

        incident.setProposedAction(action);
        incident.setConfidenceScore(confidence);
        incident.setRationale(rationale);
        incident.setStatus(IncidentStatus.AWAITING_APPROVAL);
        Incident saved = incidentRepository.save(incident);

        return new RemediationProposal(saved.getId(), saved.getStatus().name(), action, confidence, rationale);
    }

    public RemediationExecutionResult executeRemediation(Long incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IncidentNotFoundException(incidentId));

        if (incident.getStatus() != IncidentStatus.AWAITING_APPROVAL) {
            throw new IllegalStateException(
                    "Incident " + incidentId + " has no approved remediation pending (current status: " + incident.getStatus() + ")");
        }

        incident.setStatus(IncidentStatus.RESOLVED);
        Incident saved = incidentRepository.save(incident);

        return new RemediationExecutionResult(saved.getId(), saved.getStatus().name(),
                "Simulated execution of: " + saved.getProposedAction());
    }

    /**
     * Approves and executes the pending proposal in one step (matches the
     * doc's endpoint contract: "approve the proposed remediation and
     * execute it"). Delegates the AWAITING_APPROVAL guard and the actual
     * RESOLVED transition to executeRemediation rather than re-checking it
     * here, so there is exactly one place that decides whether execution is
     * allowed.
     */
    public Incident approveRemediation(Long incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IncidentNotFoundException(incidentId));
        String proposedAction = incident.getProposedAction();

        executeRemediation(incidentId);

        Incident resolved = incidentRepository.findById(incidentId).orElseThrow();
        logHumanDecision(resolved, "human_approval", "Approved by reviewer; executed action: " + proposedAction);
        return resolved;
    }

    public Incident rejectRemediation(Long incidentId, String reason) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IncidentNotFoundException(incidentId));

        if (incident.getStatus() != IncidentStatus.AWAITING_APPROVAL) {
            throw new IllegalStateException(
                    "Incident " + incidentId + " has no proposal awaiting approval (current status: " + incident.getStatus() + ")");
        }

        incident.setStatus(IncidentStatus.REJECTED);
        Incident saved = incidentRepository.save(incident);
        logHumanDecision(saved, "human_rejection", "Rejected by reviewer: " + reason);
        return saved;
    }

    private void logHumanDecision(Incident incident, String label, String reasoning) {
        int nextStep = decisionLogRepository.findByIncidentIdOrderByStepNumberAsc(incident.getId()).stream()
                .mapToInt(AgentDecisionLog::getStepNumber)
                .max()
                .orElse(0) + 1;
        decisionLogRepository.save(new AgentDecisionLog(incident, nextStep, label, null, null, reasoning));
    }
}
