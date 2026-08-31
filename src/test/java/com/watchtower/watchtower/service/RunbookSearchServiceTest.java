package com.watchtower.watchtower.service;

import com.watchtower.watchtower.dto.RunbookMatch;
import com.watchtower.watchtower.entity.Runbook;
import com.watchtower.watchtower.repository.RunbookRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RunbookSearchServiceTest {

    @Mock
    private RunbookRepository runbookRepository;

    private RunbookSearchService runbookSearchService;

    private Runbook deploymentTimeout;
    private Runbook flakyTest;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        runbookSearchService = new RunbookSearchService(runbookRepository);
        deploymentTimeout = new Runbook(
                "What to do when a deployment times out",
                "A deployment timeout means the platform gave up waiting for the new revision to report healthy.",
                List.of("deployment", "timeout", "ci-cd"));
        flakyTest = new Runbook(
                "Handling flaky test failures in CI",
                "A flaky test passes and fails intermittently without any code changes.",
                List.of("tests", "flaky", "ci"));
    }

    @Test
    void search_withMatchingQuery_ranksMostRelevantRunbookFirst() {
        when(runbookRepository.findAll()).thenReturn(List.of(deploymentTimeout, flakyTest));

        List<RunbookMatch> results = runbookSearchService.search("deployment timeout");

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).title()).isEqualTo("What to do when a deployment times out");
        assertThat(results.get(0).score()).isGreaterThan(0);
    }

    @Test
    void search_withIrrelevantQuery_returnsEmptyRatherThanForcedMatches() {
        when(runbookRepository.findAll()).thenReturn(List.of(deploymentTimeout, flakyTest));

        List<RunbookMatch> results = runbookSearchService.search("quantum flux capacitor banana");

        assertThat(results).isEmpty();
    }

    @Test
    void search_returnsAtMostThreeResults() {
        List<Runbook> manyRunbooks = List.of(
                new Runbook("CI failure one", "ci failure content", List.of("ci")),
                new Runbook("CI failure two", "ci failure content", List.of("ci")),
                new Runbook("CI failure three", "ci failure content", List.of("ci")),
                new Runbook("CI failure four", "ci failure content", List.of("ci")));
        when(runbookRepository.findAll()).thenReturn(manyRunbooks);

        List<RunbookMatch> results = runbookSearchService.search("ci failure");

        assertThat(results).hasSizeLessThanOrEqualTo(3);
    }
}
