package com.watchtower.watchtower.mcp;

import com.watchtower.watchtower.dto.PipelineRun;
import com.watchtower.watchtower.dto.RemediationExecutionResult;
import com.watchtower.watchtower.dto.RemediationProposal;
import com.watchtower.watchtower.dto.RunbookMatch;
import com.watchtower.watchtower.service.LogService;
import com.watchtower.watchtower.service.PipelineHistoryService;
import com.watchtower.watchtower.service.RemediationService;
import com.watchtower.watchtower.service.RunbookSearchService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The agent's tool-calling surface, exposed as real MCP tools rather than
 * plain internal methods dressed up as "agent tools" - this is the
 * project's key differentiator (see README architecture notes). Each method
 * here is a thin wrapper: all the actual logic lives in the plain,
 * independently unit-tested services in the service/ package.
 */
@Component
public class WatchtowerMcpTools {

    private final LogService logService;
    private final PipelineHistoryService pipelineHistoryService;
    private final RunbookSearchService runbookSearchService;
    private final RemediationService remediationService;

    public WatchtowerMcpTools(LogService logService,
                               PipelineHistoryService pipelineHistoryService,
                               RunbookSearchService runbookSearchService,
                               RemediationService remediationService) {
        this.logService = logService;
        this.pipelineHistoryService = pipelineHistoryService;
        this.runbookSearchService = runbookSearchService;
        this.remediationService = remediationService;
    }

    @McpTool(name = "get_recent_logs", description = "Fetch recent log lines for a service, most recent last.")
    public List<String> getRecentLogs(
            @McpToolParam(description = "Name of the service to fetch logs for", required = true) String serviceName) {
        return logService.getRecentLogs(serviceName);
    }

    @McpTool(name = "get_pipeline_history", description = "Fetch recent build/deploy outcomes for a service's pipeline.")
    public List<PipelineRun> getPipelineHistory(
            @McpToolParam(description = "Name of the service/pipeline", required = true) String serviceName) {
        return pipelineHistoryService.getPipelineHistory(serviceName);
    }

    @McpTool(name = "search_runbook", description = "Search the runbook knowledge base for excerpts relevant to a free-text query.")
    public List<RunbookMatch> searchRunbook(
            @McpToolParam(description = "Free-text query describing the incident or symptom", required = true) String query) {
        return runbookSearchService.search(query);
    }

    @McpTool(name = "propose_remediation", description = "Propose a remediation action for an incident, moving it to AWAITING_APPROVAL. Does not execute anything.")
    public RemediationProposal proposeRemediation(
            @McpToolParam(description = "ID of the incident this proposal is for", required = true) Long incidentId,
            @McpToolParam(description = "The proposed remediation action", required = true) String action,
            @McpToolParam(description = "Confidence in this proposal, between 0.0 and 1.0", required = true) double confidence,
            @McpToolParam(description = "Rationale explaining why this action is recommended", required = true) String rationale) {
        return remediationService.proposeRemediation(incidentId, action, confidence, rationale);
    }

    @McpTool(name = "execute_remediation", description = "Execute the approved remediation for an incident that is AWAITING_APPROVAL. Execution is simulated.")
    public RemediationExecutionResult executeRemediation(
            @McpToolParam(description = "ID of the incident whose approved remediation should be executed", required = true) Long incidentId) {
        return remediationService.executeRemediation(incidentId);
    }
}
