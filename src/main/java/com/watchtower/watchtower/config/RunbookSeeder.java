package com.watchtower.watchtower.config;

import com.watchtower.watchtower.embedding.EmbeddingCodec;
import com.watchtower.watchtower.embedding.VoyageEmbeddingClient;
import com.watchtower.watchtower.entity.Runbook;
import com.watchtower.watchtower.repository.RunbookRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Loads the runbook knowledge base (src/main/resources/runbooks/*.md) into the
 * Runbook table on startup, then embeds any runbook that doesn't have a
 * cached embedding yet (fresh rows just seeded, or existing rows from before
 * Phase 4). Both steps are safe to run on every restart: seeding is skipped
 * once the table is populated, and only un-embedded rows are re-embedded.
 * <p>
 * If VOYAGE_API_KEY isn't configured, embedding indexing is skipped with a
 * warning rather than failing startup - search_runbook simply returns no
 * results until it's set.
 */
@Component
public class RunbookSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RunbookSeeder.class);
    private static final String RUNBOOKS_LOCATION = "classpath:runbooks/*.md";

    private final RunbookRepository runbookRepository;
    private final VoyageEmbeddingClient embeddingClient;
    private final EmbeddingCodec embeddingCodec;

    public RunbookSeeder(RunbookRepository runbookRepository,
                          VoyageEmbeddingClient embeddingClient,
                          EmbeddingCodec embeddingCodec) {
        this.runbookRepository = runbookRepository;
        this.embeddingClient = embeddingClient;
        this.embeddingCodec = embeddingCodec;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        seedIfEmpty();
        indexMissingEmbeddings();
    }

    private void seedIfEmpty() throws IOException {
        if (runbookRepository.count() > 0) {
            log.info("Runbook table already populated, skipping seed");
            return;
        }

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(RUNBOOKS_LOCATION);
        List<Runbook> runbooks = new ArrayList<>();

        for (Resource resource : resources) {
            runbooks.add(parse(resource));
        }

        runbookRepository.saveAll(runbooks);
        log.info("Seeded {} runbooks", runbooks.size());
    }

    private void indexMissingEmbeddings() {
        List<Runbook> unembedded = runbookRepository.findAll().stream()
                .filter(runbook -> runbook.getEmbedding() == null)
                .toList();

        if (unembedded.isEmpty()) {
            return;
        }

        if (!embeddingClient.isConfigured()) {
            log.warn("VOYAGE_API_KEY not set; skipping embedding of {} runbook(s). "
                    + "search_runbook will return no results until it's configured.", unembedded.size());
            return;
        }

        try {
            List<String> contents = unembedded.stream().map(Runbook::getContent).toList();
            List<double[]> embeddings = embeddingClient.embed(contents, VoyageEmbeddingClient.INPUT_TYPE_DOCUMENT);

            for (int i = 0; i < unembedded.size(); i++) {
                unembedded.get(i).setEmbedding(embeddingCodec.encode(embeddings.get(i)));
            }
            runbookRepository.saveAll(unembedded);
            log.info("Embedded {} runbook(s)", unembedded.size());
        } catch (Exception e) {
            log.warn("Failed to embed runbooks via Voyage API; search_runbook will return no results "
                    + "until this succeeds: {}", e.getMessage());
        }
    }

    private Runbook parse(Resource resource) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            String firstLine = reader.readLine();
            if (firstLine == null || !firstLine.strip().equals("---")) {
                throw new IllegalStateException("Runbook " + resource.getFilename() + " is missing frontmatter");
            }

            String title = null;
            List<String> tags = List.of();
            String line;
            while ((line = reader.readLine()) != null && !line.strip().equals("---")) {
                if (line.startsWith("title:")) {
                    title = line.substring("title:".length()).strip();
                } else if (line.startsWith("tags:")) {
                    tags = Arrays.stream(line.substring("tags:".length()).split(","))
                            .map(String::strip)
                            .filter(tag -> !tag.isEmpty())
                            .toList();
                }
            }

            StringBuilder content = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                content.append(line).append('\n');
            }

            if (title == null) {
                throw new IllegalStateException("Runbook " + resource.getFilename() + " is missing a title");
            }

            return new Runbook(title, content.toString().strip(), new ArrayList<>(tags));
        }
    }
}
