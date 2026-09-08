package com.watchtower.watchtower.service;

import com.watchtower.watchtower.dto.RunbookMatch;
import com.watchtower.watchtower.embedding.EmbeddingCodec;
import com.watchtower.watchtower.embedding.VectorMath;
import com.watchtower.watchtower.embedding.VoyageEmbeddingClient;
import com.watchtower.watchtower.entity.Runbook;
import com.watchtower.watchtower.repository.RunbookRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Backs the search_runbook tool with real semantic search: Voyage AI
 * embeddings + cosine similarity, computed in-memory rather than via a
 * vector database extension (pgvector) - the "simple version first" path
 * from the project's build philosophy, since 12 runbooks don't need a
 * dedicated vector index. See README architecture notes for the full
 * reasoning and the pgvector trade-off if this needs to scale later.
 * <p>
 * Phase 3's keyword-overlap scoring is superseded by this; it could only
 * ever match queries that shared literal words with a runbook.
 */
@Service
public class RunbookSearchService {

    private static final Logger log = LoggerFactory.getLogger(RunbookSearchService.class);

    private static final int MAX_RESULTS = 3;
    private static final int EXCERPT_LENGTH = 240;

    /**
     * Empirically-tunable cutoff below which a match is considered noise
     * rather than a genuine hit, so an unrelated query returns nothing
     * instead of the "least bad" runbook.
     */
    private static final double MIN_SIMILARITY = 0.25;

    private final RunbookRepository runbookRepository;
    private final VoyageEmbeddingClient embeddingClient;
    private final EmbeddingCodec embeddingCodec;

    public RunbookSearchService(RunbookRepository runbookRepository,
                                 VoyageEmbeddingClient embeddingClient,
                                 EmbeddingCodec embeddingCodec) {
        this.runbookRepository = runbookRepository;
        this.embeddingClient = embeddingClient;
        this.embeddingCodec = embeddingCodec;
    }

    public List<RunbookMatch> search(String query) {
        if (query == null || query.isBlank() || !embeddingClient.isConfigured()) {
            return List.of();
        }

        List<Runbook> candidates = runbookRepository.findAll().stream()
                .filter(runbook -> runbook.getEmbedding() != null)
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }

        double[] queryVector;
        try {
            queryVector = embeddingClient.embed(List.of(query), VoyageEmbeddingClient.INPUT_TYPE_QUERY).get(0);
        } catch (Exception e) {
            log.warn("Failed to embed search_runbook query via Voyage API: {}", e.getMessage());
            return List.of();
        }

        return candidates.stream()
                .map(runbook -> new RunbookMatch(
                        runbook.getId(),
                        runbook.getTitle(),
                        excerpt(runbook),
                        VectorMath.cosineSimilarity(queryVector, embeddingCodec.decode(runbook.getEmbedding()))))
                .filter(match -> match.score() >= MIN_SIMILARITY)
                .sorted(Comparator.comparingDouble(RunbookMatch::score).reversed())
                .limit(MAX_RESULTS)
                .toList();
    }

    private String excerpt(Runbook runbook) {
        String content = runbook.getContent();
        if (content.length() <= EXCERPT_LENGTH) {
            return content;
        }
        return content.substring(0, EXCERPT_LENGTH).strip() + "...";
    }
}
