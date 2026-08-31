package com.watchtower.watchtower.mcp;

import com.watchtower.watchtower.entity.Incident;
import com.watchtower.watchtower.entity.Severity;
import com.watchtower.watchtower.repository.IncidentRepository;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the tools are exposed through a real MCP server interface (not
 * just callable as plain Java methods): registered against the actual
 * io.modelcontextprotocol Java SDK's tool specifications, with real
 * generated input schemas, invoked through the same callHandler the wire
 * protocol uses. See README's "why MCP" architecture note for why this
 * distinction matters.
 */
@SpringBootTest
class WatchtowerMcpToolsTest {

    private static final List<String> EXPECTED_TOOL_NAMES = List.of(
            "get_recent_logs", "get_pipeline_history", "search_runbook",
            "propose_remediation", "execute_remediation");

    @Autowired
    private List<McpServerFeatures.SyncToolSpecification> toolSpecs;

    @Autowired
    private IncidentRepository incidentRepository;

    @Test
    void allFiveToolsAreRegisteredWithDescriptionsAndInputSchemas() {
        List<String> registeredNames = toolSpecs.stream()
                .map(spec -> spec.tool().name())
                .toList();

        assertThat(registeredNames).containsExactlyInAnyOrderElementsOf(EXPECTED_TOOL_NAMES);

        assertThat(toolSpecs).allSatisfy(spec -> {
            assertThat(spec.tool().description()).isNotBlank();
            assertThat(spec.tool().inputSchema()).isNotEmpty();
        });
    }

    @Test
    void getRecentLogs_calledThroughMcpHandler_returnsWellFormedResult() {
        McpServerFeatures.SyncToolSpecification spec = findTool("get_recent_logs");

        McpSchema.CallToolResult result = spec.callHandler().apply(null,
                new McpSchema.CallToolRequest("get_recent_logs", Map.of("serviceName", "payments-service")));

        assertWellFormed(result);
    }

    @Test
    void searchRunbook_calledThroughMcpHandler_returnsWellFormedResult() {
        McpServerFeatures.SyncToolSpecification spec = findTool("search_runbook");

        McpSchema.CallToolResult result = spec.callHandler().apply(null,
                new McpSchema.CallToolRequest("search_runbook", Map.of("query", "deployment timeout")));

        assertWellFormed(result);
    }

    @Test
    void proposeThenExecuteRemediation_calledThroughMcpHandlers_updatesTheIncident() {
        Incident incident = incidentRepository.save(
                new Incident("github-actions", "checkout-service", Severity.HIGH, "{}"));

        McpServerFeatures.SyncToolSpecification propose = findTool("propose_remediation");
        McpSchema.CallToolResult proposeResult = propose.callHandler().apply(null,
                new McpSchema.CallToolRequest("propose_remediation", Map.of(
                        "incidentId", incident.getId(),
                        "action", "Restart the deployment",
                        "confidence", 0.8,
                        "rationale", "Deployment health check never passed")));
        assertWellFormed(proposeResult);

        McpServerFeatures.SyncToolSpecification execute = findTool("execute_remediation");
        McpSchema.CallToolResult executeResult = execute.callHandler().apply(null,
                new McpSchema.CallToolRequest("execute_remediation", Map.of("incidentId", incident.getId())));
        assertWellFormed(executeResult);

        Incident reloaded = incidentRepository.findById(incident.getId()).orElseThrow();
        assertThat(reloaded.getStatus().name()).isEqualTo("RESOLVED");
    }

    private McpServerFeatures.SyncToolSpecification findTool(String name) {
        return toolSpecs.stream()
                .filter(spec -> spec.tool().name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool not registered: " + name));
    }

    private void assertWellFormed(McpSchema.CallToolResult result) {
        assertThat(result).isNotNull();
        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        boolean hasContent = result.content() != null && !result.content().isEmpty();
        boolean hasStructuredContent = result.structuredContent() != null;
        assertThat(hasContent || hasStructuredContent).isTrue();
    }
}
