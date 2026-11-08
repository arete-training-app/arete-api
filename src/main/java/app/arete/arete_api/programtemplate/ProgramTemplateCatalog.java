package app.arete.arete_api.programtemplate;

import app.arete.arete_api.config.properties.ProgramTemplateProperties;
import app.arete.arete_api.model.dto.programtemplate.ProgramTemplateCatalogEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The templates that can be imported: each subfolder of {@code arete.program-template.catalog.directory} is one
 * template, named after the folder, holding one file per day. Names are restricted and resolved inside the catalog
 * folder, so a name can never point at a file elsewhere on the server. Folders whose name doesn't match, such as a
 * {@code <Name>.tmp} folder the converting service is still writing, aren't entries. The layout is part of the
 * contract in {@code api/program-template-day-file.schema.json}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProgramTemplateCatalog {

    // Matches the catalogEntry pattern in program-template.yaml
    private static final Pattern ENTRY_NAME = Pattern.compile("^[A-Za-z0-9 _-]{1,100}$");
    private static final String DAY_FILE_EXTENSION = ".json";

    private final ProgramTemplateProperties programTemplateProperties;

    /**
     * Logs where the catalog is read from, because a relative path resolves against the directory the app was
     * started from.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void logCatalogDirectory() {
        final Path catalogDirectory = getCatalogDirectory();
        if (Files.isDirectory(catalogDirectory)) {
            log.info("Program template catalog: {}", catalogDirectory);
            return;
        }
        log.warn("Program template catalog: {} doesn't exist; the catalog stays empty until it's created",
                catalogDirectory);
    }

    /**
     * @return the catalog's entries ordered by name; empty if the catalog folder doesn't exist
     */
    public List<ProgramTemplateCatalogEntry> listEntries() {
        final Path catalogDirectory = getCatalogDirectory();
        if (!Files.isDirectory(catalogDirectory)) {
            log.warn("The program template catalog folder {} doesn't exist; the catalog is empty", catalogDirectory);
            return List.of();
        }
        try (Stream<Path> children = Files.list(catalogDirectory)) {
            return children
                    .filter(Files::isDirectory)
                    .map(directory -> directory.getFileName().toString())
                    .filter(name -> ENTRY_NAME.matcher(name).matches())
                    .sorted(Comparator.naturalOrder())
                    .map(name -> new ProgramTemplateCatalogEntry(name, listDayFiles(catalogDirectory.resolve(name)).size()))
                    .toList();
        } catch (final IOException exception) {
            throw new UncheckedIOException("Couldn't list the program template catalog", exception);
        }
    }

    /**
     * @param entryName the entry's name
     * @return the entry's folder, or empty if the name isn't a valid entry name or no such folder exists
     */
    public Optional<Path> resolve(final String entryName) {
        if (entryName == null || !ENTRY_NAME.matcher(entryName).matches()) {
            return Optional.empty();
        }
        final Path catalogDirectory = getCatalogDirectory();
        final Path entryDirectory = catalogDirectory.resolve(entryName).normalize();
        if (!entryDirectory.getParent().equals(catalogDirectory) || !Files.isDirectory(entryDirectory)) {
            return Optional.empty();
        }
        return Optional.of(entryDirectory);
    }

    /**
     * Only the {@code .json} files directly in the folder are day files: subfolders and other files are ignored, so
     * each template is one flat folder.
     *
     * @param entryDirectory a folder returned by {@link #resolve}
     * @return its day files ordered by file name
     */
    public List<Path> listDayFiles(final Path entryDirectory) {
        try (Stream<Path> files = Files.list(entryDirectory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(DAY_FILE_EXTENSION))
                    .sorted()
                    .toList();
        } catch (final IOException exception) {
            throw new UncheckedIOException("Couldn't list the day files of " + entryDirectory.getFileName(), exception);
        }
    }

    private Path getCatalogDirectory() {
        return programTemplateProperties.catalog().directory().toAbsolutePath().normalize();
    }
}
