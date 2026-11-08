package app.arete.arete_api.mapper;

import app.arete.arete_api.api.model.ProgramFromTemplateRequestDto;
import app.arete.arete_api.api.model.ProgramPageResponseDto;
import app.arete.arete_api.api.model.ProgramRequestDto;
import app.arete.arete_api.api.model.ProgramResponseDto;
import app.arete.arete_api.api.model.ProgramSortFieldDto;
import app.arete.arete_api.model.dto.program.ProgramFilterDto;
import app.arete.arete_api.model.entity.Program;
import app.arete.arete_api.model.entity.User;
import app.arete.arete_api.util.enums.ProgramSortField;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueMappingStrategy;
import org.springframework.data.domain.Page;

import java.time.LocalDate;

@Mapper(config = CentralMapperConfig.class, uses = DateTimeMapper.class)
public interface ProgramMapper {

    ProgramResponseDto toProgramResponseDto(final Program program);

    @Mapping(target = "page", source = "number")
    ProgramPageResponseDto toProgramPageResponseDto(final Page<Program> programPage);

    @BeanMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    ProgramFilterDto toProgramFilterDto(final String name, final LocalDate createdFrom, final LocalDate createdTo);

    ProgramSortField toProgramSortField(final ProgramSortFieldDto programSortFieldDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "name", source = "programRequestDto.name")
    @Mapping(target = "description", source = "programRequestDto.description")
    @Mapping(target = "owner", source = "owner")
    Program toProgram(final ProgramRequestDto programRequestDto, final User owner);

    /**
     * The description is passed separately because the service falls back to the template's.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "name", source = "programFromTemplateRequestDto.name")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "owner", source = "owner")
    Program toProgram(
            final ProgramFromTemplateRequestDto programFromTemplateRequestDto,
            final String description,
            final User owner);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "owner", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateProgramFromDto(final ProgramRequestDto programRequestDto, @MappingTarget final Program program);
}
