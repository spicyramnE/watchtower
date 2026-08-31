package com.watchtower.watchtower.service;

import com.watchtower.watchtower.dto.PipelineRun;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Backs the get_pipeline_history tool. Outcomes are synthetic - no real
 * CI/CD system is wired up in this project - but weighted to occasionally
 * show a run of recent failures, so the agent has a pattern worth reasoning
 * about (Runbook 06: "Triaging repeated pipeline failures for a single
 * service").
 */
@Service
public class PipelineHistoryService {

    private static final int RUN_COUNT = 5;

    public List<PipelineRun> getPipelineHistory(String serviceName) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean troubled = random.nextInt(4) == 0;
        Instant timestamp = Instant.now();
        List<PipelineRun> runs = new ArrayList<>();

        for (int i = 0; i < RUN_COUNT; i++) {
            timestamp = timestamp.minus(random.nextInt(2, 24), ChronoUnit.HOURS);
            boolean failed = troubled ? i < 3 : random.nextInt(5) == 0;
            String revision = "v1.14." + (RUN_COUNT - i);
            String status = failed ? "FAILURE" : "SUCCESS";
            int durationSeconds = random.nextInt(60, 601);
            runs.add(new PipelineRun(revision, status, timestamp, durationSeconds));
        }

        return runs;
    }
}
