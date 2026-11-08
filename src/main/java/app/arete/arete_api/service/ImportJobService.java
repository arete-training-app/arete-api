package app.arete.arete_api.service;

import app.arete.arete_api.exception.ConflictException;
import app.arete.arete_api.exception.NotFoundException;
import app.arete.arete_api.model.entity.ImportJob;
import app.arete.arete_api.util.enums.ImportJobType;

import java.util.List;
import java.util.Set;

/**
 * The internal background import queue; it has no endpoints of its own. Imports of every type are queued here by the
 * service of their domain (e.g. {@link ProgramTemplateImportJobService}), claimed by {@code ImportJobWorker} and
 * dispatched to the {@code ImportJobHandler} of their type. Each step runs in its own transaction, so a crash leaves a
 * job either pending or claimed, and a claimed job is taken over by another poll once its lease expires.
 */
public interface ImportJobService {

    /**
     * Queues an import, due straight away. Returns the entity because only domain services call it; they map it to
     * their own response.
     *
     * @param type         what the job creates
     * @param catalogEntry the catalog folder to import, resolved by the handler of the type when the job runs
     * @return the pending job
     * @throws ConflictException if a job of this type for the same catalog entry is still pending or in progress
     */
    ImportJob enqueue(final ImportJobType type, final String catalogEntry);

    /**
     * Claims up to the configured batch of due jobs of the given types: pending ones whose next attempt has come, and
     * in-progress ones whose lease expired. Claimed jobs are marked in progress and their attempt count goes up. Jobs
     * another instance is claiming at the same moment are skipped.
     *
     * @param types the job types to claim
     * @return the ids of the claimed jobs, oldest first
     */
    List<Long> claimDueJobs(final Set<ImportJobType> types);

    /**
     * Runs a claimed job: its handler creates the result and the job is completed with the result's id, in one
     * transaction. A job that is no longer in progress (another worker finished it) is left alone.
     *
     * @param importJobId the claimed job's id
     * @throws NotFoundException if the job doesn't exist
     * @throws RuntimeException  whatever the handler throws; nothing is saved, and the caller records the failure
     *                           with {@link #recordFailure}
     */
    void processJob(final Long importJobId);

    /**
     * Records why a claimed job failed. Problems with the content (bad request, conflict, broken constraint) fail it
     * for good; other problems schedule a retry with a doubling wait, until the configured attempts are used up.
     *
     * @param importJobId the claimed job's id
     * @param exception   what {@link #processJob} threw
     * @throws NotFoundException if the job doesn't exist
     */
    void recordFailure(final Long importJobId, final RuntimeException exception);
}
