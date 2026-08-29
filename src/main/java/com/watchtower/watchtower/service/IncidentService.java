package com.watchtower.watchtower.service;

import com.watchtower.watchtower.dto.CreateIncidentRequest;
import com.watchtower.watchtower.entity.Incident;
import com.watchtower.watchtower.entity.IncidentStatus;
import com.watchtower.watchtower.entity.Severity;
import com.watchtower.watchtower.exception.IncidentNotFoundException;
import com.watchtower.watchtower.repository.IncidentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IncidentService {

    private final IncidentRepository incidentRepository;

    public IncidentService(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    public Incident createIncident(CreateIncidentRequest request) {
        Incident incident = new Incident(
                request.source(),
                request.serviceName(),
                Severity.valueOf(request.severity()),
                request.rawPayload()
        );
        return incidentRepository.save(incident);
    }

    public Incident getIncident(Long id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new IncidentNotFoundException(id));
    }

    public List<Incident> listIncidents(IncidentStatus status) {
        if (status == null) {
            return incidentRepository.findAll();
        }
        return incidentRepository.findByStatus(status);
    }
}
