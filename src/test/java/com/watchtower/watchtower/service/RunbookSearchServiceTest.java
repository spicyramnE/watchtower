package com.watchtower.watchtower.service;

import com.watchtower.watchtower.dto.RunbookMatch;
import com.watchtower.watchtower.embedding.EmbeddingCodec;
import com.watchtower.watchtower.embedding.VoyageEmbeddingClient;
import com.watchtower.watchtower.entity.Runbook;
import com.watchtower.watchtower.repository.RunbookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RunbookSearchServiceTest {

    @Mock
    private RunbookRepository runbookRepository;

    @Mock
    private VoyageEmbeddingClient embeddingClient;

    @Mock
    private EmbeddingCodec embeddingCodec;

    private RunbookSearchService runbookSearchService;

    private Runbook deploymentTimeout;
    private Runbook flakyTest;

    @BeforeEach
    void setUp() {
        runbookSearchService = new RunbookSearchService(runbookRepository, embeddingClient, embeddingCodec);

        deploymentTimeout = new Runbook("What to do when a deployment times out", "content", List.of("deployment"));
        deploymentTimeout.setEmbedding("vec:deployment-timeout");

        flakyTest = new Runbook("Handling flaky test failures in CI", "content", List.of("tests"));
        flakyTest.setEmbedding("vec:flaky-test");
    }

    @Test
    void search_withMatchingQuery_ranksMostRelevantRunbookFirst() {
        when(embeddingCodec.decode("vec:deployment-timeout")).thenReturn(new double[]{1, 0, 0});
        when(embeddingCodec.decode("vec:flaky-test")).thenReturn(new double[]{0, 1, 0});
        when(embeddingClient.isConfigured()).thenReturn(true);
        when(runbookRepository.findAll()).thenReturn(List.of(deploymentTimeout, flakyTest));
        when(embeddingClient.embed(List.of("deployment timed out"), VoyageEmbeddingClient.INPUT_TYPE_QUERY))
                .thenReturn(List.of(new double[]{1, 0, 0}));

        List<RunbookMatch> results = runbookSearchService.search("deployment timed out");

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).title()).isEqualTo("What to do when a deployment times out");
        assertThat(results.get(0).score()).isEqualTo(1.0);
    }

    @Test
    void search_withIrrelevantQuery_returnsEmptyRatherThanForcedMatches() {
        when(embeddingCodec.decode("vec:deployment-timeout")).thenReturn(new double[]{1, 0, 0});
        when(embeddingCodec.decode("vec:flaky-test")).thenReturn(new double[]{0, 1, 0});
        when(embeddingClient.isConfigured()).thenReturn(true);
        when(runbookRepository.findAll()).thenReturn(List.of(deploymentTimeout, flakyTest));
        when(embeddingClient.embed(List.of("banana"), VoyageEmbeddingClient.INPUT_TYPE_QUERY))
                .thenReturn(List.of(new double[]{0, 0, 1}));

        List<RunbookMatch> results = runbookSearchService.search("banana");

        assertThat(results).isEmpty();
    }

    @Test
    void search_returnsAtMostThreeResults() {
        List<Runbook> manyRunbooks = List.of(
                embedded("CI failure one", "vec:1"),
                embedded("CI failure two", "vec:2"),
                embedded("CI failure three", "vec:3"),
                embedded("CI failure four", "vec:4"));
        when(embeddingCodec.decode(anyString())).thenReturn(new double[]{1, 0, 0});
        when(embeddingClient.isConfigured()).thenReturn(true);
        when(runbookRepository.findAll()).thenReturn(manyRunbooks);
        when(embeddingClient.embed(List.of("ci failure"), VoyageEmbeddingClient.INPUT_TYPE_QUERY))
                .thenReturn(List.of(new double[]{1, 0, 0}));

        List<RunbookMatch> results = runbookSearchService.search("ci failure");

        assertThat(results).hasSize(3);
    }

    @Test
    void search_whenVoyageNotConfigured_returnsEmpty() {
        when(embeddingClient.isConfigured()).thenReturn(false);

        List<RunbookMatch> results = runbookSearchService.search("deployment timed out");

        assertThat(results).isEmpty();
    }

    @Test
    void search_whenNoRunbooksAreEmbeddedYet_returnsEmpty() {
        Runbook unembedded = new Runbook("Not yet embedded", "content", List.of());
        when(embeddingClient.isConfigured()).thenReturn(true);
        when(runbookRepository.findAll()).thenReturn(List.of(unembedded));

        List<RunbookMatch> results = runbookSearchService.search("anything");

        assertThat(results).isEmpty();
    }

    private Runbook embedded(String title, String embeddingKey) {
        Runbook runbook = new Runbook(title, "content", List.of("ci"));
        runbook.setEmbedding(embeddingKey);
        return runbook;
    }
}
