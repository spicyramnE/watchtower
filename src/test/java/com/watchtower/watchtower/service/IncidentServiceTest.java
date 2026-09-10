package com.watchtower.watchtower.service;

import com.watchtower.watchtower.dto.CreateIncidentRequest;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    private IncidentService incidentService;

    @BeforeEach
    void setUp() {
        incidentService = new IncidentService(incidentRepository);
    }

    @Test
    void createIncident_persistsWithCorrectFields() {
        CreateIncidentRequest request = new CreateIncidentRequest(
                "github-actions", "payments-service", "HIGH", "{\"error\":\"OOMKilled\"}");
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        Incident result = incidentService.createIncident(request);

        assertThat(result.getSource()).isEqualTo("github-actions");
        assertThat(result.getServiceName()).isEqualTo("payments-service");
        assertThat(result.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(result.getStatus()).isEqualTo(IncidentStatus.NEW);
        verify(incidentRepository).save(any(Incident.class));
    }

    @Test
    void getIncident_whenExists_returnsIt() {
        Incident incident = new Incident("github-actions", "auth-service", Severity.LOW, "{}");
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));

        Incident result = incidentService.getIncident(1L);

        assertThat(result.getServiceName()).isEqualTo("auth-service");
    }

    @Test
    void getIncident_whenMissing_throwsNotFound() {
        when(incidentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> incidentService.getIncident(999L))
                .isInstanceOf(IncidentNotFoundException.class);
    }

    @Test
    void listIncidents_withNullStatus_returnsAll() {
        when(incidentRepository.findAll()).thenReturn(List.of(
                new Incident("src", "svc-a", Severity.LOW, "{}"),
                new Incident("src", "svc-b", Severity.HIGH, "{}")));

        List<Incident> result = incidentService.listIncidents(null);

        assertThat(result).hasSize(2);
        verify(incidentRepository).findAll();
    }

    @Test
    void listIncidents_withStatus_filtersCorrectly() {
        when(incidentRepository.findByStatus(IncidentStatus.NEW)).thenReturn(List.of(
                new Incident("src", "svc-a", Severity.LOW, "{}")));

        List<Incident> result = incidentService.listIncidents(IncidentStatus.NEW);

        assertThat(result).hasSize(1);
        verify(incidentRepository).findByStatus(IncidentStatus.NEW);
    }
}
