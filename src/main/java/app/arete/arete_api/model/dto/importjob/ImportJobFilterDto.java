package app.arete.arete_api.model.dto.importjob;

import app.arete.arete_api.util.enums.ImportJobStatus;
import app.arete.arete_api.util.enums.ImportJobType;

public record ImportJobFilterDto(
        ImportJobType type,
        ImportJobStatus status,
        String catalogEntry
) {
}
