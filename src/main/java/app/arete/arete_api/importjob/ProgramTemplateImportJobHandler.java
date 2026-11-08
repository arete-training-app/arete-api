package app.arete.arete_api.importjob;

import app.arete.arete_api.exception.BadRequestException;
import app.arete.arete_api.model.dto.programtemplate.ProgramTemplateContent;
import app.arete.arete_api.programtemplate.ProgramTemplateCatalog;
import app.arete.arete_api.programtemplate.ProgramTemplateDayFileReader;
import app.arete.arete_api.service.ProgramTemplateService;
import app.arete.arete_api.util.enums.ImportJobType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Imports a catalog entry: reads its day files and creates the template. The entry is
 * resolved again here, because its folder may have changed or gone since the job was queued.
 */
@Component
@RequiredArgsConstructor
public class ProgramTemplateImportJobHandler implements ImportJobHandler {

    private final ProgramTemplateCatalog programTemplateCatalog;
    private final ProgramTemplateDayFileReader programTemplateDayFileReader;
    private final ProgramTemplateService programTemplateService;

    @Override
    public ImportJobType type() {
        return ImportJobType.PROGRAM_TEMPLATE;
    }

    @Override
    public Long handle(final String catalogEntry) {
        final Path entryDirectory = programTemplateCatalog.resolve(catalogEntry)
                .orElseThrow(() -> new BadRequestException(
                        "The catalog entry '%s' no longer exists".formatted(catalogEntry)));
        final ProgramTemplateContent content = programTemplateDayFileReader.read(catalogEntry, entryDirectory);
        return programTemplateService.createProgramTemplate(content).getId();
    }
}
