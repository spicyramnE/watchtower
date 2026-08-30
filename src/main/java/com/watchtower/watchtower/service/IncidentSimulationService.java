package com.watchtower.watchtower.service;

import com.watchtower.watchtower.dto.CreateIncidentRequest;
import com.watchtower.watchtower.entity.Incident;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Produces realistic, varied synthetic incidents so the rest of the system
 * (and demos) don't depend on a real CI/CD system for data. Backs the
 * dev/demo-only POST /incidents/simulate endpoint - not part of the
 * production API surface.
 */
@Service
public class IncidentSimulationService {

    private static final List<String> SERVICE_NAMES = List.of(
            "payments-service", "checkout-service", "auth-service",
            "notification-service", "inventory-service");

    private enum Scenario {
        FAILED_BUILD {
            @Override
            CreateIncidentRequest generate(String service) {
                return new CreateIncidentRequest("github-actions", service, "MEDIUM",
                        """
                        {"stage":"build","error":"Could not resolve dependency com.example:shared-lib:2.4.1",\
                        "exitCode":1,"log":"ERROR: Failed to resolve com.example:shared-lib:2.4.1 - not found in any configured repository"}""");
            }
        },
        DEPLOYMENT_TIMEOUT {
            @Override
            CreateIncidentRequest generate(String service) {
                return new CreateIncidentRequest("github-actions", service, "HIGH",
                        """
                        {"stage":"deploy","error":"Deployment did not become healthy within timeout",\
                        "timeoutSeconds":300,"revision":"v1.14.2","log":"Waiting for rollout to finish: 2 of 4 updated replicas are available..."}""");
            }
        },
        OOM_CRASH {
            @Override
            CreateIncidentRequest generate(String service) {
                return new CreateIncidentRequest("github-actions", service, "CRITICAL",
                        """
                        {"stage":"test","error":"OOMKilled","exitCode":137,\
                        "log":"java.lang.OutOfMemoryError: Java heap space at com.example.TestRunner.run(TestRunner.java:88)"}""");
            }
        },
        FLAKY_TEST {
            @Override
            CreateIncidentRequest generate(String service) {
                return new CreateIncidentRequest("github-actions", service, "LOW",
                        """
                        {"stage":"test","error":"Intermittent test failure","test":"OrderServiceIT.shouldProcessOrderWithinSla",\
                        "failureRateLast20Runs":"3/20","log":"AssertionError: expected <200> but was <504> (timing-dependent)"}""");
            }
        },
        FAILED_ROLLBACK {
            @Override
            CreateIncidentRequest generate(String service) {
                return new CreateIncidentRequest("github-actions", service, "CRITICAL",
                        """
                        {"stage":"rollback","error":"Rollback to previous revision failed","fromRevision":"v1.14.2","toRevision":"v1.14.1",\
                        "log":"Error: previous revision image no longer available in registry"}""");
            }
        };

        abstract CreateIncidentRequest generate(String service);
    }

    private final IncidentService incidentService;

    public IncidentSimulationService(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    public Incident simulateIncident() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Scenario scenario = Scenario.values()[random.nextInt(Scenario.values().length)];
        String service = SERVICE_NAMES.get(random.nextInt(SERVICE_NAMES.size()));
        return incidentService.createIncident(scenario.generate(service));
    }
}
