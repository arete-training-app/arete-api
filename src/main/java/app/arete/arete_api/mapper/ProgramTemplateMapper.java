package app.arete.arete_api.mapper;

import app.arete.arete_api.api.model.DayOfWeekDto;
import app.arete.arete_api.api.model.ProgramTemplateDayResponseDto;
import app.arete.arete_api.api.model.ProgramTemplatePageResponseDto;
import app.arete.arete_api.api.model.ProgramTemplatePartResponseDto;
import app.arete.arete_api.api.model.ProgramTemplateResponseDto;
import app.arete.arete_api.api.model.ProgramTemplateSortFieldDto;
import app.arete.arete_api.model.dto.programtemplate.ProgramTemplateContent;
import app.arete.arete_api.model.dto.programtemplate.ProgramTemplateContentDay;
import app.arete.arete_api.model.dto.programtemplate.ProgramTemplateContentPart;
import app.arete.arete_api.model.dto.programtemplate.ProgramTemplateDayFilePart;
import app.arete.arete_api.model.dto.programtemplate.ProgramTemplateFilterDto;
import app.arete.arete_api.model.entity.ProgramTemplate;
import app.arete.arete_api.model.entity.ProgramTemplateDay;
import app.arete.arete_api.model.entity.ProgramTemplatePart;
import app.arete.arete_api.util.ProgramTemplateWeeks;
import app.arete.arete_api.util.enums.ProgramTemplateSortField;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;
import org.springframework.data.domain.Page;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@Mapper(config = CentralMapperConfig.class, uses = DateTimeMapper.class, imports = ProgramTemplateWeeks.class)
public interface ProgramTemplateMapper {

    @Mapping(target = "weekCount", expression = "java(ProgramTemplateWeeks.weekOfPosition("
            + "programTemplate.getLastPosition(), programTemplate.getOriginalStartDate()))")
    ProgramTemplateResponseDto toProgramTemplateResponseDto(final ProgramTemplate programTemplate);

    @Mapping(target = "page", source = "number")
    ProgramTemplatePageResponseDto toProgramTemplatePageResponseDto(final Page<ProgramTemplate> programTemplatePage);

    /**
     * The week and weekday come from the template's original start date, which fixes the weekday of every position.
     */
    @Mapping(target = "position", source = "programTemplateDay.dayPosition")
    @Mapping(target = "week", expression = "java(ProgramTemplateWeeks.weekOfPosition("
            + "programTemplateDay.getDayPosition(), originalStartDate))")
    @Mapping(target = "dayOfWeek", expression = "java(toDayOfWeekDto(ProgramTemplateWeeks.dayOfWeekOfPosition("
            + "programTemplateDay.getDayPosition(), originalStartDate)))")
    @Mapping(target = "parts", source = "programTemplateDay.parts")
    ProgramTemplateDayResponseDto toProgramTemplateDayResponseDto(
            final ProgramTemplateDay programTemplateDay,
            final LocalDate originalStartDate);

    default List<ProgramTemplateDayResponseDto> toProgramTemplateDayResponseDtos(
            final List<ProgramTemplateDay> programTemplateDays,
            final LocalDate originalStartDate) {
        return programTemplateDays.stream()
                .map(programTemplateDay -> toProgramTemplateDayResponseDto(programTemplateDay, originalStartDate))
                .toList();
    }

    DayOfWeekDto toDayOfWeekDto(final DayOfWeek dayOfWeek);

    ProgramTemplatePartResponseDto toProgramTemplatePartResponseDto(final ProgramTemplatePart programTemplatePart);

    @BeanMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    ProgramTemplateFilterDto toProgramTemplateFilterDto(final String name);

    ProgramTemplateSortField toProgramTemplateSortField(final ProgramTemplateSortFieldDto sortFieldDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "name", source = "content.name")
    @Mapping(target = "description", source = "content.description")
    @Mapping(target = "originalStartDate", source = "content.startDate")
    ProgramTemplate toProgramTemplate(
            final ProgramTemplateContent content,
            final String contentHash,
            final int dayCount,
            final int lastPosition);

    /**
     * Parts are added by the service through {@link ProgramTemplateDay#appendParts}, which also assigns their order.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "dayPosition", source = "contentDay.position")
    @Mapping(target = "programTemplate", source = "programTemplate")
    @Mapping(target = "parts", ignore = true)
    ProgramTemplateDay toProgramTemplateDay(
            final ProgramTemplateContentDay contentDay,
            final ProgramTemplate programTemplate);

    /**
     * The order is assigned by {@code ProgramTemplateDay#appendParts}.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "programTemplateDay", ignore = true)
    @Mapping(target = "orderIndex", ignore = true)
    ProgramTemplatePart toProgramTemplatePart(final ProgramTemplateContentPart contentPart);

    List<ProgramTemplatePart> toProgramTemplateParts(final List<ProgramTemplateContentPart> contentParts);

    ProgramTemplateContentPart toProgramTemplateContentPart(final ProgramTemplateDayFilePart dayFilePart);

    List<ProgramTemplateContentPart> toProgramTemplateContentParts(final List<ProgramTemplateDayFilePart> dayFileParts);
}
