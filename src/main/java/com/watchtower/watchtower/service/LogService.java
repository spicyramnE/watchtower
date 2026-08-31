package com.watchtower.watchtower.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Backs the get_recent_logs tool. Log lines are synthetic (no real log
 * aggregator is wired up in this project) but shaped like genuine service
 * logs so the agent has plausible evidence to reason over.
 */
@Service
public class LogService {

    private static final int LINE_COUNT = 8;

    private static final List<Function<String, String>> LINE_GENERATORS = List.of(
            service -> "INFO  Starting request handler for " + service,
            service -> "INFO  Health check passed for " + service,
            service -> "WARN  Slow response from downstream dependency (" + randomInt(50, 900) + "ms) in " + service,
            service -> "ERROR Connection reset by peer while calling " + service,
            service -> "INFO  Processed request in " + randomInt(5, 300) + "ms for " + service,
            service -> "WARN  Retrying failed call to " + service + " (attempt " + randomInt(1, 3) + ")",
            service -> "ERROR NullPointerException in " + service + ".handleRequest()",
            service -> "INFO  Scaled " + service + " to " + randomInt(2, 6) + " replicas",
            service -> "DEBUG Cache miss for key incident:" + service + ":" + randomInt(1000, 9999),
            service -> "INFO  Garbage collection completed in " + randomInt(10, 120) + "ms for " + service
    );

    public List<String> getRecentLogs(String serviceName) {
        Instant now = Instant.now();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        return IntStream.range(0, LINE_COUNT)
                .mapToObj(i -> {
                    Function<String, String> generator = LINE_GENERATORS.get(random.nextInt(LINE_GENERATORS.size()));
                    Instant timestamp = now.minusSeconds((long) (LINE_COUNT - i) * randomInt(5, 45));
                    return DateTimeFormatter.ISO_INSTANT.format(timestamp) + " [" + serviceName + "] " + generator.apply(serviceName);
                })
                .collect(Collectors.toList());
    }

    private static int randomInt(int minInclusive, int maxInclusive) {
        return ThreadLocalRandom.current().nextInt(minInclusive, maxInclusive + 1);
    }
}
