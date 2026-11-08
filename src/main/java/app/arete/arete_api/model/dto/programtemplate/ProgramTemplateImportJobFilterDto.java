package app.arete.arete_api.model.dto.programtemplate;

import app.arete.arete_api.util.enums.ImportJobStatus;

public record ProgramTemplateImportJobFilterDto(
        ImportJobStatus status,
        String catalogEntry
) {
}
