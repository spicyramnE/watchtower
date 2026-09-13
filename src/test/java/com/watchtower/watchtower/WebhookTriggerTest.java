package com.watchtower.watchtower;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deliberately failing test - exists only to make this PR's CI run fail on
 * purpose, to prove the real GitHub webhook ingestion path end to end.
 * This branch is never meant to merge; delete it after the test.
 */
class WebhookTriggerTest {

    @Test
    void deliberatelyFails_toTriggerARealWorkflowRunFailureWebhook() {
        assertThat(false).isTrue();
    }
}
