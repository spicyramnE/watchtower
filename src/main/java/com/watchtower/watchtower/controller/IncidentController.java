package com.watchtower.watchtower.controller;

import com.watchtower.watchtower.dto.CreateIncidentRequest;
import com.watchtower.watchtower.dto.IncidentResponse;
import com.watchtower.watchtower.entity.Incident;
import com.watchtower.watchtower.entity.IncidentStatus;
import com.watchtower.watchtower.service.IncidentService;
import com.watchtower.watchtower.service.IncidentSimulationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class IncidentController {

    private final IncidentService incidentService;
    private final IncidentSimulationService incidentSimulationService;

    public IncidentController(IncidentService incidentService,
                               IncidentSimulationService incidentSimulationService) {
        this.incidentService = incidentService;
        this.incidentSimulationService = incidentSimulationService;
    }

    @PostMapping("/incidents")
    public ResponseEntity<IncidentResponse> createIncident(@Valid @RequestBody CreateIncidentRequest request) {
        Incident incident = incidentService.createIncident(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(IncidentResponse.from(incident));
    }

    /**
     * Dev/demo-only: produces a realistic synthetic incident so the rest of the
     * system can be exercised without a real CI/CD system feeding it. Not part
     * of the production API surface.
     */
    @PostMapping("/incidents/simulate")
    public ResponseEntity<IncidentResponse> simulateIncident() {
        Incident incident = incidentSimulationService.simulateIncident();
        return ResponseEntity.status(HttpStatus.CREATED).body(IncidentResponse.from(incident));
    }

    @GetMapping("/incidents/{id}")
    public IncidentResponse getIncident(@PathVariable Long id) {
        return IncidentResponse.from(incidentService.getIncident(id));
    }

    @GetMapping("/incidents")
    public List<IncidentResponse> listIncidents(@RequestParam(required = false) IncidentStatus status) {
        return incidentService.listIncidents(status).stream()
                .map(IncidentResponse::from)
                .toList();
    }
}
