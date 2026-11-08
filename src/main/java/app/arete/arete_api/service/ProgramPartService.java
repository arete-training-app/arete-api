package app.arete.arete_api.service;

import app.arete.arete_api.api.model.ProgramDayResponseDto;
import app.arete.arete_api.api.model.ProgramPartRequestDto;
import app.arete.arete_api.api.model.ProgramPartResponseDto;
import app.arete.arete_api.api.model.ReorderProgramPartsRequestDto;
import app.arete.arete_api.exception.BadRequestException;
import app.arete.arete_api.exception.ConflictException;
import app.arete.arete_api.exception.NotFoundException;

import java.time.LocalDate;

/**
 * The program parts of the days in the signed-in user's programs. Every method works only on programs the caller owns
 * and throws {@link NotFoundException} for a missing program or another user's program.
 */
public interface ProgramPartService {

    /**
     * Adds a part after the existing parts of a date. A rest day becomes a {@code PLANNED} day with this part.
     *
     * @param programId             the program id
     * @param date                  the date of the day
     * @param programPartRequestDto the content of the part
     * @return the created part
     * @throws NotFoundException if the program doesn't exist or belongs to another user
     * @throws ConflictException if the day already has {@code arete.program.max-parts-per-day} parts
     */
    ProgramPartResponseDto createUserProgramPart(
            final Long programId,
            final LocalDate date,
            final ProgramPartRequestDto programPartRequestDto);

    /**
     * Replaces the title, description and duration of a part. Its position and its feedback are kept.
     *
     * @param programId             the program id
     * @param date                  the date of the day
     * @param partId                the part id
     * @param programPartRequestDto the new content of the part
     * @return the updated part
     * @throws NotFoundException if the program doesn't exist or belongs to another user, the date is a rest day, or
     *                           the part isn't part of this day
     */
    ProgramPartResponseDto updateUserProgramPart(
            final Long programId,
            final LocalDate date,
            final Long partId,
            final ProgramPartRequestDto programPartRequestDto);

    /**
     * Deletes a part and moves the parts after it up. Deleting the last part of a day also deletes the day.
     *
     * @param programId the program id
     * @param date      the date of the day
     * @param partId    the part id
     * @throws NotFoundException if the program doesn't exist or belongs to another user, the date is a rest day, or
     *                           the part isn't part of this day
     */
    void deleteUserProgramPart(final Long programId, final LocalDate date, final Long partId);

    /**
     * Sets the order of a day's parts to the order of the requested ids.
     *
     * @param programId                     the program id
     * @param date                          the date of the day
     * @param reorderProgramPartsRequestDto the part ids in the wanted order
     * @return the day with its parts in the new order
     * @throws BadRequestException if the ids aren't exactly the ids of the day's parts
     * @throws NotFoundException   if the program doesn't exist or belongs to another user, or the date is a rest day
     */
    ProgramDayResponseDto reorderUserProgramParts(
            final Long programId,
            final LocalDate date,
            final ReorderProgramPartsRequestDto reorderProgramPartsRequestDto);
}
