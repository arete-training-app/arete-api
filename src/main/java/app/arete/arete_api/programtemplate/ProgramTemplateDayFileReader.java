package app.arete.arete_api.programtemplate;

import app.arete.arete_api.config.properties.ProgramTemplateProperties;
import app.arete.arete_api.exception.BadRequestException;
import app.arete.arete_api.mapper.ProgramTemplateMapper;
import app.arete.arete_api.model.dto.programtemplate.ProgramTemplateContent;
import app.arete.arete_api.model.dto.programtemplate.ProgramTemplateContentDay;
import app.arete.arete_api.model.dto.programtemplate.ProgramTemplateDayFile;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Reads the day files of a catalog entry, the only format this app accepts (providers' data is converted by a
 * separate service; see {@code api/program-template-day-file.schema.json}): one {@link ProgramTemplateDayFile} per
 * {@code .json} file. The earliest date becomes position 1 and every other date its offset from it, so dates without
 * a file stay uncovered.
 */
@Component
@RequiredArgsConstructor
public class ProgramTemplateDayFileReader {

    private static final String VIOLATION_SEPARATOR = "; ";

    private final ProgramTemplateCatalog programTemplateCatalog;
    private final ProgramTemplateProperties programTemplateProperties;
    private final ProgramTemplateMapper programTemplateMapper;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    /**
     * @param name           the template's name
     * @param entryDirectory the catalog entry's folder
     * @return the template's content, with days in position order
     * @throws BadRequestException  if a file isn't a valid day file; the message names the file
     * @throws UncheckedIOException if a file can't be read; worth retrying
     */
    public ProgramTemplateContent read(final String name, final Path entryDirectory) {
        final List<Path> dayFilePaths = programTemplateCatalog.listDayFiles(entryDirectory);
        if (dayFilePaths.isEmpty()) {
            throw new BadRequestException("The catalog entry '%s' has no day files".formatted(name));
        }
        final Map<LocalDate, DatedDay> daysByDate = new TreeMap<>();
        for (final Path dayFilePath : dayFilePaths) {
            final DatedDay day = new DatedDay(dayFilePath.getFileName().toString(), readDayFile(dayFilePath));
            final DatedDay sameDate = daysByDate.putIfAbsent(day.file().date(), day);
            if (sameDate != null) {
                throw new BadRequestException("%s and %s both have the date %s"
                        .formatted(sameDate.fileName(), day.fileName(), day.file().date()));
            }
        }
        // A TreeMap is ordered by date, so its first key is position 1
        final LocalDate startDate = daysByDate.keySet().iterator().next();
        return new ProgramTemplateContent(name, null, startDate, toPositionedDays(daysByDate));
    }

    private ProgramTemplateDayFile readDayFile(final Path dayFilePath) {
        final String fileName = dayFilePath.getFileName().toString();
        final byte[] content;
        try {
            content = Files.readAllBytes(dayFilePath);
        } catch (final IOException exception) {
            throw new UncheckedIOException("Couldn't read " + fileName, exception);
        }
        final ProgramTemplateDayFile dayFile;
        try {
            dayFile = objectMapper.readValue(content, ProgramTemplateDayFile.class);
        } catch (final JacksonException exception) {
            throw new BadRequestException("%s isn't a valid day file: %s"
                    .formatted(fileName, exception.getOriginalMessage()));
        }
        if (dayFile == null) {
            throw new BadRequestException(fileName + " is empty");
        }
        final Set<ConstraintViolation<ProgramTemplateDayFile>> violations = validator.validate(dayFile);
        if (!violations.isEmpty()) {
            throw new BadRequestException("%s is invalid: %s".formatted(fileName, describe(violations)));
        }
        return dayFile;
    }

    /**
     * The map is ordered by date, so its first day is the earliest one.
     */
    private List<ProgramTemplateContentDay> toPositionedDays(final Map<LocalDate, DatedDay> daysByDate) {
        final DatedDay firstDay = daysByDate.values().iterator().next();
        final int maxDays = programTemplateProperties.maxDays();
        return daysByDate.values().stream()
                .map(day -> {
                    final long offset = ChronoUnit.DAYS.between(firstDay.file().date(), day.file().date());
                    if (offset >= maxDays) {
                        throw new BadRequestException("%s is %d days after the first day (%s); a template spans at most %d days"
                                .formatted(day.fileName(), offset, firstDay.fileName(), maxDays));
                    }
                    return new ProgramTemplateContentDay(
                            (int) offset + 1,
                            day.fileName(),
                            programTemplateMapper.toProgramTemplateContentParts(day.file().parts()));
                })
                .toList();
    }

    private static String describe(final Set<ConstraintViolation<ProgramTemplateDayFile>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .sorted()
                .collect(Collectors.joining(VIOLATION_SEPARATOR));
    }

    private record DatedDay(
            String fileName,
            ProgramTemplateDayFile file
    ) {
    }
}
