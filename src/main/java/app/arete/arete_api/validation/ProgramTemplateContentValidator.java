package app.arete.arete_api.validation;

import app.arete.arete_api.config.properties.ProgramProperties;
import app.arete.arete_api.config.properties.ProgramTemplateProperties;
import app.arete.arete_api.exception.BadRequestException;
import app.arete.arete_api.model.dto.programtemplate.ProgramTemplateContent;
import app.arete.arete_api.model.dto.programtemplate.ProgramTemplateContentDay;
import app.arete.arete_api.model.dto.programtemplate.ProgramTemplateContentPart;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The rules a template's content must follow whatever format it was read from. Run by the import worker before the
 * template is created; errors name the day and the file it came from.
 */
@Component
@RequiredArgsConstructor
public class ProgramTemplateContentValidator {

    private final ProgramTemplateProperties programTemplateProperties;
    private final ProgramProperties programProperties;

    /**
     * @throws BadRequestException if there are no days, a position is repeated or above the configured maximum, a
     *                             day has too many parts, or any text contains a blocked term
     */
    public void validate(final ProgramTemplateContent content) {
        validateDays(content.days());
        rejectBlockedTerms(content);
    }

    private void validateDays(final List<ProgramTemplateContentDay> days) {
        if (days.isEmpty()) {
            throw new BadRequestException("A template needs at least one day");
        }
        final int maxDays = programTemplateProperties.maxDays();
        if (days.size() > maxDays) {
            throw new BadRequestException("A template can have at most %d days".formatted(maxDays));
        }
        final Set<Integer> seenPositions = new HashSet<>();
        for (final ProgramTemplateContentDay day : days) {
            if (day.position() < 1 || day.position() > maxDays) {
                throw new BadRequestException("%s: day position %d is outside 1 to %d"
                        .formatted(describe(day), day.position(), maxDays));
            }
            if (!seenPositions.add(day.position())) {
                throw new BadRequestException("%s: day position %d appears more than once"
                        .formatted(describe(day), day.position()));
            }
            if (day.parts().size() > programProperties.maxPartsPerDay()) {
                throw new BadRequestException("%s has more than %d parts"
                        .formatted(describe(day), programProperties.maxPartsPerDay()));
            }
        }
    }

    private void rejectBlockedTerms(final ProgramTemplateContent content) {
        final List<String> blockedTerms = programTemplateProperties.blockedTerms().stream()
                .map(String::trim)
                .filter(term -> !term.isEmpty())
                .map(term -> term.toLowerCase(Locale.ROOT))
                .toList();
        if (blockedTerms.isEmpty()) {
            return;
        }
        requireNoBlockedTerm("name", content.name(), blockedTerms);
        requireNoBlockedTerm("description", content.description(), blockedTerms);
        for (final ProgramTemplateContentDay day : content.days()) {
            final String location = describe(day);
            for (final ProgramTemplateContentPart part : day.parts()) {
                requireNoBlockedTerm(location, part.title(), blockedTerms);
                requireNoBlockedTerm(location, part.description(), blockedTerms);
            }
        }
    }

    private static String describe(final ProgramTemplateContentDay day) {
        return day.sourceFile() == null
                ? "day " + day.position()
                : "day %d (%s)".formatted(day.position(), day.sourceFile());
    }

    private static void requireNoBlockedTerm(
            final String location,
            final String text,
            final List<String> blockedTerms) {
        findBlockedTerm(text, blockedTerms).ifPresent(term -> {
            throw new BadRequestException("The %s contains the blocked term '%s'".formatted(location, term));
        });
    }

    private static Optional<String> findBlockedTerm(final String text, final List<String> blockedTerms) {
        if (text == null) {
            return Optional.empty();
        }
        final String lowerCaseText = text.toLowerCase(Locale.ROOT);
        return blockedTerms.stream().filter(lowerCaseText::contains).findFirst();
    }
}
