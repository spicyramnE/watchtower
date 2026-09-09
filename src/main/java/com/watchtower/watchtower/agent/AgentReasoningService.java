package com.watchtower.watchtower.agent;

import com.watchtower.watchtower.dto.DiagnosisResult;
import com.watchtower.watchtower.entity.AgentDecisionLog;
import com.watchtower.watchtower.entity.Incident;
import com.watchtower.watchtower.entity.IncidentStatus;
import com.watchtower.watchtower.exception.IncidentNotFoundException;
import com.watchtower.watchtower.repository.AgentDecisionLogRepository;
import com.watchtower.watchtower.repository.IncidentRepository;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The ReAct loop: hands an incident to the model, lets it call tools to
 * gather evidence, and expects it to conclude by calling propose_remediation
 * with its diagnosis. Every step is written to AgentDecisionLog - see
 * README architecture notes for the full reasoning behind this design,
 * including why execute_remediation is deliberately never offered to the
 * model (human-in-the-loop is enforced here, not just at the API layer).
 */
@Service
public class AgentReasoningService {

    private static final Logger log = LoggerFactory.getLogger(AgentReasoningService.class);

    private static final int MAX_ITERATIONS = 5;

    /**
     * execute_remediation is intentionally excluded: the agent may propose a
     * fix, never carry it out. Phase 6 gates execution behind human
     * approval; letting the model call it directly would bypass that
     * entirely.
     */
    private static final Set<String> AGENT_TOOL_NAMES = Set.of(
            "get_recent_logs", "get_pipeline_history", "search_runbook", "propose_remediation");

    private static final String SYSTEM_PROMPT = """
            You are Watchtower, an SRE agent that diagnoses CI/CD pipeline incidents.

            You have tools to gather evidence: get_recent_logs, get_pipeline_history, and
            search_runbook. Use them to understand what happened before concluding anything -
            do not guess without evidence.

            Once you have enough evidence, conclude by calling propose_remediation exactly
            once with: the specific action to take, a confidence score between 0.0 and 1.0,
            and a rationale that cites the evidence you gathered. Do not call
            propose_remediation until you have actually gathered evidence with at least one
            other tool first.

            You cannot execute any remediation yourself - a human must approve it first.
            Work efficiently: you have a limited number of tool calls available.""";

    private final GroqClient groqClient;
    // Resolved lazily (not injected directly) - this bean is only fully
    // populated once every @McpTool-annotated component has been scanned,
    // which isn't guaranteed yet at AgentReasoningService's own construction
    // time. Injecting the List directly here previously caused it to be
    // resolved (and cached empty) too early, breaking MCP tool registration
    // for the whole application context.
    //
    // @Qualifier is required: Spring AI registers two distinct
    // List<SyncToolSpecification> beans - "toolSpecs" (our @McpTool-scanned
    // tools) and "syncTools" (its own ToolCallback conversion path, unused
    // here). Regular field/constructor injection would silently pick
    // "toolSpecs" only because a parameter happened to share that name;
    // ObjectProvider doesn't apply that same implicit tiebreaker, so it must
    // be named explicitly.
    private final ObjectProvider<List<McpServerFeatures.SyncToolSpecification>> toolSpecsProvider;
    private final IncidentRepository incidentRepository;
    private final AgentDecisionLogRepository decisionLogRepository;
    private final ObjectMapper objectMapper;

    public AgentReasoningService(GroqClient groqClient,
                                  @Qualifier("toolSpecs") ObjectProvider<List<McpServerFeatures.SyncToolSpecification>> toolSpecsProvider,
                                  IncidentRepository incidentRepository,
                                  AgentDecisionLogRepository decisionLogRepository,
                                  ObjectMapper objectMapper) {
        this.groqClient = groqClient;
        this.toolSpecsProvider = toolSpecsProvider;
        this.incidentRepository = incidentRepository;
        this.decisionLogRepository = decisionLogRepository;
        this.objectMapper = objectMapper;
    }

    private List<McpServerFeatures.SyncToolSpecification> toolSpecs() {
        return toolSpecsProvider.getObject();
    }

    public DiagnosisResult diagnose(Long incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IncidentNotFoundException(incidentId));

        if (!groqClient.isConfigured()) {
            return new DiagnosisResult(incident.getId(), incident.getStatus().name(), 0, false,
                    "GROQ_API_KEY is not configured; the agent cannot run.");
        }

        incident.setStatus(IncidentStatus.DIAGNOSING);
        incidentRepository.save(incident);

        List<GroqClient.GroqTool> tools = buildToolDefinitions();
        List<GroqClient.ChatMessage> messages = new ArrayList<>();
        messages.add(GroqClient.ChatMessage.system(SYSTEM_PROMPT));
        messages.add(GroqClient.ChatMessage.user(buildIncidentSummary(incident)));

        for (int step = 1; step <= MAX_ITERATIONS; step++) {
            GroqClient.ChatCompletionResponse response = groqClient.chat(messages, tools);
            GroqClient.Message message = response.choices().get(0).message();

            if (message.toolCalls() == null || message.toolCalls().isEmpty()) {
                logStep(incident, step, null, null, null, message.content());
                return new DiagnosisResult(incident.getId(), incident.getStatus().name(), step, false,
                        "Agent concluded without proposing a remediation: " + message.content());
            }

            messages.add(GroqClient.ChatMessage.assistant(message.content(), message.toolCalls()));

            for (GroqClient.ToolCall call : message.toolCalls()) {
                String toolName = call.function().name();
                String argumentsJson = call.function().arguments();

                if (!AGENT_TOOL_NAMES.contains(toolName)) {
                    String errorText = "Tool not permitted for autonomous use: " + toolName;
                    log.warn("Agent attempted to call disallowed tool {} for incident {}", toolName, incidentId);
                    logStep(incident, step, toolName, argumentsJson, errorText, null);
                    messages.add(GroqClient.ChatMessage.toolResult(call.id(), toolName, errorText));
                    continue;
                }

                String resultText = invokeTool(toolName, argumentsJson);
                logStep(incident, step, toolName, argumentsJson, resultText, null);
                messages.add(GroqClient.ChatMessage.toolResult(call.id(), toolName, resultText));

                if (toolName.equals("propose_remediation")) {
                    Incident reloaded = incidentRepository.findById(incidentId).orElseThrow();
                    return new DiagnosisResult(reloaded.getId(), reloaded.getStatus().name(), step, true, resultText);
                }
            }
        }

        String capMessage = "Iteration cap (" + MAX_ITERATIONS + ") reached without a conclusive diagnosis.";
        logStep(incident, MAX_ITERATIONS + 1, null, null, null, capMessage);
        return new DiagnosisResult(incident.getId(), incident.getStatus().name(), MAX_ITERATIONS, false, capMessage);
    }

    private String invokeTool(String toolName, String argumentsJson) {
        try {
            Map<String, Object> arguments = objectMapper.readValue(argumentsJson, Map.class);
            McpServerFeatures.SyncToolSpecification spec = findTool(toolName);
            McpSchema.CallToolResult result = spec.callHandler()
                    .apply(null, new McpSchema.CallToolRequest(toolName, arguments));
            return extractText(result);
        } catch (Exception e) {
            log.warn("Tool call to {} failed: {}", toolName, e.getMessage());
            return "Tool call failed: " + e.getMessage();
        }
    }

    private String extractText(McpSchema.CallToolResult result) {
        if (result.content() == null || result.content().isEmpty()) {
            return result.structuredContent() != null ? result.structuredContent().toString() : "";
        }
        StringBuilder text = new StringBuilder();
        for (McpSchema.Content content : result.content()) {
            if (content instanceof McpSchema.TextContent textContent) {
                text.append(textContent.text());
            }
        }
        return text.toString();
    }

    private McpServerFeatures.SyncToolSpecification findTool(String name) {
        return toolSpecs().stream()
                .filter(spec -> spec.tool().name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No MCP tool registered with name: " + name));
    }

    private List<GroqClient.GroqTool> buildToolDefinitions() {
        return toolSpecs().stream()
                .filter(spec -> AGENT_TOOL_NAMES.contains(spec.tool().name()))
                .map(spec -> GroqClient.GroqTool.function(
                        spec.tool().name(), spec.tool().description(), spec.tool().inputSchema()))
                .toList();
    }

    private String buildIncidentSummary(Incident incident) {
        return """
                Diagnose this incident:
                Incident ID: %d
                Source: %s
                Service: %s
                Severity: %s
                Raw payload: %s""".formatted(
                incident.getId(), incident.getSource(), incident.getServiceName(),
                incident.getSeverity(), incident.getRawPayload());
    }

    private void logStep(Incident incident, int stepNumber, String toolName, String toolInput,
                          String toolOutput, String reasoning) {
        decisionLogRepository.save(new AgentDecisionLog(incident, stepNumber, toolName, toolInput, toolOutput, reasoning));
    }
}
