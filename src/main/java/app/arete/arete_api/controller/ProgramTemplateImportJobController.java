package app.arete.arete_api.controller;

import app.arete.arete_api.api.ProgramTemplateImportJobApi;
import app.arete.arete_api.api.model.ProgramTemplateImportJobPageResponseDto;
import app.arete.arete_api.api.model.ProgramTemplateImportJobRequestDto;
import app.arete.arete_api.api.model.ProgramTemplateImportJobResponseDto;
import app.arete.arete_api.api.model.ProgramTemplateImportJobSortFieldDto;
import app.arete.arete_api.api.model.ProgramTemplateImportJobStatusDto;
import app.arete.arete_api.api.model.SortDirectionDto;
import app.arete.arete_api.mapper.ProgramTemplateImportJobMapper;
import app.arete.arete_api.model.dto.programtemplate.ProgramTemplateImportJobFilterDto;
import app.arete.arete_api.service.ProgramTemplateImportJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * Admin endpoints for the import jobs that create program templates from catalog entries in the background: start
 * one, list them, and follow one until its template is ready.
 */
@RestController
@RequiredArgsConstructor
public class ProgramTemplateImportJobController implements ProgramTemplateImportJobApi {

    // Matches the getProgramTemplateImportJob path in program-template.yaml
    private static final String IMPORT_JOB_PATH = "/admin/program-templates/import-jobs/{importJobId}";

    private final ProgramTemplateImportJobService programTemplateImportJobService;
    private final ProgramTemplateImportJobMapper programTemplateImportJobMapper;

    @Override
    public ResponseEntity<ProgramTemplateImportJobResponseDto> importProgramTemplateJob(
            final ProgramTemplateImportJobRequestDto programTemplateImportJobRequestDto) {
        final ProgramTemplateImportJobResponseDto queuedImportJob =
                programTemplateImportJobService.enqueueImportJob(programTemplateImportJobRequestDto);
        final URI importJobUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(IMPORT_JOB_PATH)
                .buildAndExpand(queuedImportJob.getId())
                .toUri();
        return ResponseEntity.accepted().location(importJobUri).body(queuedImportJob);
    }

    @Override
    public ResponseEntity<ProgramTemplateImportJobPageResponseDto> getProgramTemplateImportJobs(
            final Integer page,
            final Integer size,
            final ProgramTemplateImportJobSortFieldDto sort,
            final SortDirectionDto direction,
            final ProgramTemplateImportJobStatusDto status,
            final String catalogEntry) {
        final ProgramTemplateImportJobFilterDto programTemplateImportJobFilterDto =
                programTemplateImportJobMapper.toProgramTemplateImportJobFilterDto(status, catalogEntry);
        return ResponseEntity.ok(programTemplateImportJobService.getProgramTemplateImportJobs(
                page, size, sort, direction, programTemplateImportJobFilterDto));
    }

    @Override
    public ResponseEntity<ProgramTemplateImportJobResponseDto> getProgramTemplateImportJob(final Long importJobId) {
        return ResponseEntity.ok(programTemplateImportJobService.getProgramTemplateImportJob(importJobId));
    }
}
