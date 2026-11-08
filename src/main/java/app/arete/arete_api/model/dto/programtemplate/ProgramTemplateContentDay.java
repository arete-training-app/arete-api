package app.arete.arete_api.model.dto.programtemplate;

import java.util.List;

/**
 * @param position   1-based offset from the template's first day
 * @param sourceFile the file the day was read from, so errors can point at it; never stored
 * @param parts      the day's parts, in order
 */
public record ProgramTemplateContentDay(
        int position,
        String sourceFile,
        List<ProgramTemplateContentPart> parts
) {
    public ProgramTemplateContentDay {
        parts = List.copyOf(parts);
    }
}
