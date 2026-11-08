package app.arete.arete_api.model.dto.programtemplate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * One part of a {@link ProgramTemplateDayFile}. The limits match the {@code program_template_part} columns.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProgramTemplateDayFilePart(
        @NotBlank @Size(max = 150) String title,
        @Size(max = 4000) String description,
        @Min(1) @Max(1440) Integer durationMinutes
) {
}
