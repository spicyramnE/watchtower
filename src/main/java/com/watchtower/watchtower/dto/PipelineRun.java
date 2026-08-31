package com.watchtower.watchtower.dto;

import java.time.Instant;

public record PipelineRun(String revision, String status, Instant timestamp, int durationSeconds) {
}
