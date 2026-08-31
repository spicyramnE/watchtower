package com.watchtower.watchtower.service;

import com.watchtower.watchtower.dto.PipelineRun;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineHistoryServiceTest {

    private final PipelineHistoryService pipelineHistoryService = new PipelineHistoryService();

    @Test
    void getPipelineHistory_returnsFiveWellFormedRunsInDescendingTimeOrder() {
        List<PipelineRun> runs = pipelineHistoryService.getPipelineHistory("payments-service");

        assertThat(runs).hasSize(5);
        assertThat(runs).allSatisfy(run -> {
            assertThat(run.revision()).isNotBlank();
            assertThat(Set.of("SUCCESS", "FAILURE")).contains(run.status());
            assertThat(run.durationSeconds()).isBetween(60, 600);
            assertThat(run.timestamp()).isNotNull();
        });

        for (int i = 1; i < runs.size(); i++) {
            assertThat(runs.get(i).timestamp()).isBefore(runs.get(i - 1).timestamp());
        }
    }
}
