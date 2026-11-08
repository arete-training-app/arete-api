package app.arete.arete_api.service;

import app.arete.arete_api.api.model.ProgramTemplateCatalogPageResponseDto;
import app.arete.arete_api.api.model.ProgramTemplateImportJobPageResponseDto;
import app.arete.arete_api.api.model.ProgramTemplateImportJobRequestDto;
import app.arete.arete_api.api.model.ProgramTemplateImportJobResponseDto;
import app.arete.arete_api.api.model.ProgramTemplateImportJobSortFieldDto;
import app.arete.arete_api.api.model.SortDirectionDto;
import app.arete.arete_api.exception.ConflictException;
import app.arete.arete_api.exception.NotFoundException;
import app.arete.arete_api.model.dto.programtemplate.ProgramTemplateImportJobFilterDto;

/**
 * The program template catalog and its import jobs: what can be imported, queueing an import and following it. Jobs
 * only name a catalog entry; the import worker reads the entry's files and creates the template later, through the
 * internal {@link ImportJobService} queue.
 */
public interface ProgramTemplateImportJobService {

    /**
     * Returns one page of the catalog, ordered by name, with each entry's latest import job.
     *
     * @param page zero-based page index
     * @param size number of entries per page
     * @param name optional text the entry names must contain, ignoring case; {@code null} doesn't filter
     * @return the requested page and the totals
     */
    ProgramTemplateCatalogPageResponseDto getProgramTemplateCatalog(final int page, final int size, final String name);

    /**
     * Queues an import job for a catalog entry. Nothing is read yet: the files are read and checked by the worker,
     * and any problem with them shows up as a failed job.
     *
     * @param importJobRequestDto the catalog entry to import
     * @return the queued, pending import job
     * @throws NotFoundException if the catalog has no such entry
     * @throws ConflictException if an import job for the entry is already queued or running
     */
    ProgramTemplateImportJobResponseDto enqueueImportJob(final ProgramTemplateImportJobRequestDto importJobRequestDto);

    /**
     * Returns one page of program template import jobs, whatever their status. Rows with the same sort value are
     * ordered by id, so pages are stable.
     *
     * @param page                              zero-based page index
     * @param size                              number of import jobs per page
     * @param sort                              the field to sort by
     * @param direction                         the sort direction
     * @param programTemplateImportJobFilterDto optional filters; {@code null} components don't filter
     * @return the requested page and the totals
     */
    ProgramTemplateImportJobPageResponseDto getProgramTemplateImportJobs(
            final int page,
            final int size,
            final ProgramTemplateImportJobSortFieldDto sort,
            final SortDirectionDto direction,
            final ProgramTemplateImportJobFilterDto programTemplateImportJobFilterDto);

    /**
     * Returns the progress of one program template import job, with the template it created once it is completed.
     *
     * @param importJobId the import job id
     * @return the import job
     * @throws NotFoundException if no program template import job has this id
     */
    ProgramTemplateImportJobResponseDto getProgramTemplateImportJob(final Long importJobId);
}
