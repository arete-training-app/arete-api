package app.arete.arete_api.programtemplate;

import app.arete.arete_api.exception.BadRequestException;
import app.arete.arete_api.util.ProgramTemplateContentHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Works out the content hash of a catalog entry's current day files, the same hash an import of the entry would
 * store, so the catalog can tell whether those days already exist as a template. Templates deliberately don't record
 * where they came from, so this is the only link between an entry and a template, and it is computed, never stored.
 * <p>
 * Reading an entry means reading every day file, and the catalog is polled while an import runs, so the hash is
 * cached per entry against a fingerprint of its files (name, size, last-modified time); a hit only reads file
 * attributes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProgramTemplateCatalogHasher {

    private final ProgramTemplateCatalog programTemplateCatalog;
    private final ProgramTemplateDayFileReader programTemplateDayFileReader;
    private final Map<String, CachedHash> cache = new ConcurrentHashMap<>();

    /**
     * @param entryName the catalog entry's name
     * @return the content hash of its current days, or empty if the entry is gone or its files can't be imported as
     *         they stand
     */
    public Optional<String> contentHash(final String entryName) {
        final Optional<Path> entryDirectory = programTemplateCatalog.resolve(entryName);
        if (entryDirectory.isEmpty()) {
            cache.remove(entryName);
            return Optional.empty();
        }
        final List<DayFileStamp> fingerprint;
        try {
            fingerprint = fingerprint(entryDirectory.get());
        } catch (final UncheckedIOException exception) {
            log.debug("Couldn't stat the day files of catalog entry '{}': {}", entryName, exception.getMessage());
            return Optional.empty();
        }
        final CachedHash cached = cache.get(entryName);
        if (cached != null && cached.fingerprint().equals(fingerprint)) {
            return cached.contentHash();
        }
        final Optional<String> contentHash = hash(entryName, entryDirectory.get());
        cache.put(entryName, new CachedHash(fingerprint, contentHash));
        return contentHash;
    }

    private Optional<String> hash(final String entryName, final Path entryDirectory) {
        try {
            return Optional.of(ProgramTemplateContentHasher.hashDays(
                    programTemplateDayFileReader.read(entryName, entryDirectory).days()));
        } catch (final BadRequestException | UncheckedIOException exception) {
            log.debug("Catalog entry '{}' can't be hashed: {}", entryName, exception.getMessage());
            return Optional.empty();
        }
    }

    private List<DayFileStamp> fingerprint(final Path entryDirectory) {
        return programTemplateCatalog.listDayFiles(entryDirectory).stream()
                .map(ProgramTemplateCatalogHasher::stamp)
                .toList();
    }

    private static DayFileStamp stamp(final Path dayFile) {
        try {
            return new DayFileStamp(
                    dayFile.getFileName().toString(),
                    Files.size(dayFile),
                    Files.getLastModifiedTime(dayFile).toMillis());
        } catch (final IOException exception) {
            throw new UncheckedIOException("Couldn't stat " + dayFile.getFileName(), exception);
        }
    }

    private record DayFileStamp(String name, long size, long lastModifiedMillis) {
    }

    private record CachedHash(List<DayFileStamp> fingerprint, Optional<String> contentHash) {
    }
}
