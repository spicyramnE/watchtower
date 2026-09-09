package com.watchtower.watchtower.agent;

import com.watchtower.watchtower.dto.DiagnosisResult;
import com.watchtower.watchtower.entity.AgentDecisionLog;
import com.watchtower.watchtower.entity.Incident;
import com.watchtower.watchtower.entity.IncidentStatus;
import com.watchtower.watchtower.entity.Severity;
import com.watchtower.watchtower.repository.AgentDecisionLogRepository;
import com.watchtower.watchtower.repository.IncidentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Exercises the real ReAct loop mechanics (tool dispatch through the actual
 * registered MCP tools, decision logging, iteration capping, the
 * execute_remediation safety block) against real Postgres, with only
 * GroqClient mocked so the suite stays deterministic and free.
 */
@SpringBootTest
class AgentReasoningServiceTest {

    @Autowired
    private AgentReasoningService agentReasoningService;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private AgentDecisionLogRepository decisionLogRepository;

    @MockitoBean
    private GroqClient groqClient;

    @Test
    void diagnose_whenModelGathersEvidenceThenProposes_concludesAndAwaitsApproval() {
        when(groqClient.isConfigured()).thenReturn(true);

        Incident incident = incidentRepository.save(
                new Incident("github-actions", "payments-service", Severity.HIGH, "{\"error\":\"OOMKilled\"}"));

        // propose_remediation's arguments need the real incident id, so this
        // is stubbed after the incident is persisted
        when(groqClient.chat(any(), any())).thenReturn(
                toolCallResponse("get_recent_logs", "{\"serviceName\":\"payments-service\"}"),
                toolCallResponse("propose_remediation", """
                        {"incidentId":%d,"action":"Restart the deployment","confidence":0.85,"rationale":"OOMKilled in recent logs"}""".formatted(incident.getId())));

        DiagnosisResult result = agentReasoningService.diagnose(incident.getId());

        assertThat(result.concluded()).isTrue();
        assertThat(result.incidentStatus()).isEqualTo("AWAITING_APPROVAL");
        assertThat(result.iterationsUsed()).isEqualTo(2);

        Incident reloaded = incidentRepository.findById(incident.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(IncidentStatus.AWAITING_APPROVAL);
        assertThat(reloaded.getProposedAction()).isEqualTo("Restart the deployment");

        List<AgentDecisionLog> logs = decisionLogRepository.findByIncidentIdOrderByStepNumberAsc(incident.getId());
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).getToolName()).isEqualTo("get_recent_logs");
        assertThat(logs.get(1).getToolName()).isEqualTo("propose_remediation");
    }

    @Test
    void diagnose_whenModelNeverConcludes_stopsAtIterationCapWithoutSilentFailure() {
        when(groqClient.isConfigured()).thenReturn(true);
        when(groqClient.chat(any(), any())).thenReturn(
                toolCallResponse("get_recent_logs", "{\"serviceName\":\"payments-service\"}"));

        Incident incident = incidentRepository.save(
                new Incident("github-actions", "payments-service", Severity.HIGH, "{}"));

        DiagnosisResult result = agentReasoningService.diagnose(incident.getId());

        assertThat(result.concluded()).isFalse();
        assertThat(result.iterationsUsed()).isEqualTo(5);
        assertThat(result.summary()).containsIgnoringCase("iteration cap");

        List<AgentDecisionLog> logs = decisionLogRepository.findByIncidentIdOrderByStepNumberAsc(incident.getId());
        assertThat(logs).hasSize(6); // 5 tool-call steps + 1 cap-reached entry
        assertThat(logs.get(5).getReasoning()).containsIgnoringCase("iteration cap");
    }

    @Test
    void diagnose_whenModelConcludesWithPlainText_returnsInconclusiveResult() {
        when(groqClient.isConfigured()).thenReturn(true);
        when(groqClient.chat(any(), any())).thenReturn(
                textResponse("I don't have enough information to diagnose this."));

        Incident incident = incidentRepository.save(
                new Incident("github-actions", "payments-service", Severity.LOW, "{}"));

        DiagnosisResult result = agentReasoningService.diagnose(incident.getId());

        assertThat(result.concluded()).isFalse();
        assertThat(result.summary()).contains("I don't have enough information");
    }

    @Test
    void diagnose_whenModelAttemptsExecuteRemediation_blocksItAndContinues() {
        when(groqClient.isConfigured()).thenReturn(true);
        when(groqClient.chat(any(), any())).thenReturn(
                toolCallResponse("execute_remediation", "{\"incidentId\":1}"));

        Incident incident = incidentRepository.save(
                new Incident("github-actions", "payments-service", Severity.HIGH, "{}"));

        agentReasoningService.diagnose(incident.getId());

        Incident reloaded = incidentRepository.findById(incident.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isNotEqualTo(IncidentStatus.RESOLVED);

        List<AgentDecisionLog> logs = decisionLogRepository.findByIncidentIdOrderByStepNumberAsc(incident.getId());
        assertThat(logs.get(0).getToolOutput()).containsIgnoringCase("not permitted");
    }

    @Test
    void diagnose_whenGroqNotConfigured_returnsImmediatelyWithoutChangingIncident() {
        when(groqClient.isConfigured()).thenReturn(false);

        Incident incident = incidentRepository.save(
                new Incident("github-actions", "payments-service", Severity.HIGH, "{}"));

        DiagnosisResult result = agentReasoningService.diagnose(incident.getId());

        assertThat(result.concluded()).isFalse();
        assertThat(result.incidentStatus()).isEqualTo("NEW");

        Incident reloaded = incidentRepository.findById(incident.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(IncidentStatus.NEW);
    }

    private GroqClient.ChatCompletionResponse toolCallResponse(String toolName, String argumentsJson) {
        GroqClient.ToolCall call = new GroqClient.ToolCall(
                "call_1", "function", new GroqClient.FunctionCall(toolName, argumentsJson));
        GroqClient.Message message = new GroqClient.Message(null, null, List.of(call));
        return new GroqClient.ChatCompletionResponse(List.of(new GroqClient.Choice(message, "tool_calls")));
    }

    private GroqClient.ChatCompletionResponse textResponse(String content) {
        GroqClient.Message message = new GroqClient.Message("assistant", content, null);
        return new GroqClient.ChatCompletionResponse(List.of(new GroqClient.Choice(message, "stop")));
    }
}
