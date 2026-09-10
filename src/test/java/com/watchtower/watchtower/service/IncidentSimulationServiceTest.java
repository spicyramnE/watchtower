package com.watchtower.watchtower.service;

import com.watchtower.watchtower.entity.Incident;
import com.watchtower.watchtower.entity.IncidentStatus;
import com.watchtower.watchtower.repository.IncidentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class IncidentSimulationServiceTest {

    @Autowired
    private IncidentSimulationService simulationService;

    @Autowired
    private IncidentRepository incidentRepository;

    @Test
    void simulateIncident_createsPersistedIncidentWithValidFields() {
        Incident incident = simulationService.simulateIncident();

        assertThat(incident.getId()).isNotNull();
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.NEW);
        assertThat(incident.getSource()).isEqualTo("github-actions");
        assertThat(incident.getServiceName()).isNotBlank();
        assertThat(incident.getRawPayload()).isNotBlank();
        assertThat(incident.getSeverity()).isNotNull();

        assertThat(incidentRepository.findById(incident.getId())).isPresent();
    }

    @Test
    void simulateIncident_producesVariedScenariosAndServices() {
        Set<String> services = new HashSet<>();
        Set<String> payloads = new HashSet<>();
        for (int i = 0; i < 30; i++) {
            Incident incident = simulationService.simulateIncident();
            services.add(incident.getServiceName());
            payloads.add(incident.getRawPayload());
        }

        assertThat(services.size()).isGreaterThan(1);
        assertThat(payloads.size()).isGreaterThan(1);
    }
}
