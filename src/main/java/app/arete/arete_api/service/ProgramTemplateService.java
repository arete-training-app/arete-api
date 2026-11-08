package app.arete.arete_api.service;

import app.arete.arete_api.api.model.ProgramTemplateDayResponseDto;
import app.arete.arete_api.api.model.ProgramTemplatePageResponseDto;
import app.arete.arete_api.api.model.ProgramTemplateResponseDto;
import app.arete.arete_api.api.model.ProgramTemplateSortFieldDto;
import app.arete.arete_api.api.model.SortDirectionDto;
import app.arete.arete_api.exception.BadRequestException;
import app.arete.arete_api.exception.ConflictException;
import app.arete.arete_api.exception.NotFoundException;
import app.arete.arete_api.model.dto.programtemplate.ProgramTemplateContent;
import app.arete.arete_api.model.dto.programtemplate.ProgramTemplateFilterDto;
import app.arete.arete_api.model.entity.ProgramTemplate;
import app.arete.arete_api.model.entity.ProgramTemplateDay;

import java.util.List;

/**
 * Program templates: programs without an owner, made of days at relative positions. Access is restricted to admins
 * by the security configuration, not by the service, so the import worker can call it without a signed-in user.
 */
public interface ProgramTemplateService {

    /**
     * Creates a template from content read by a source reader. Positions without a day stay uncovered. For the import
     * worker only; clients queue import jobs through {@link ProgramTemplateImportJobService}.
     *
     * @param content the template's name, description and days
     * @return the created template
     * @throws BadRequestException if the content breaks a rule of {@code ProgramTemplateContentValidator}
     * @throws ConflictException   if a template with the same days already exists
     */
    ProgramTemplateResponseDto createProgramTemplate(final ProgramTemplateContent content);

    /**
     * Returns one page of templates. Rows with the same sort value are ordered by id, so pages are stable.
     *
     * @param page                     zero-based page index
     * @param size                     number of templates per page
     * @param sort                     the field to sort by
     * @param direction                the sort direction
     * @param programTemplateFilterDto optional filters, combined with AND; {@code null} components don't filter
     * @return the requested page and the totals
     */
    ProgramTemplatePageResponseDto getProgramTemplates(
            final int page,
            final int size,
            final ProgramTemplateSortFieldDto sort,
            final SortDirectionDto direction,
            final ProgramTemplateFilterDto programTemplateFilterDto);

    /**
     * Resolves one template as an entity. For other services only; controllers never call it.
     *
     * @param programTemplateId the template id
     * @return the template
     * @throws NotFoundException if the template doesn't exist
     */
    ProgramTemplate getProgramTemplateEntity(final Long programTemplateId);

    /**
     * Returns every covered day of a template, each with its parts, in position order. For other services only;
     * unlike {@link #getProgramTemplateDays}, it isn't limited to a range, because a template's length is already
     * capped at import.
     *
     * @param programTemplate the template
     * @return the covered days, in position order
     */
    List<ProgramTemplateDay> getAllProgramTemplateDays(final ProgramTemplate programTemplate);

    /**
     * Returns one template, without its days.
     *
     * @param programTemplateId the template id
     * @return the template
     * @throws NotFoundException if the template doesn't exist
     */
    ProgramTemplateResponseDto getProgramTemplateById(final Long programTemplateId);

    /**
     * Returns the covered days from one position to another, both inclusive, in position order. A position in the
     * range without a day is uncovered and isn't in the result. Each day carries its Monday-based week and weekday in
     * the source, derived from the template's original start date.
     *
     * @param programTemplateId the template id
     * @param fromDay           the first position of the range
     * @param toDay             the last position of the range
     * @return the covered days of the range, each with its week, weekday and parts in order
     * @throws NotFoundException   if the template doesn't exist
     * @throws BadRequestException if {@code toDay} is before {@code fromDay} or the range is longer than the
     *                             configured maximum
     */
    List<ProgramTemplateDayResponseDto> getProgramTemplateDays(
            final Long programTemplateId,
            final int fromDay,
            final int toDay);

    /**
     * Permanently deletes a template together with all its days.
     *
     * @param programTemplateId the template id
     * @throws NotFoundException if the template doesn't exist
     */
    void deleteProgramTemplate(final Long programTemplateId);
}
