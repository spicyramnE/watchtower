package com.watchtower.watchtower.controller;

import tools.jackson.databind.ObjectMapper;
import com.watchtower.watchtower.dto.CreateIncidentRequest;
import com.watchtower.watchtower.entity.Incident;
import com.watchtower.watchtower.entity.Severity;
import com.watchtower.watchtower.repository.IncidentRepository;
import com.watchtower.watchtower.service.RemediationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the full Spring context (controller, service, JPA/Postgres, validation)
 * without binding a real network socket - see README's "Local development notes"
 * for why the app can't be run with an actual embedded server in this environment.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser // any authenticated user satisfies every route here except approve/reject - overridden per-test where role matters
class IncidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private RemediationService remediationService;

    @Test
    void createIncident_withValidBody_returns201AndPersistedRow() throws Exception {
        CreateIncidentRequest request = new CreateIncidentRequest(
                "github-actions", "payments-service", "HIGH", "{\"error\":\"OOMKilled\"}");

        mockMvc.perform(post("/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", greaterThan(0)))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.severity").value("HIGH"))
                .andExpect(jsonPath("$.serviceName").value("payments-service"));
    }

    @Test
    void createIncident_withInvalidSeverity_returns400WithStructuredError() throws Exception {
        String badRequest = """
                {"source":"github-actions","serviceName":"payments-service","severity":"WRONG","rawPayload":"{}"}""";

        mockMvc.perform(post("/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.messages", hasSize(greaterThan(0))));
    }

    @Test
    void createIncident_withMissingField_returns400() throws Exception {
        String badRequest = """
                {"serviceName":"payments-service","severity":"HIGH","rawPayload":"{}"}""";

        mockMvc.perform(post("/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getIncident_afterCreate_returnsSameIncident() throws Exception {
        CreateIncidentRequest request = new CreateIncidentRequest(
                "synthetic", "checkout-service", "CRITICAL", "{\"error\":\"deploy timeout\"}");

        String createBody = mockMvc.perform(post("/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(createBody).get("id").asLong();

        mockMvc.perform(get("/incidents/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceName").value("checkout-service"));
    }

    @Test
    void getIncident_withUnknownId_returns404() throws Exception {
        mockMvc.perform(get("/incidents/999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listIncidents_filteredByStatus_returnsOnlyMatching() throws Exception {
        mockMvc.perform(get("/incidents").param("status", "NEW"))
                .andExpect(status().isOk());
    }

    @Test
    void listIncidents_withInvalidStatus_returns400() throws Exception {
        mockMvc.perform(get("/incidents").param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void simulateIncident_returns201WithNewSyntheticIncident() throws Exception {
        mockMvc.perform(post("/incidents/simulate"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", greaterThan(0)))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.source").value("github-actions"));
    }

    @Test
    void simulateIncident_calledRepeatedly_producesVariedScenarios() throws Exception {
        Set<String> distinctPayloads = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            String body = mockMvc.perform(post("/incidents/simulate"))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            distinctPayloads.add(objectMapper.readTree(body).get("rawPayload").asText());
        }
        // 5 scenario templates x 5 service names - 20 draws should hit well over 1 distinct payload
        assertThat(distinctPayloads.size()).isGreaterThan(1);
    }

    @Test
    @WithMockUser(roles = "APPROVER")
    void fullLifecycle_proposeThenApprove_resolvesTheIncidentAndListsInAwaitingApprovalUntilThen() throws Exception {
        Incident incident = incidentRepository.save(
                new Incident("github-actions", "payments-service", Severity.HIGH, "{\"error\":\"OOMKilled\"}"));
        remediationService.proposeRemediation(incident.getId(), "Restart the deployment", 0.8, "OOMKilled in logs");

        mockMvc.perform(get("/incidents/awaiting-approval"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + incident.getId() + ")]").exists());

        mockMvc.perform(post("/incidents/" + incident.getId() + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        mockMvc.perform(get("/incidents/" + incident.getId() + "/decision-log"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[-1].toolName").value("human_approval"));
    }

    @Test
    @WithMockUser(roles = "APPROVER")
    void rejectIncident_withReason_marksRejectedAndLogsIt() throws Exception {
        Incident incident = incidentRepository.save(
                new Incident("github-actions", "payments-service", Severity.HIGH, "{}"));
        remediationService.proposeRemediation(incident.getId(), "Restart the deployment", 0.8, "rationale");

        mockMvc.perform(post("/incidents/" + incident.getId() + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Too risky during business hours\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mockMvc.perform(get("/incidents/" + incident.getId() + "/decision-log"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[-1].toolName").value("human_rejection"))
                .andExpect(jsonPath("$[-1].reasoning").value(org.hamcrest.Matchers.containsString("Too risky during business hours")));
    }

    @Test
    @WithMockUser(roles = "APPROVER")
    void rejectIncident_withoutReason_returns400() throws Exception {
        Incident incident = incidentRepository.save(
                new Incident("github-actions", "payments-service", Severity.HIGH, "{}"));
        remediationService.proposeRemediation(incident.getId(), "action", 0.8, "rationale");

        mockMvc.perform(post("/incidents/" + incident.getId() + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "APPROVER")
    void approveIncident_withoutPendingProposal_returns409() throws Exception {
        Incident incident = incidentRepository.save(
                new Incident("github-actions", "payments-service", Severity.HIGH, "{}"));

        mockMvc.perform(post("/incidents/" + incident.getId() + "/approve"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithAnonymousUser
    void listIncidents_whenUnauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/incidents"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void approveIncident_asViewer_returns403() throws Exception {
        Incident incident = incidentRepository.save(
                new Incident("github-actions", "payments-service", Severity.HIGH, "{}"));
        remediationService.proposeRemediation(incident.getId(), "action", 0.8, "rationale");

        mockMvc.perform(post("/incidents/" + incident.getId() + "/approve"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void rejectIncident_asViewer_returns403() throws Exception {
        Incident incident = incidentRepository.save(
                new Incident("github-actions", "payments-service", Severity.HIGH, "{}"));
        remediationService.proposeRemediation(incident.getId(), "action", 0.8, "rationale");

        mockMvc.perform(post("/incidents/" + incident.getId() + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"no\"}"))
                .andExpect(status().isForbidden());
    }

    /**
     * Everything above uses @WithMockUser, which pre-populates the
     * SecurityContext before the request - it never actually exercises
     * JwtAuthenticationFilter's own Bearer-header parsing. This test does:
     * a real login for a real token, then a real Authorization header, to
     * prove the whole chain works together, not just each piece in
     * isolation.
     */
    @Test
    @WithAnonymousUser
    void realJwtFromLogin_grantsAccessToProtectedEndpoint_andApproverTokenCanApprove() throws Exception {
        String viewerLoginBody = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"viewer\",\"password\":\"viewer123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String viewerToken = objectMapper.readTree(viewerLoginBody).get("token").asText();

        mockMvc.perform(get("/incidents").header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk());

        Incident incident = incidentRepository.save(
                new Incident("github-actions", "payments-service", Severity.HIGH, "{}"));
        remediationService.proposeRemediation(incident.getId(), "action", 0.8, "rationale");

        mockMvc.perform(post("/incidents/" + incident.getId() + "/approve")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden());

        String approverLoginBody = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"approver\",\"password\":\"approver123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String approverToken = objectMapper.readTree(approverLoginBody).get("token").asText();

        mockMvc.perform(post("/incidents/" + incident.getId() + "/approve")
                        .header("Authorization", "Bearer " + approverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }
}
