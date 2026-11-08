package app.arete.arete_api.importjob;

import app.arete.arete_api.exception.BadRequestException;
import app.arete.arete_api.exception.ConflictException;
import app.arete.arete_api.util.enums.ImportJobType;

/**
 * Creates the result of one type of import job. Implementations are Spring beans; the import worker picks the one
 * whose {@link #type()} matches the job. Supporting a new kind of import means adding an {@link ImportJobType} value
 * and a handler for it.
 */
public interface ImportJobHandler {

    ImportJobType type();

    /**
     * Reads the catalog entry's files and creates what they describe. Runs inside the transaction that completes the
     * job, so the result and the job's completion are saved together or not at all.
     *
     * @param catalogEntry the catalog folder to import, as it was queued
     * @return the id of the created result
     * @throws BadRequestException if the entry is gone or its files are invalid; the job then fails without a retry
     * @throws ConflictException   if the result already exists; the job then fails without a retry
     */
    Long handle(final String catalogEntry);
}
