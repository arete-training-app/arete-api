package app.arete.arete_api.mapper;

import app.arete.arete_api.api.model.ProgramPartRequestDto;
import app.arete.arete_api.api.model.ProgramPartResponseDto;
import app.arete.arete_api.model.entity.ProgramPart;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = CentralMapperConfig.class)
public interface ProgramPartMapper {

    ProgramPartResponseDto toProgramPartResponseDto(final ProgramPart programPart);

    /**
     * The order is assigned by {@code ProgramDay#addPart}, and a new part starts without feedback.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "programDay", ignore = true)
    @Mapping(target = "orderIndex", ignore = true)
    @Mapping(target = "completed", ignore = true)
    @Mapping(target = "resultNotes", ignore = true)
    ProgramPart toProgramPart(final ProgramPartRequestDto programPartRequestDto);
}
