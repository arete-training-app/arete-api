package app.arete.arete_api.service.implementations;

import app.arete.arete_api.api.model.ProgramTemplateCatalogEntryResponseDto;
import app.arete.arete_api.api.model.ProgramTemplateCatalogPageResponseDto;
import app.arete.arete_api.api.model.ProgramTemplateImportJobPageResponseDto;
import app.arete.arete_api.api.model.ProgramTemplateImportJobRequestDto;
import app.arete.arete_api.api.model.ProgramTemplateImportJobResponseDto;
import app.arete.arete_api.api.model.ProgramTemplateImportJobSortFieldDto;
import app.arete.arete_api.api.model.SortDirectionDto;
import app.arete.arete_api.exception.NotFoundException;
import app.arete.arete_api.mapper.PaginationMapper;
import app.arete.arete_api.mapper.ProgramTemplateImportJobMapper;
import app.arete.arete_api.model.dto.importjob.ImportJobFilterDto;
import app.arete.arete_api.model.dto.programtemplate.ProgramTemplateCatalogEntry;
import app.arete.arete_api.model.dto.programtemplate.ProgramTemplateImportJobFilterDto;
import app.arete.arete_api.model.entity.ImportJob;
import app.arete.arete_api.programtemplate.ProgramTemplateCatalog;
import app.arete.arete_api.repository.ImportJobRepository;
import app.arete.arete_api.service.ImportJobService;
import app.arete.arete_api.service.ProgramTemplateImportJobService;
import app.arete.arete_api.specification.ImportJobSpecifications;
import app.arete.arete_api.util.PaginationUtils;
import app.arete.arete_api.util.enums.ImportJobType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramTemplateImportJobServiceImpl implements ProgramTemplateImportJobService {

    private static final String IMPORT_JOB_NOT_FOUND_DETAIL = "Program template import job not found";
    private static final String CATALOG_ENTRY_NOT_FOUND_DETAIL = "Catalog entry not found";

    private final ProgramTemplateCatalog programTemplateCatalog;
    private final ImportJobRepository importJobRepository;
    private final ImportJobSpecifications importJobSpecifications;
    private final ProgramTemplateImportJobMapper programTemplateImportJobMapper;
    private final PaginationMapper paginationMapper;
    private final ImportJobService importJobService;

    /**
     * The catalog is a folder listing, so it is filtered and paged in memory; it holds tens of entries, not
     * thousands.
     */
    @Override
    @Transactional(readOnly = true)
    public ProgramTemplateCatalogPageResponseDto getProgramTemplateCatalog(
            final int page,
            final int size,
            final String name) {
        final List<ProgramTemplateCatalogEntry> entries = programTemplateCatalog.listEntries().stream()
                .filter(entry -> name == null || containsIgnoringCase(entry.name(), name))
                .toList();
        log.debug("Listing the catalog: page {}, size {}, name filter {}, {} matching entries",
                page, size, name, entries.size());
        final Pageable pageable = PageRequest.of(page, size);
        final int fromIndex = (int) Math.min(pageable.getOffset(), entries.size());
        final int toIndex = Math.min(fromIndex + size, entries.size());
        final List<ProgramTemplateCatalogEntryResponseDto> pageContent = entries.subList(fromIndex, toIndex).stream()
                .map(entry -> programTemplateImportJobMapper.toProgramTemplateCatalogEntryResponseDto(
                        entry,
                        importJobRepository
                                .findFirstByTypeAndCatalogEntryOrderByIdDesc(ImportJobType.PROGRAM_TEMPLATE, entry.name())
                                .orElse(null)))
                .toList();
        return programTemplateImportJobMapper.toProgramTemplateCatalogPageResponseDto(
                new PageImpl<>(pageContent, pageable, entries.size()));
    }

    @Override
    @Transactional
    public ProgramTemplateImportJobResponseDto enqueueImportJob(
            final ProgramTemplateImportJobRequestDto importJobRequestDto) {
        final String catalogEntry = importJobRequestDto.getCatalogEntry();
        programTemplateCatalog.resolve(catalogEntry)
                .orElseThrow(() -> new NotFoundException(CATALOG_ENTRY_NOT_FOUND_DETAIL));
        final ImportJob importJob = importJobService.enqueue(ImportJobType.PROGRAM_TEMPLATE, catalogEntry);
        return programTemplateImportJobMapper.toProgramTemplateImportJobResponseDto(importJob);
    }

    @Override
    @Transactional(readOnly = true)
    public ProgramTemplateImportJobPageResponseDto getProgramTemplateImportJobs(
            final int page,
            final int size,
            final ProgramTemplateImportJobSortFieldDto sort,
            final SortDirectionDto direction,
            final ProgramTemplateImportJobFilterDto programTemplateImportJobFilterDto) {
        log.debug("Listing program template import jobs: page {}, size {}, sort {} {}, filter {}",
                page, size, sort, direction, programTemplateImportJobFilterDto);
        final Pageable pageable = PaginationUtils.toPageRequest(
                page,
                size,
                programTemplateImportJobMapper.toImportJobSortField(sort).getValue(),
                paginationMapper.toSortDirection(direction));
        final Specification<ImportJob> specification = importJobSpecifications.buildFilters(new ImportJobFilterDto(
                ImportJobType.PROGRAM_TEMPLATE,
                programTemplateImportJobFilterDto.status(),
                programTemplateImportJobFilterDto.catalogEntry()));
        return programTemplateImportJobMapper.toProgramTemplateImportJobPageResponseDto(
                importJobRepository.findAll(specification, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public ProgramTemplateImportJobResponseDto getProgramTemplateImportJob(final Long importJobId) {
        log.debug("Getting program template import job {}", importJobId);
        return importJobRepository.findByIdAndType(importJobId, ImportJobType.PROGRAM_TEMPLATE)
                .map(programTemplateImportJobMapper::toProgramTemplateImportJobResponseDto)
                .orElseThrow(() -> new NotFoundException(IMPORT_JOB_NOT_FOUND_DETAIL));
    }

    private static boolean containsIgnoringCase(final String text, final String part) {
        return text.toLowerCase(Locale.ROOT).contains(part.toLowerCase(Locale.ROOT));
    }
}
