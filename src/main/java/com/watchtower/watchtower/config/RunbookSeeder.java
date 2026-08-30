package com.watchtower.watchtower.config;

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
 * Runbook table on startup. Runs once - if the table already has rows, it's a
 * no-op, so restarting the app doesn't create duplicates.
 */
@Component
public class RunbookSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RunbookSeeder.class);
    private static final String RUNBOOKS_LOCATION = "classpath:runbooks/*.md";

    private final RunbookRepository runbookRepository;

    public RunbookSeeder(RunbookRepository runbookRepository) {
        this.runbookRepository = runbookRepository;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
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
