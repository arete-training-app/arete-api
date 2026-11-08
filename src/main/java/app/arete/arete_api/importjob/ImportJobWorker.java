package app.arete.arete_api.importjob;

import app.arete.arete_api.service.ImportJobService;
import app.arete.arete_api.util.enums.ImportJobType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Processes the import queue. Polled every {@code arete.import-job.poll-interval} by {@code ImportJobSchedulingConfig},
 * and called directly in tests. Not transactional: claiming, processing and recording a failure each run in their own
 * transaction, so a failed job never rolls back the claim of the others.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImportJobWorker {

    // Only one run at a time per instance, also when tests call it while polling is on; other instances are kept
    // apart by the claim's SKIP LOCKED
    private final AtomicBoolean running = new AtomicBoolean();

    private final ImportJobService importJobService;

    /**
     * Claims and runs batches of due jobs of the given types until none is due. Jobs that fail for a temporary reason
     * are due again only after their backoff, so they aren't retried within the same run. Does nothing if a run is
     * already in progress on this instance: that run keeps claiming until nothing is due, so it also takes the jobs
     * this one would have.
     *
     * @param types the job types to process
     * @return how many jobs were claimed, or 0 if another run was in progress
     */
    public int runDueJobs(final Set<ImportJobType> types) {
        if (!running.compareAndSet(false, true)) {
            log.info("An import run is already in progress; skipping this one");
            return 0;
        }
        try {
            int claimedJobs = 0;
            List<Long> jobIds = importJobService.claimDueJobs(types);
            while (!jobIds.isEmpty()) {
                jobIds.forEach(this::processClaimedJob);
                claimedJobs += jobIds.size();
                jobIds = importJobService.claimDueJobs(types);
            }
            // Polling runs every few seconds, so only runs that did something are logged
            if (claimedJobs > 0) {
                log.debug("Import run finished: {} job(s) processed", claimedJobs);
            }
            return claimedJobs;
        } finally {
            running.set(false);
        }
    }

    private void processClaimedJob(final Long jobId) {
        try {
            importJobService.processJob(jobId);
        } catch (final RuntimeException exception) {
            log.error("Import job {} failed: {}", jobId, exception.getMessage());
            recordFailure(jobId, exception);
        }
    }

    private void recordFailure(final Long jobId, final RuntimeException exception) {
        try {
            importJobService.recordFailure(jobId, exception);
        } catch (final RuntimeException recordException) {
            log.error("Couldn't record the failure of import job {}; it is retried once its lease expires",
                    jobId, recordException);
        }
    }
}
