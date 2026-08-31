package com.watchtower.watchtower.service;

import com.watchtower.watchtower.dto.RemediationExecutionResult;
import com.watchtower.watchtower.dto.RemediationProposal;
import com.watchtower.watchtower.entity.Incident;
import com.watchtower.watchtower.entity.IncidentStatus;
import com.watchtower.watchtower.exception.IncidentNotFoundException;
import com.watchtower.watchtower.repository.IncidentRepository;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Backs the propose_remediation and execute_remediation tools. A proposal
 * is stored directly on its Incident (one active proposal per incident,
 * mirroring the NEW -> ... -> AWAITING_APPROVAL -> RESOLVED/REJECTED
 * lifecycle) rather than in a separate table - there's no case yet where an
 * incident needs more than one live proposal.
 * <p>
 * execute_remediation currently only enforces that a proposal is pending;
 * Phase 6 adds the actual human approval gate in front of it.
 */
@Service
public class RemediationService {

    private static final Set<IncidentStatus> TERMINAL_STATUSES = Set.of(IncidentStatus.RESOLVED, IncidentStatus.REJECTED);

    private final IncidentRepository incidentRepository;

    public RemediationService(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
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
}
