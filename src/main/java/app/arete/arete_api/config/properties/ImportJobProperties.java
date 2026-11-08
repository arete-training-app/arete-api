package app.arete.arete_api.config.properties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param pollingEnabled whether this instance polls the queue; turned off in tests, which run the worker by hand
 * @param pollInterval   the wait between the end of one poll and the start of the next, which is also the longest a
 *                       newly queued job waits before it starts
 * @param batchSize      the most jobs claimed at once; a poll keeps claiming batches until nothing is due
 * @param maxAttempts    the attempts after which a job that keeps failing for a temporary reason fails for good
 * @param retryBackoff   the earliest a failed job is retried after its first attempt; it doubles with every further
 *                       attempt, and the retry happens on the first poll after that
 * @param lease          how long a claimed job may stay in progress before a poll takes it over, for runs that
 *                       stopped mid-job
 */
@Validated
@ConfigurationProperties(prefix = "arete.import-job")
public record ImportJobProperties(
        boolean pollingEnabled,
        @NotNull Duration pollInterval,
        @Positive int batchSize,
        @Positive int maxAttempts,
        @NotNull Duration retryBackoff,
        @NotNull Duration lease
) {
}
