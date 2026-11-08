package app.arete.arete_api;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Writes catalog entries for tests into the catalog folder of the test profile.
 */
public final class TestProgramTemplateCatalog {

    public static final LocalDate FIRST_DATE = LocalDate.of(2026, 1, 1);

    private final Path catalogDirectory;

    public TestProgramTemplateCatalog(final Path catalogDirectory) {
        this.catalogDirectory = catalogDirectory;
    }

    /**
     * Writes one day file per title, named after its date, {@code dayOffsets[i]} days after {@link #FIRST_DATE}.
     */
    public void writeDays(final String entry, final String partTitlePrefix, final int... dayOffsets) {
        for (final int dayOffset : dayOffsets) {
            final LocalDate date = FIRST_DATE.plusDays(dayOffset);
            writeFile(entry, date + ".json", dayJson(date, partTitlePrefix + " " + dayOffset));
        }
    }

    public void writeFile(final String entry, final String fileName, final String content) {
        try {
            // The file name may include a subfolder
            final Path file = catalogDirectory.resolve(entry).resolve(fileName);
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public static String dayJson(final LocalDate date, final String... partTitles) {
        final String parts = Arrays.stream(partTitles)
                .map(title -> "{\"title\":\"%s\"}".formatted(title))
                .collect(Collectors.joining(","));
        return "{\"date\":\"%s\",\"parts\":[%s]}".formatted(date, parts);
    }
}
