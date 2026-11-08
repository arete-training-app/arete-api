package app.arete.arete_api.model.entity;

import app.arete.arete_api.util.enums.ImportJobStatus;
import app.arete.arete_api.util.enums.ImportJobType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One queued import. A worker claims it (PROCESSING), then completes it with the id of what it created, retries it
 * later, or fails it for good.
 */
@Table(name = "import_job", schema = "public")
@Getter
@Setter
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class ImportJob extends Auditable {

    private static final int ERROR_MAX_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportJobType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportJobStatus status;

    // The catalog folder the job imports, resolved by the handler of the job's type when it runs
    @Column(name = "catalog_entry", nullable = false, length = 100)
    private String catalogEntry;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "result_id")
    private Long resultId;

    @Column(length = ERROR_MAX_LENGTH)
    private String error;

    public void complete(final Long resultId, final LocalDateTime now) {
        this.status = ImportJobStatus.COMPLETED;
        this.resultId = resultId;
        this.completedAt = now;
        this.lockedAt = null;
        this.error = null;
    }

    /**
     * Fails the job without retrying.
     */
    public void failPermanently(final String error, final LocalDateTime now) {
        this.status = ImportJobStatus.FAILED;
        this.error = truncate(error);
        this.completedAt = now;
        this.lockedAt = null;
    }

    public void scheduleRetry(final String error, final LocalDateTime nextAttemptAt) {
        this.status = ImportJobStatus.PENDING;
        this.error = truncate(error);
        this.nextAttemptAt = nextAttemptAt;
        this.lockedAt = null;
    }

    private static String truncate(final String error) {
        return error.length() <= ERROR_MAX_LENGTH ? error : error.substring(0, ERROR_MAX_LENGTH);
    }
}
