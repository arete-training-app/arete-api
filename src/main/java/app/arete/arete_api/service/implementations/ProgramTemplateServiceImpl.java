package app.arete.arete_api.service.implementations;

import app.arete.arete_api.api.model.ProgramTemplateDayResponseDto;
import app.arete.arete_api.api.model.ProgramTemplatePageResponseDto;
import app.arete.arete_api.api.model.ProgramTemplateResponseDto;
import app.arete.arete_api.api.model.ProgramTemplateSortFieldDto;
import app.arete.arete_api.api.model.SortDirectionDto;
import app.arete.arete_api.config.properties.ProgramTemplateProperties;
import app.arete.arete_api.exception.BadRequestException;
import app.arete.arete_api.exception.ConflictException;
import app.arete.arete_api.exception.NotFoundException;
import app.arete.arete_api.mapper.PaginationMapper;
import app.arete.arete_api.mapper.ProgramTemplateMapper;
import app.arete.arete_api.model.dto.programtemplate.ProgramTemplateContent;
import app.arete.arete_api.model.dto.programtemplate.ProgramTemplateContentDay;
import app.arete.arete_api.model.dto.programtemplate.ProgramTemplateFilterDto;
import app.arete.arete_api.model.entity.ProgramTemplate;
import app.arete.arete_api.model.entity.ProgramTemplateDay;
import app.arete.arete_api.repository.ProgramTemplateDayRepository;
import app.arete.arete_api.repository.ProgramTemplateRepository;
import app.arete.arete_api.service.ProgramTemplateService;
import app.arete.arete_api.specification.ProgramTemplateSpecifications;
import app.arete.arete_api.util.PaginationUtils;
import app.arete.arete_api.util.ProgramTemplateContentHasher;
import app.arete.arete_api.validation.ProgramTemplateContentValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramTemplateServiceImpl implements ProgramTemplateService {

    private static final String PROGRAM_TEMPLATE_NOT_FOUND_DETAIL = "Program template not found";
    private static final int FIRST_POSITION = 1;

    private final ProgramTemplateRepository programTemplateRepository;
    private final ProgramTemplateDayRepository programTemplateDayRepository;
    private final ProgramTemplateSpecifications programTemplateSpecifications;
    private final ProgramTemplateMapper programTemplateMapper;
    private final PaginationMapper paginationMapper;
    private final ProgramTemplateContentValidator programTemplateContentValidator;
    private final ProgramTemplateProperties programTemplateProperties;

    @Override
    @Transactional
    public ProgramTemplateResponseDto createProgramTemplate(final ProgramTemplateContent content) {
        programTemplateContentValidator.validate(content);
        final List<ProgramTemplateContentDay> days = content.days();
        final String contentHash = ProgramTemplateContentHasher.hashDays(days);
        programTemplateRepository.findByContentHash(contentHash).ifPresent(existing -> {
            throw new ConflictException(
                    "These days were already imported as program template %d".formatted(existing.getId()));
        });

        final int lastPosition = days.stream().mapToInt(ProgramTemplateContentDay::position).max().orElseThrow();
        final ProgramTemplate programTemplate = programTemplateRepository.save(
                programTemplateMapper.toProgramTemplate(content, contentHash, days.size(), lastPosition));
        programTemplateDayRepository.saveAll(days.stream().map(day -> buildDay(programTemplate, day)).toList());
        log.info("Program template {} created: '{}', {} days over {} positions, starting {}",
                programTemplate.getId(), programTemplate.getName(), days.size(), lastPosition,
                programTemplate.getOriginalStartDate());
        return programTemplateMapper.toProgramTemplateResponseDto(programTemplate);
    }

    @Override
    @Transactional(readOnly = true)
    public ProgramTemplatePageResponseDto getProgramTemplates(
            final int page,
            final int size,
            final ProgramTemplateSortFieldDto sort,
            final SortDirectionDto direction,
            final ProgramTemplateFilterDto programTemplateFilterDto) {
        log.debug("Listing program templates: page {}, size {}, sort {} {}, filter {}",
                page, size, sort, direction, programTemplateFilterDto);
        final Pageable pageable = PaginationUtils.toPageRequest(
                page,
                size,
                programTemplateMapper.toProgramTemplateSortField(sort).getValue(),
                paginationMapper.toSortDirection(direction));
        final Specification<ProgramTemplate> specification =
                programTemplateSpecifications.buildFilters(programTemplateFilterDto);
        return programTemplateMapper.toProgramTemplatePageResponseDto(
                programTemplateRepository.findAll(specification, pageable));
    }

    @Override
    public ProgramTemplate getProgramTemplateEntity(final Long programTemplateId) {
        return programTemplateRepository.findById(programTemplateId)
                .orElseThrow(() -> new NotFoundException(PROGRAM_TEMPLATE_NOT_FOUND_DETAIL));
    }

    @Override
    public List<ProgramTemplateDay> getAllProgramTemplateDays(final ProgramTemplate programTemplate) {
        return programTemplateDayRepository.findAllByProgramTemplateIdAndDayPositionBetweenOrderByDayPosition(
                programTemplate.getId(), FIRST_POSITION, programTemplate.getLastPosition());
    }

    @Override
    @Transactional(readOnly = true)
    public ProgramTemplateResponseDto getProgramTemplateById(final Long programTemplateId) {
        log.debug("Getting program template {}", programTemplateId);
        return programTemplateMapper.toProgramTemplateResponseDto(getProgramTemplateEntity(programTemplateId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProgramTemplateDayResponseDto> getProgramTemplateDays(
            final Long programTemplateId,
            final int fromDay,
            final int toDay) {
        validateRange(fromDay, toDay);
        final ProgramTemplate programTemplate = getProgramTemplateEntity(programTemplateId);
        final List<ProgramTemplateDay> programTemplateDays =
                programTemplateDayRepository.findAllByProgramTemplateIdAndDayPositionBetweenOrderByDayPosition(
                        programTemplateId, fromDay, toDay);
        log.debug("Program template {}: {} covered day(s) between positions {} and {}",
                programTemplateId, programTemplateDays.size(), fromDay, toDay);
        return programTemplateMapper.toProgramTemplateDayResponseDtos(
                programTemplateDays, programTemplate.getOriginalStartDate());
    }

    @Override
    @Transactional
    public void deleteProgramTemplate(final Long programTemplateId) {
        final ProgramTemplate programTemplate = getProgramTemplateEntity(programTemplateId);
        programTemplateRepository.delete(programTemplate);
        log.info("Program template {} deleted: '{}'", programTemplateId, programTemplate.getName());
    }

    private ProgramTemplateDay buildDay(
            final ProgramTemplate programTemplate,
            final ProgramTemplateContentDay contentDay) {
        final ProgramTemplateDay programTemplateDay =
                programTemplateMapper.toProgramTemplateDay(contentDay, programTemplate);
        programTemplateDay.appendParts(programTemplateMapper.toProgramTemplateParts(contentDay.parts()));
        return programTemplateDay;
    }

    private void validateRange(final int fromDay, final int toDay) {
        if (toDay < fromDay) {
            throw new BadRequestException("'toDay' must not be before 'fromDay'");
        }
        final int rangeMaxDays = programTemplateProperties.rangeMaxDays();
        if (toDay - fromDay + 1 > rangeMaxDays) {
            throw new BadRequestException("A period can span at most %d days".formatted(rangeMaxDays));
        }
    }
}
