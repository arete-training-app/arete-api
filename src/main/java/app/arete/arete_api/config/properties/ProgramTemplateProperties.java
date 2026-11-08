package app.arete.arete_api.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.util.List;

/**
 * @param blockedTerms texts (hosts, names) that must not appear in imported content, matched ignoring case; the
 *                     values come from the environment so the code and its config don't name any source
 * @param maxDays      the highest day position and the most days a template may have
 * @param rangeMaxDays the most positions one period read may span
 * @param catalog      where the templates that can be imported are kept
 */
@Validated
@ConfigurationProperties(prefix = "arete.program-template")
public record ProgramTemplateProperties(
        @NotNull List<String> blockedTerms,
        @Positive int maxDays,
        @Positive int rangeMaxDays,
        @Valid @NotNull Catalog catalog
) {

    /**
     * @param directory the catalog folder; each subfolder is one template, named after the folder, holding its day
     *                  files
     */
    public record Catalog(
            @NotNull Path directory
    ) {
    }
}
