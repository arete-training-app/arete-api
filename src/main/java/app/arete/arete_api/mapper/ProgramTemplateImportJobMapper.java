package app.arete.arete_api.mapper;

import app.arete.arete_api.api.model.ProgramTemplateCatalogEntryResponseDto;
import app.arete.arete_api.api.model.ProgramTemplateCatalogPageResponseDto;
import app.arete.arete_api.api.model.ProgramTemplateImportJobPageResponseDto;
import app.arete.arete_api.api.model.ProgramTemplateImportJobResponseDto;
import app.arete.arete_api.api.model.ProgramTemplateImportJobSortFieldDto;
import app.arete.arete_api.api.model.ProgramTemplateImportJobStatusDto;
import app.arete.arete_api.model.dto.programtemplate.ProgramTemplateCatalogEntry;
import app.arete.arete_api.model.dto.programtemplate.ProgramTemplateImportJobFilterDto;
import app.arete.arete_api.model.entity.ImportJob;
import app.arete.arete_api.util.enums.ImportJobSortField;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;
import org.springframework.data.domain.Page;

/**
 * A program template import job is an {@link ImportJob} of type {@code PROGRAM_TEMPLATE}; its result is the template.
 */
@Mapper(config = CentralMapperConfig.class, uses = DateTimeMapper.class)
public interface ProgramTemplateImportJobMapper {

    @Mapping(target = "programTemplateId", source = "resultId")
    ProgramTemplateImportJobResponseDto toProgramTemplateImportJobResponseDto(final ImportJob importJob);

    @Mapping(target = "page", source = "number")
    ProgramTemplateImportJobPageResponseDto toProgramTemplateImportJobPageResponseDto(
            final Page<ImportJob> importJobPage);

    @BeanMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    ProgramTemplateImportJobFilterDto toProgramTemplateImportJobFilterDto(
            final ProgramTemplateImportJobStatusDto status,
            final String catalogEntry);

    ImportJobSortField toImportJobSortField(final ProgramTemplateImportJobSortFieldDto sortFieldDto);

    /**
     * @param latestImportJob the entry's most recent job, or {@code null} if it was never imported
     */
    @Mapping(target = "name", source = "catalogEntry.name")
    @Mapping(target = "dayFileCount", source = "catalogEntry.dayFileCount")
    @Mapping(target = "latestImportJob", source = "latestImportJob")
    ProgramTemplateCatalogEntryResponseDto toProgramTemplateCatalogEntryResponseDto(
            final ProgramTemplateCatalogEntry catalogEntry,
            final ImportJob latestImportJob);

    @Mapping(target = "page", source = "number")
    ProgramTemplateCatalogPageResponseDto toProgramTemplateCatalogPageResponseDto(
            final Page<ProgramTemplateCatalogEntryResponseDto> catalogEntryPage);
}
