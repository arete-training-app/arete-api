package app.arete.arete_api.controller;

import app.arete.arete_api.api.ProgramTemplateApi;
import app.arete.arete_api.api.model.ProgramFromTemplateRequestDto;
import app.arete.arete_api.api.model.ProgramResponseDto;
import app.arete.arete_api.api.model.ProgramTemplateCatalogPageResponseDto;
import app.arete.arete_api.api.model.ProgramTemplateDayResponseDto;
import app.arete.arete_api.api.model.ProgramTemplatePageResponseDto;
import app.arete.arete_api.api.model.ProgramTemplateResponseDto;
import app.arete.arete_api.api.model.ProgramTemplateSortFieldDto;
import app.arete.arete_api.api.model.SortDirectionDto;
import app.arete.arete_api.mapper.ProgramTemplateMapper;
import app.arete.arete_api.model.dto.programtemplate.ProgramTemplateFilterDto;
import app.arete.arete_api.service.ProgramService;
import app.arete.arete_api.service.ProgramTemplateImportJobService;
import app.arete.arete_api.service.ProgramTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Admin endpoints for program templates: the templates that exist (list, get, days, delete, create a program from
 * one) and the catalog of templates that can be imported. The import jobs have their own
 * {@link ProgramTemplateImportJobController}.
 */
@RestController
@RequiredArgsConstructor
public class ProgramTemplateController implements ProgramTemplateApi {

    // Matches the getProgram path in program.yaml
    private static final String PROGRAM_PATH = "/programs/{programId}";

    private final ProgramTemplateService programTemplateService;
    private final ProgramTemplateImportJobService programTemplateImportJobService;
    private final ProgramService programService;
    private final ProgramTemplateMapper programTemplateMapper;

    @Override
    public ResponseEntity<ProgramTemplatePageResponseDto> getProgramTemplates(
            final Integer page,
            final Integer size,
            final ProgramTemplateSortFieldDto sort,
            final SortDirectionDto direction,
            final String name) {
        final ProgramTemplateFilterDto programTemplateFilterDto = programTemplateMapper.toProgramTemplateFilterDto(name);
        return ResponseEntity.ok(
                programTemplateService.getProgramTemplates(page, size, sort, direction, programTemplateFilterDto));
    }

    @Override
    public ResponseEntity<ProgramTemplateResponseDto> getProgramTemplate(final Long programTemplateId) {
        return ResponseEntity.ok(programTemplateService.getProgramTemplateById(programTemplateId));
    }

    @Override
    public ResponseEntity<List<ProgramTemplateDayResponseDto>> getProgramTemplateDays(
            final Long programTemplateId,
            final Integer fromDay,
            final Integer toDay) {
        return ResponseEntity.ok(programTemplateService.getProgramTemplateDays(programTemplateId, fromDay, toDay));
    }

    @Override
    public ResponseEntity<Void> deleteProgramTemplate(final Long programTemplateId) {
        programTemplateService.deleteProgramTemplate(programTemplateId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ProgramResponseDto> createProgramFromTemplate(
            final Long programTemplateId,
            final ProgramFromTemplateRequestDto programFromTemplateRequestDto) {
        final ProgramResponseDto createdProgram =
                programService.createUserProgramFromTemplate(programTemplateId, programFromTemplateRequestDto);
        final URI programUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(PROGRAM_PATH)
                .buildAndExpand(createdProgram.getId())
                .toUri();
        return ResponseEntity.created(programUri).body(createdProgram);
    }

    @Override
    public ResponseEntity<ProgramTemplateCatalogPageResponseDto> getProgramTemplateCatalog(
            final Integer page,
            final Integer size,
            final String name) {
        return ResponseEntity.ok(programTemplateImportJobService.getProgramTemplateCatalog(page, size, name));
    }
}
