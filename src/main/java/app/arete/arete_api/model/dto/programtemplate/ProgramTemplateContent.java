package app.arete.arete_api.model.dto.programtemplate;

import java.time.LocalDate;
import java.util.List;

/**
 * A template as the import pipeline sees it, whatever format it was read from: every source reader produces this,
 * and validation, deduplication and persistence only work on it.
 *
 * @param name        the template's name
 * @param description an optional description
 * @param startDate   the source's date of position 1
 * @param days        the covered days, in position order
 */
public record ProgramTemplateContent(
        String name,
        String description,
        LocalDate startDate,
        List<ProgramTemplateContentDay> days
) {
    public ProgramTemplateContent {
        days = List.copyOf(days);
    }
}
