package app.arete.arete_api.model.dto.programtemplate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * One day file of a catalog entry: the workout of one date. The dates' order and gaps become the template's
 * positions and uncovered days. Produced by a separate converting service; the contract it follows is
 * {@code api/program-template-day-file.schema.json}, so keep both in sync.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProgramTemplateDayFile(
        @NotNull LocalDate date,
        @NotNull @Size(min = 1) List<@Valid @NotNull ProgramTemplateDayFilePart> parts
) {
}
