package com.watchtower.watchtower.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LogServiceTest {

    private final LogService logService = new LogService();

    @Test
    void getRecentLogs_returnsEightLinesMentioningTheService() {
        List<String> logs = logService.getRecentLogs("payments-service");

        assertThat(logs).hasSize(8);
        assertThat(logs).allSatisfy(line -> {
            assertThat(line).contains("[payments-service]");
            assertThat(line).isNotBlank();
        });
    }

    @Test
    void getRecentLogs_calledTwice_producesVariedOutput() {
        List<String> first = logService.getRecentLogs("checkout-service");
        List<String> second = logService.getRecentLogs("checkout-service");

        assertThat(first).isNotEqualTo(second);
    }
}
