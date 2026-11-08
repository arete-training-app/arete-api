package app.arete.arete_api.repository;

import app.arete.arete_api.model.entity.ImportJob;
import app.arete.arete_api.util.enums.ImportJobStatus;
import app.arete.arete_api.util.enums.ImportJobType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ImportJobRepository extends JpaRepository<ImportJob, Long>, JpaSpecificationExecutor<ImportJob> {

    Optional<ImportJob> findByIdAndType(final Long id, final ImportJobType type);

    boolean existsByTypeAndCatalogEntryAndStatusIn(
            final ImportJobType type,
            final String catalogEntry,
            final Collection<ImportJobStatus> statuses);

    Optional<ImportJob> findFirstByTypeAndCatalogEntryOrderByIdDesc(
            final ImportJobType type,
            final String catalogEntry);

    /**
     * Locks the jobs of the given types that are due: pending ones whose next attempt has come, and in-progress ones
     * whose run held them longer than the lease. Native because JPQL has no SKIP LOCKED: rows another run is claiming
     * are skipped instead of waited for, so several instances can run at once.
     */
    @Query(value = """
            SELECT job.id
            FROM import_job job
            WHERE job.type IN (:types)
              AND ((job.status = :pendingStatus AND job.next_attempt_at <= :now)
                OR (job.status = :processingStatus AND job.locked_at < :staleBefore))
            ORDER BY job.id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Long> lockDueJobIds(
            @Param("types") final Collection<String> types,
            @Param("pendingStatus") final String pendingStatus,
            @Param("processingStatus") final String processingStatus,
            @Param("now") final LocalDateTime now,
            @Param("staleBefore") final LocalDateTime staleBefore,
            @Param("limit") final int limit);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE ImportJob job
            SET job.status = :processingStatus,
                job.lockedAt = :now,
                job.updatedAt = :now,
                job.attempts = job.attempts + 1,
                job.version = job.version + 1
            WHERE job.id IN :jobIds
            """)
    int markProcessing(
            @Param("jobIds") final Collection<Long> jobIds,
            @Param("processingStatus") final ImportJobStatus processingStatus,
            @Param("now") final LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT job FROM ImportJob job WHERE job.id = :jobId")
    Optional<ImportJob> findByIdForUpdate(@Param("jobId") final Long jobId);
}
