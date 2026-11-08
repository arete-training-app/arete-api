package app.arete.arete_api.service;

import app.arete.arete_api.api.model.ProgramFromTemplateRequestDto;
import app.arete.arete_api.api.model.ProgramPageResponseDto;
import app.arete.arete_api.api.model.ProgramRequestDto;
import app.arete.arete_api.api.model.ProgramResponseDto;
import app.arete.arete_api.api.model.ProgramSortFieldDto;
import app.arete.arete_api.api.model.SortDirectionDto;
import app.arete.arete_api.exception.BadRequestException;
import app.arete.arete_api.exception.NotFoundException;
import app.arete.arete_api.model.dto.program.ProgramFilterDto;
import app.arete.arete_api.model.entity.Program;

/**
 * The signed-in user's training programs. Every method works only on programs the caller owns; another user's
 * program is reported as not found, so ids of other users' programs can't be discovered.
 */
public interface ProgramService {

    /**
     * Resolves one of the caller's programs as an entity. For other services only; controllers never call it.
     *
     * @param programId the program id
     * @return the program, to be used as the parent of its days
     * @throws NotFoundException if the program doesn't exist or belongs to another user
     */
    Program getUserProgramEntity(final Long programId);

    /**
     * Returns one page of the caller's programs. Rows with the same sort value are ordered by id, so pages are
     * stable.
     *
     * @param page             zero-based page index
     * @param size             number of programs per page
     * @param sort             the field to sort by
     * @param direction        the sort direction
     * @param programFilterDto optional filters, combined with AND; {@code null} components don't filter
     * @return the requested page and the totals
     * @throws BadRequestException if {@code createdTo} is before {@code createdFrom}
     */
    ProgramPageResponseDto getUserPrograms(
            final int page,
            final int size,
            final ProgramSortFieldDto sort,
            final SortDirectionDto direction,
            final ProgramFilterDto programFilterDto);

    /**
     * Returns one of the caller's programs, without its days.
     *
     * @param programId the program id
     * @return the program
     * @throws NotFoundException if the program doesn't exist or belongs to another user
     */
    ProgramResponseDto getUserProgramById(final Long programId);

    /**
     * Creates a program owned by the caller, without any days.
     *
     * @param programRequestDto the program's name and description
     * @return the created program
     */
    ProgramResponseDto createUserProgram(final ProgramRequestDto programRequestDto);

    /**
     * Creates a program owned by the caller from a template, copying every covered day and its parts as planned
     * work without feedback. Position 1 lands in the requested start week, on the same weekday as in the template's
     * source, and every other day keeps its distance from it: every workout keeps its weekday, and positions
     * without a day stay empty. Without a description, the template's is used.
     *
     * @param programTemplateId             the template to copy
     * @param programFromTemplateRequestDto the program's name, optional description and start week
     * @return the created program
     * @throws NotFoundException if the template doesn't exist
     */
    ProgramResponseDto createUserProgramFromTemplate(
            final Long programTemplateId,
            final ProgramFromTemplateRequestDto programFromTemplateRequestDto);

    /**
     * Replaces the name and description of one of the caller's programs. Its days are left unchanged.
     *
     * @param programId         the program id
     * @param programRequestDto the new name and description
     * @return the updated program
     * @throws NotFoundException if the program doesn't exist or belongs to another user
     */
    ProgramResponseDto updateUserProgram(final Long programId, final ProgramRequestDto programRequestDto);

    /**
     * Permanently deletes one of the caller's programs together with all its days.
     *
     * @param programId the program id
     * @throws NotFoundException if the program doesn't exist or belongs to another user
     */
    void deleteUserProgram(final Long programId);
}
