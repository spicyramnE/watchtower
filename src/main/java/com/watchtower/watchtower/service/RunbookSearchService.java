package com.watchtower.watchtower.service;

import com.watchtower.watchtower.dto.RunbookMatch;
import com.watchtower.watchtower.entity.Runbook;
import com.watchtower.watchtower.repository.RunbookRepository;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Backs the search_runbook tool with keyword-overlap scoring - a simple,
 * explainable baseline that gets RAG working end-to-end. Phase 4 upgrades
 * this to embedding-based semantic similarity without changing the tool's
 * contract; see README's architecture notes for the reasoning behind
 * building the simple version first.
 */
@Service
public class RunbookSearchService {

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^a-z0-9]+");
    private static final int MAX_RESULTS = 3;
    private static final int EXCERPT_LENGTH = 240;

    private final RunbookRepository runbookRepository;

    public RunbookSearchService(RunbookRepository runbookRepository) {
        this.runbookRepository = runbookRepository;
    }

    public List<RunbookMatch> search(String query) {
        Set<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        return runbookRepository.findAll().stream()
                .map(runbook -> new RunbookMatch(runbook.getId(), runbook.getTitle(), excerpt(runbook), score(runbook, queryTokens)))
                .filter(match -> match.score() > 0)
                .sorted(Comparator.comparingDouble(RunbookMatch::score).reversed())
                .limit(MAX_RESULTS)
                .toList();
    }

    private double score(Runbook runbook, Set<String> queryTokens) {
        Set<String> titleTokens = tokenize(runbook.getTitle());
        Set<String> tagTokens = runbook.getTags().stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        Set<String> contentTokens = tokenize(runbook.getContent());

        double score = 0;
        for (String token : queryTokens) {
            if (titleTokens.contains(token)) {
                score += 3;
            }
            if (tagTokens.contains(token)) {
                score += 2;
            }
            if (contentTokens.contains(token)) {
                score += 1;
            }
        }
        return score;
    }

    private String excerpt(Runbook runbook) {
        String content = runbook.getContent();
        if (content.length() <= EXCERPT_LENGTH) {
            return content;
        }
        return content.substring(0, EXCERPT_LENGTH).strip() + "...";
    }

    private Set<String> tokenize(String text) {
        return Arrays.stream(TOKEN_SPLIT.split(text.toLowerCase(Locale.ROOT)))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toSet());
    }
}
