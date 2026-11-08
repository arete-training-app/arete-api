package app.arete.arete_api.service.implementations;

import app.arete.arete_api.api.model.ProgramFromTemplateRequestDto;
import app.arete.arete_api.api.model.ProgramPageResponseDto;
import app.arete.arete_api.api.model.ProgramRequestDto;
import app.arete.arete_api.api.model.ProgramResponseDto;
import app.arete.arete_api.api.model.ProgramSortFieldDto;
import app.arete.arete_api.api.model.SortDirectionDto;
import app.arete.arete_api.exception.BadRequestException;
import app.arete.arete_api.exception.NotFoundException;
import app.arete.arete_api.mapper.PaginationMapper;
import app.arete.arete_api.mapper.ProgramDayMapper;
import app.arete.arete_api.mapper.ProgramMapper;
import app.arete.arete_api.model.dto.program.ProgramFilterDto;
import app.arete.arete_api.model.entity.Program;
import app.arete.arete_api.model.entity.ProgramDay;
import app.arete.arete_api.model.entity.ProgramTemplate;
import app.arete.arete_api.model.entity.ProgramTemplateDay;
import app.arete.arete_api.model.entity.User;
import app.arete.arete_api.repository.ProgramDayRepository;
import app.arete.arete_api.repository.ProgramRepository;
import app.arete.arete_api.service.ProgramService;
import app.arete.arete_api.service.ProgramTemplateService;
import app.arete.arete_api.service.UserService;
import app.arete.arete_api.specification.ProgramSpecifications;
import app.arete.arete_api.util.PaginationUtils;
import app.arete.arete_api.util.ProgramTemplateWeeks;
import app.arete.arete_api.util.enums.DayStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramServiceImpl implements ProgramService {

    private static final String PROGRAM_NOT_FOUND_DETAIL = "Program not found";

    private final ProgramRepository programRepository;
    private final ProgramSpecifications programSpecifications;
    private final ProgramMapper programMapper;
    private final PaginationMapper paginationMapper;
    private final UserService userService;
    private final ProgramTemplateService programTemplateService;
    private final ProgramDayRepository programDayRepository;
    private final ProgramDayMapper programDayMapper;

    @Override
    public Program getUserProgramEntity(final Long programId) {
        final User user = userService.getAuthenticatedUser();
        return programRepository.findByIdAndOwnerId(programId, user.getId())
                .orElseThrow(() -> new NotFoundException(PROGRAM_NOT_FOUND_DETAIL));
    }

    @Override
    @Transactional(readOnly = true)
    public ProgramPageResponseDto getUserPrograms(
            final int page,
            final int size,
            final ProgramSortFieldDto sort,
            final SortDirectionDto direction,
            final ProgramFilterDto programFilterDto) {
        validateCreatedRange(programFilterDto);
        final User user = userService.getAuthenticatedUser();
        log.debug("Listing programs of user {}: page {}, size {}, sort {} {}, filter {}",
                user.getId(), page, size, sort, direction, programFilterDto);
        final Pageable pageable = PaginationUtils.toPageRequest(
                page,
                size,
                programMapper.toProgramSortField(sort).getValue(),
                paginationMapper.toSortDirection(direction));
        final Specification<Program> specification = programSpecifications.buildFilters(user.getId(), programFilterDto);
        return programMapper.toProgramPageResponseDto(programRepository.findAll(specification, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public ProgramResponseDto getUserProgramById(final Long programId) {
        log.debug("Getting program {}", programId);
        return programMapper.toProgramResponseDto(getUserProgramEntity(programId));
    }

    @Override
    @Transactional
    public ProgramResponseDto createUserProgram(final ProgramRequestDto programRequestDto) {
        final User user = userService.getAuthenticatedUser();
        final Program program = programRepository.save(programMapper.toProgram(programRequestDto, user));
        log.info("Program {} created by user {}: '{}'", program.getId(), user.getId(), program.getName());
        return programMapper.toProgramResponseDto(program);
    }

    @Override
    @Transactional
    public ProgramResponseDto createUserProgramFromTemplate(
            final Long programTemplateId,
            final ProgramFromTemplateRequestDto programFromTemplateRequestDto) {
        final ProgramTemplate programTemplate = programTemplateService.getProgramTemplateEntity(programTemplateId);
        final LocalDate startDate = ProgramTemplateWeeks.startDateInWeek(
                programFromTemplateRequestDto.getStartWeek(), programTemplate.getOriginalStartDate());
        final String description = programFromTemplateRequestDto.getDescription() == null
                ? programTemplate.getDescription()
                : programFromTemplateRequestDto.getDescription();

        final User user = userService.getAuthenticatedUser();
        final Program program = programRepository.save(
                programMapper.toProgram(programFromTemplateRequestDto, description, user));
        final List<ProgramDay> programDays = programDayRepository.saveAll(
                programTemplateService.getAllProgramTemplateDays(programTemplate).stream()
                        .map(templateDay -> buildProgramDay(program, startDate, templateDay))
                        .toList());
        log.info("Program {} created by user {} from template {}: '{}', {} days starting {}",
                program.getId(), user.getId(), programTemplateId, program.getName(), programDays.size(), startDate);
        return programMapper.toProgramResponseDto(program);
    }

    @Override
    @Transactional
    public ProgramResponseDto updateUserProgram(final Long programId, final ProgramRequestDto programRequestDto) {
        final Program program = getUserProgramEntity(programId);
        programMapper.updateProgramFromDto(programRequestDto, program);
        final Program savedProgram = programRepository.save(program);
        log.info("Program {} updated: '{}'", programId, savedProgram.getName());
        return programMapper.toProgramResponseDto(savedProgram);
    }

    @Override
    @Transactional
    public void deleteUserProgram(final Long programId) {
        final Program program = getUserProgramEntity(programId);
        programRepository.delete(program);
        log.info("Program {} deleted: '{}'", programId, program.getName());
    }

    private ProgramDay buildProgramDay(
            final Program program,
            final LocalDate startDate,
            final ProgramTemplateDay templateDay) {
        final LocalDate dayDate = startDate.plusDays(templateDay.getDayPosition() - 1L);
        final ProgramDay programDay = programDayMapper.toProgramDay(program, dayDate, DayStatus.PLANNED);
        programDay.appendParts(programDayMapper.toProgramParts(templateDay.getParts()));
        return programDay;
    }

    private static void validateCreatedRange(final ProgramFilterDto programFilterDto) {
        if (programFilterDto.createdFrom() != null
                && programFilterDto.createdTo() != null
                && programFilterDto.createdTo().isBefore(programFilterDto.createdFrom())) {
            throw new BadRequestException("'createdTo' must not be before 'createdFrom'");
        }
    }
}
