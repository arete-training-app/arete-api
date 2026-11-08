package app.arete.arete_api.util.enums;

import java.util.EnumSet;
import java.util.Set;

public enum ImportJobStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED;

    /**
     * Statuses of a job that may still create its result; matches the partial unique index on import_job.
     */
    public static final Set<ImportJobStatus> ACTIVE = EnumSet.of(PENDING, PROCESSING);
}
