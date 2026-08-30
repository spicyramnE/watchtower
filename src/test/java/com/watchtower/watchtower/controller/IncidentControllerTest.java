package com.watchtower.watchtower.controller;

import tools.jackson.databind.ObjectMapper;
import com.watchtower.watchtower.dto.CreateIncidentRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
class IncidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
}
