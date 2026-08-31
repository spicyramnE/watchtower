package com.watchtower.watchtower.service;

import com.watchtower.watchtower.dto.RemediationExecutionResult;
import com.watchtower.watchtower.dto.RemediationProposal;
import com.watchtower.watchtower.entity.Incident;
import com.watchtower.watchtower.entity.IncidentStatus;
import com.watchtower.watchtower.entity.Severity;
import com.watchtower.watchtower.exception.IncidentNotFoundException;
import com.watchtower.watchtower.repository.IncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemediationServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    private RemediationService remediationService;

    @BeforeEach
    void setUp() {
        remediationService = new RemediationService(incidentRepository);
    }

    @Test
    void proposeRemediation_onNewIncident_movesToAwaitingApprovalWithStoredFields() {
        Incident incident = new Incident("github-actions", "payments-service", Severity.HIGH, "{}");
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RemediationProposal proposal = remediationService.proposeRemediation(1L, "Restart the deployment", 0.85, "Deployment timed out waiting for readiness");

        assertThat(proposal.status()).isEqualTo("AWAITING_APPROVAL");
        assertThat(proposal.action()).isEqualTo("Restart the deployment");
        assertThat(proposal.confidence()).isEqualTo(0.85);
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.AWAITING_APPROVAL);
        assertThat(incident.getProposedAction()).isEqualTo("Restart the deployment");
        assertThat(incident.getConfidenceScore()).isEqualTo(0.85);
    }

    @Test
    void proposeRemediation_onResolvedIncident_throwsIllegalState() {
        Incident incident = new Incident("github-actions", "payments-service", Severity.HIGH, "{}");
        incident.setStatus(IncidentStatus.RESOLVED);
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));

        assertThatThrownBy(() -> remediationService.proposeRemediation(1L, "action", 0.5, "rationale"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void proposeRemediation_onUnknownIncident_throwsNotFound() {
        when(incidentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> remediationService.proposeRemediation(999L, "action", 0.5, "rationale"))
                .isInstanceOf(IncidentNotFoundException.class);
    }

    @Test
    void executeRemediation_onAwaitingApproval_resolvesTheIncident() {
        Incident incident = new Incident("github-actions", "payments-service", Severity.HIGH, "{}");
        incident.setStatus(IncidentStatus.AWAITING_APPROVAL);
        incident.setProposedAction("Restart the deployment");
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RemediationExecutionResult result = remediationService.executeRemediation(1L);

        assertThat(result.status()).isEqualTo("RESOLVED");
        assertThat(result.message()).contains("Restart the deployment");
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
    }

    @Test
    void executeRemediation_withoutPendingProposal_throwsIllegalState() {
        Incident incident = new Incident("github-actions", "payments-service", Severity.HIGH, "{}");
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));

        assertThatThrownBy(() -> remediationService.executeRemediation(1L))
                .isInstanceOf(IllegalStateException.class);
    }
}
