package app.arete.arete_api.service;

import app.arete.arete_api.api.model.MoveProgramDayRequestDto;
import app.arete.arete_api.api.model.ProgramCalendarResponseDto;
import app.arete.arete_api.api.model.ProgramDayFeedbackRequestDto;
import app.arete.arete_api.api.model.ProgramDayPageResponseDto;
import app.arete.arete_api.api.model.ProgramDayResponseDto;
import app.arete.arete_api.api.model.ProgramDaySortFieldDto;
import app.arete.arete_api.api.model.SortDirectionDto;
import app.arete.arete_api.exception.BadRequestException;
import app.arete.arete_api.exception.ConflictException;
import app.arete.arete_api.exception.NotFoundException;
import app.arete.arete_api.model.dto.program.ProgramDayFilterDto;
import app.arete.arete_api.model.entity.ProgramDay;

import java.time.LocalDate;

/**
 * The dated days of the signed-in user's programs. A date without a day is a rest day. Every method works only on
 * programs the caller owns and throws {@link NotFoundException} for a missing program or another user's program.
 */
public interface ProgramDayService {

    /**
     * Returns one entry for every date from {@code from} to {@code to}, both inclusive, with the day planned on that
     * date or no day on rest days.
     *
     * @param programId the program id
     * @param from      the first date of the range
     * @param to        the last date of the range
     * @return one calendar entry per date in the range, in date order
     * @throws BadRequestException if {@code to} is before {@code from}, or the range is longer than
     *                             {@code arete.program.calendar-max-days}
     * @throws NotFoundException   if the program doesn't exist or belongs to another user
     */
    ProgramCalendarResponseDto getUserProgramCalendar(final Long programId, final LocalDate from, final LocalDate to);

    /**
     * Returns one page of the program's planned days. Rows with the same sort value are ordered by id, so pages are
     * stable.
     *
     * @param programId           the program id
     * @param page                zero-based page index
     * @param size                number of days per page
     * @param sort                the field to sort by
     * @param direction           the sort direction
     * @param programDayFilterDto optional filters, combined with AND; {@code null} components don't filter
     * @return the requested page and the totals
     * @throws BadRequestException if {@code to} is before {@code from}
     * @throws NotFoundException   if the program doesn't exist or belongs to another user
     */
    ProgramDayPageResponseDto getUserProgramDays(
            final Long programId,
            final int page,
            final int size,
            final ProgramDaySortFieldDto sort,
            final SortDirectionDto direction,
            final ProgramDayFilterDto programDayFilterDto);

    /**
     * Returns the day planned on one date, with its program parts in order.
     *
     * @param programId the program id
     * @param date      the date of the day
     * @return the day
     * @throws NotFoundException if the program doesn't exist or belongs to another user, or the date is a rest day
     */
    ProgramDayResponseDto getUserProgramDay(final Long programId, final LocalDate date);

    /**
     * Deletes the day planned on one date with all its program parts, making it a rest day.
     *
     * @param programId the program id
     * @param date      the date of the day
     * @throws NotFoundException if the program doesn't exist or belongs to another user, or the date is a rest day
     */
    void deleteUserProgramDay(final Long programId, final LocalDate date);

    /**
     * Saves the user's feedback on how a day went: its status, notes and the results of the listed program parts.
     * Parts that aren't listed keep their feedback, and the plan itself isn't changed.
     *
     * @param programId                    the program id
     * @param date                         the date of the day
     * @param programDayFeedbackRequestDto the status, notes and part results
     * @return the day with its feedback
     * @throws BadRequestException if the status is {@code COMPLETED} and the date is after today (UTC), or a listed
     *                             part doesn't belong to this day
     * @throws NotFoundException   if the program doesn't exist or belongs to another user, or the date is a rest day
     */
    ProgramDayResponseDto updateUserProgramDayFeedback(
            final Long programId,
            final LocalDate date,
            final ProgramDayFeedbackRequestDto programDayFeedbackRequestDto);

    /**
     * Moves the day planned on one date to another date, leaving the first date a rest day. If the other date
     * already has a day, the moved parts are added after its parts; otherwise the day itself moves. Either way the
     * moved parts start again as planned, without results.
     *
     * @param programId                the program id
     * @param date                     the date of the day to move
     * @param moveProgramDayRequestDto the date to move it to
     * @return the day at its new date
     * @throws BadRequestException if the new date is the same as the current one
     * @throws NotFoundException   if the program doesn't exist or belongs to another user, or the date is a rest day
     * @throws ConflictException   if the day is already completed
     */
    ProgramDayResponseDto moveUserProgramDay(
            final Long programId,
            final LocalDate date,
            final MoveProgramDayRequestDto moveProgramDayRequestDto);

    /**
     * Returns the entity of the day planned on one date, for other services that change its program parts.
     *
     * @param programId the program id
     * @param date      the date of the day
     * @return the day entity
     * @throws NotFoundException if the program doesn't exist or belongs to another user, or the date is a rest day
     */
    ProgramDay getUserProgramDayEntity(final Long programId, final LocalDate date);

    /**
     * Returns the entity of the day planned on one date. A rest day gets a new, unsaved day with status
     * {@code PLANNED}, which the caller saves together with its first part.
     *
     * @param programId the program id
     * @param date      the date of the day
     * @return the existing day entity, or a new unsaved one for a rest day
     * @throws NotFoundException if the program doesn't exist or belongs to another user
     */
    ProgramDay getOrCreateUserProgramDayEntity(final Long programId, final LocalDate date);
}
