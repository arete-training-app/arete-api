package app.arete.arete_api.service.implementations;

import app.arete.arete_api.api.model.CalendarDayResponseDto;
import app.arete.arete_api.api.model.MoveProgramDayRequestDto;
import app.arete.arete_api.api.model.ProgramCalendarResponseDto;
import app.arete.arete_api.api.model.ProgramDayFeedbackRequestDto;
import app.arete.arete_api.api.model.ProgramDayPageResponseDto;
import app.arete.arete_api.api.model.ProgramDayResponseDto;
import app.arete.arete_api.api.model.ProgramDaySortFieldDto;
import app.arete.arete_api.api.model.SortDirectionDto;
import app.arete.arete_api.api.model.ProgramPartFeedbackRequestDto;
import app.arete.arete_api.config.properties.ProgramProperties;
import app.arete.arete_api.exception.BadRequestException;
import app.arete.arete_api.exception.ConflictException;
import app.arete.arete_api.exception.NotFoundException;
import app.arete.arete_api.mapper.PaginationMapper;
import app.arete.arete_api.mapper.ProgramDayMapper;
import app.arete.arete_api.model.dto.program.ProgramDayFilterDto;
import app.arete.arete_api.model.entity.Program;
import app.arete.arete_api.model.entity.ProgramDay;
import app.arete.arete_api.repository.ProgramDayRepository;
import app.arete.arete_api.service.ProgramDayService;
import app.arete.arete_api.service.ProgramService;
import app.arete.arete_api.specification.ProgramDaySpecifications;
import app.arete.arete_api.util.PaginationUtils;
import app.arete.arete_api.util.enums.DayStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramDayServiceImpl implements ProgramDayService {

    private static final String PROGRAM_DAY_NOT_FOUND_DETAIL = "No day is planned on this date";

    private final ProgramDayRepository programDayRepository;
    private final ProgramDaySpecifications programDaySpecifications;
    private final ProgramDayMapper programDayMapper;
    private final PaginationMapper paginationMapper;
    private final ProgramService programService;
    private final ProgramProperties programProperties;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public ProgramCalendarResponseDto getUserProgramCalendar(
            final Long programId,
            final LocalDate from,
            final LocalDate to) {
        log.debug("Getting the calendar of program {} from {} to {}", programId, from, to);
        validateCalendarRange(from, to);
        final Program program = programService.getUserProgramEntity(programId);
        final Map<LocalDate, ProgramDay> dayByDate = programDayRepository
                .findAllByProgramIdAndDayDateBetween(program.getId(), from, to).stream()
                .collect(Collectors.toMap(ProgramDay::getDayDate, Function.identity()));
        final List<CalendarDayResponseDto> calendarDays = from.datesUntil(to.plusDays(1))
                .map(date -> programDayMapper.toCalendarDayResponseDto(date, dayByDate.get(date)))
                .toList();
        return programDayMapper.toProgramCalendarResponseDto(program.getId(), from, to, calendarDays);
    }

    @Override
    @Transactional(readOnly = true)
    public ProgramDayPageResponseDto getUserProgramDays(
            final Long programId,
            final int page,
            final int size,
            final ProgramDaySortFieldDto sort,
            final SortDirectionDto direction,
            final ProgramDayFilterDto programDayFilterDto) {
        log.debug("Listing days of program {}: page {}, size {}, sort {} {}, filter {}",
                programId, page, size, sort, direction, programDayFilterDto);
        validateFilterRange(programDayFilterDto);
        final Program program = programService.getUserProgramEntity(programId);
        final Pageable pageable = PaginationUtils.toPageRequest(
                page,
                size,
                programDayMapper.toProgramDaySortField(sort).getValue(),
                paginationMapper.toSortDirection(direction));
        final Specification<ProgramDay> specification =
                programDaySpecifications.buildFilters(program.getId(), programDayFilterDto);
        return programDayMapper.toProgramDayPageResponseDto(programDayRepository.findAll(specification, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public ProgramDayResponseDto getUserProgramDay(final Long programId, final LocalDate date) {
        log.debug("Getting the day of program {} on {}", programId, date);
        return programDayMapper.toProgramDayResponseDto(getUserProgramDayEntity(programId, date));
    }

    @Override
    @Transactional
    public void deleteUserProgramDay(final Long programId, final LocalDate date) {
        final ProgramDay programDay = getUserProgramDayEntity(programId, date);
        final int partCount = programDay.getParts().size();
        programDayRepository.delete(programDay);
        log.info("Program {}: day {} deleted with its {} part(s)", programId, date, partCount);
    }

    @Override
    @Transactional
    public ProgramDayResponseDto updateUserProgramDayFeedback(
            final Long programId,
            final LocalDate date,
            final ProgramDayFeedbackRequestDto programDayFeedbackRequestDto) {
        final DayStatus status = programDayMapper.toDayStatus(programDayFeedbackRequestDto.getStatus());
        if (status == DayStatus.COMPLETED && date.isAfter(LocalDate.now(clock))) {
            throw new BadRequestException("A day can't be completed before its date");
        }
        final ProgramDay programDay = getUserProgramDayEntity(programId, date);
        programDay.giveFeedback(status, programDayFeedbackRequestDto.getNotes());
        for (final ProgramPartFeedbackRequestDto partFeedback : programDayFeedbackRequestDto.getParts()) {
            programDay.givePartFeedback(
                    partFeedback.getPartId(),
                    partFeedback.getCompleted(),
                    partFeedback.getResultNotes());
        }
        final ProgramDay savedProgramDay = programDayRepository.save(programDay);
        log.info("Program {}: feedback on {} saved, status {}, {} part result(s)",
                programId, date, status, programDayFeedbackRequestDto.getParts().size());
        return programDayMapper.toProgramDayResponseDto(savedProgramDay);
    }

    @Override
    @Transactional
    public ProgramDayResponseDto moveUserProgramDay(
            final Long programId,
            final LocalDate date,
            final MoveProgramDayRequestDto moveProgramDayRequestDto) {
        final LocalDate toDate = moveProgramDayRequestDto.getToDate();
        if (toDate.equals(date)) {
            throw new BadRequestException("The day is already on this date");
        }
        final ProgramDay sourceDay = getUserProgramDayEntity(programId, date);
        if (sourceDay.getStatus() == DayStatus.COMPLETED) {
            throw new ConflictException("A completed day can't be moved");
        }
        final Optional<ProgramDay> targetDay =
                programDayRepository.findByProgramIdAndDayDate(sourceDay.getProgram().getId(), toDate);
        if (targetDay.isEmpty()) {
            sourceDay.moveTo(toDate);
            final ProgramDay movedDay = programDayRepository.saveAndFlush(sourceDay);
            log.info("Program {}: day {} moved to {}", programId, date, toDate);
            return programDayMapper.toProgramDayResponseDto(movedDay);
        }
        targetDay.get().appendParts(programDayMapper.copyProgramParts(sourceDay.getParts()));
        programDayRepository.delete(sourceDay);
        final ProgramDay mergedDay = programDayRepository.saveAndFlush(targetDay.get());
        log.info("Program {}: day {} merged into {}, which now has {} part(s)",
                programId, date, toDate, mergedDay.getParts().size());
        return programDayMapper.toProgramDayResponseDto(mergedDay);
    }

    @Override
    @Transactional(readOnly = true)
    public ProgramDay getUserProgramDayEntity(final Long programId, final LocalDate date) {
        final Program program = programService.getUserProgramEntity(programId);
        return programDayRepository.findByProgramIdAndDayDate(program.getId(), date)
                .orElseThrow(() -> new NotFoundException(PROGRAM_DAY_NOT_FOUND_DETAIL));
    }

    @Override
    @Transactional(readOnly = true)
    public ProgramDay getOrCreateUserProgramDayEntity(final Long programId, final LocalDate date) {
        final Program program = programService.getUserProgramEntity(programId);
        return programDayRepository.findByProgramIdAndDayDate(program.getId(), date)
                .orElseGet(() -> programDayMapper.toProgramDay(program, date, DayStatus.PLANNED));
    }

    private void validateCalendarRange(final LocalDate from, final LocalDate to) {
        if (to.isBefore(from)) {
            throw new BadRequestException("'to' must not be before 'from'");
        }
        final long requestedDays = ChronoUnit.DAYS.between(from, to) + 1;
        if (requestedDays > programProperties.calendarMaxDays()) {
            throw new BadRequestException(
                    "The calendar range can span at most %d days".formatted(programProperties.calendarMaxDays()));
        }
    }

    private static void validateFilterRange(final ProgramDayFilterDto programDayFilterDto) {
        if (programDayFilterDto.from() != null
                && programDayFilterDto.to() != null
                && programDayFilterDto.to().isBefore(programDayFilterDto.from())) {
            throw new BadRequestException("'to' must not be before 'from'");
        }
    }
}
