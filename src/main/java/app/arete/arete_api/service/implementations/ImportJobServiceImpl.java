package app.arete.arete_api.service.implementations;

import app.arete.arete_api.config.properties.ImportJobProperties;
import app.arete.arete_api.exception.BadRequestException;
import app.arete.arete_api.exception.ConflictException;
import app.arete.arete_api.exception.NotFoundException;
import app.arete.arete_api.importjob.ImportJobHandler;
import app.arete.arete_api.model.entity.ImportJob;
import app.arete.arete_api.repository.ImportJobRepository;
import app.arete.arete_api.service.ImportJobService;
import app.arete.arete_api.util.enums.ImportJobStatus;
import app.arete.arete_api.util.enums.ImportJobType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportJobServiceImpl implements ImportJobService {

    private static final String IMPORT_JOB_NOT_FOUND_DETAIL = "Import job not found";
    private static final String ALREADY_QUEUED_DETAIL = "An import job for catalog entry '%s' is already queued or running";
    // Caps the doubling of the retry wait, so a large attempt count can't overflow the multiplier
    private static final int MAX_BACKOFF_DOUBLINGS = 16;

    private final ImportJobRepository importJobRepository;
    private final List<ImportJobHandler> importJobHandlers;
    private final ImportJobProperties importJobProperties;
    private final Clock clock;

    @Override
    @Transactional
    public ImportJob enqueue(final ImportJobType type, final String catalogEntry) {
        if (importJobRepository.existsByTypeAndCatalogEntryAndStatusIn(type, catalogEntry, ImportJobStatus.ACTIVE)) {
            throw new ConflictException(ALREADY_QUEUED_DETAIL.formatted(catalogEntry));
        }
        final ImportJob importJob = ImportJob.builder()
                .type(type)
                .status(ImportJobStatus.PENDING)
                .catalogEntry(catalogEntry)
                .nextAttemptAt(LocalDateTime.now(clock))
                .build();
        try {
            final ImportJob queuedImportJob = importJobRepository.saveAndFlush(importJob);
            log.info("Import job {} queued: {} '{}'", queuedImportJob.getId(), type, catalogEntry);
            return queuedImportJob;
        } catch (final DataIntegrityViolationException exception) {
            // Another request queued the same catalog entry between the check and the insert
            throw new ConflictException(ALREADY_QUEUED_DETAIL.formatted(catalogEntry));
        }
    }

    @Override
    @Transactional
    public List<Long> claimDueJobs(final Set<ImportJobType> types) {
        if (types.isEmpty()) {
            return List.of();
        }
        final LocalDateTime now = LocalDateTime.now(clock);
        final List<Long> jobIds = importJobRepository.lockDueJobIds(
                types.stream().map(ImportJobType::name).toList(),
                ImportJobStatus.PENDING.name(),
                ImportJobStatus.PROCESSING.name(),
                now,
                now.minus(importJobProperties.lease()),
                importJobProperties.batchSize());
        if (!jobIds.isEmpty()) {
            importJobRepository.markProcessing(jobIds, ImportJobStatus.PROCESSING, now);
            log.info("Claimed import jobs {}", jobIds);
        }
        return jobIds;
    }

    @Override
    @Transactional
    public void processJob(final Long importJobId) {
        final ImportJob importJob = getImportJobForUpdate(importJobId);
        if (importJob.getStatus() != ImportJobStatus.PROCESSING) {
            return;
        }
        final Long resultId = getHandler(importJob.getType()).handle(importJob.getCatalogEntry());
        importJob.complete(resultId, LocalDateTime.now(clock));
        log.info("Import job {} completed: '{}' -> result {}", importJobId, importJob.getCatalogEntry(), resultId);
    }

    @Override
    @Transactional
    public void recordFailure(final Long importJobId, final RuntimeException exception) {
        final ImportJob importJob = getImportJobForUpdate(importJobId);
        if (importJob.getStatus() != ImportJobStatus.PROCESSING) {
            return;
        }
        final LocalDateTime now = LocalDateTime.now(clock);
        final String error = describe(exception);
        if (isPermanent(exception) || importJob.getAttempts() >= importJobProperties.maxAttempts()) {
            importJob.failPermanently(error, now);
            log.warn("Import job {} failed for good after {} attempt(s): {}",
                    importJobId, importJob.getAttempts(), error);
            return;
        }
        final int doublings = Math.min(importJob.getAttempts() - 1, MAX_BACKOFF_DOUBLINGS);
        final LocalDateTime nextAttemptAt =
                now.plus(importJobProperties.retryBackoff().multipliedBy(1L << doublings));
        importJob.scheduleRetry(error, nextAttemptAt);
        log.warn("Import job {} will be retried at {}: {}", importJobId, nextAttemptAt, error);
    }

    private ImportJob getImportJobForUpdate(final Long importJobId) {
        return importJobRepository.findByIdForUpdate(importJobId)
                .orElseThrow(() -> new NotFoundException(IMPORT_JOB_NOT_FOUND_DETAIL));
    }

    private ImportJobHandler getHandler(final ImportJobType type) {
        return importJobHandlers.stream()
                .filter(handler -> handler.type() == type)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No import job handler for " + type));
    }

    /**
     * Problems with the content itself fail the same way on every attempt, so they aren't retried.
     */
    private static boolean isPermanent(final RuntimeException exception) {
        return exception instanceof BadRequestException
                || exception instanceof ConflictException
                || exception instanceof DataIntegrityViolationException;
    }

    private static String describe(final RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
