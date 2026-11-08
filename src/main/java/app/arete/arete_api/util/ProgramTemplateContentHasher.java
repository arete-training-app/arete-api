package app.arete.arete_api.util;

import app.arete.arete_api.model.dto.programtemplate.ProgramTemplateContentDay;
import app.arete.arete_api.model.dto.programtemplate.ProgramTemplateContentPart;
import lombok.experimental.UtilityClass;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

@UtilityClass
public class ProgramTemplateContentHasher {

    private static final String DIGEST_ALGORITHM = "SHA-256";
    private static final char FIELD_SEPARATOR = '\u001f';
    private static final char RECORD_SEPARATOR = '\u001e';

    /**
     * Hashes the workout content of the days: positions, part order, titles, descriptions and durations. The
     * template's name and description and the source file names are left out, so the same days under another name
     * are still the same content.
     */
    public static String hashDays(final List<ProgramTemplateContentDay> days) {
        final StringBuilder content = new StringBuilder();
        days.stream()
                .sorted(Comparator.comparingInt(ProgramTemplateContentDay::position))
                .forEach(day -> {
                    content.append(day.position()).append(RECORD_SEPARATOR);
                    for (final ProgramTemplateContentPart part : day.parts()) {
                        content.append(part.title()).append(FIELD_SEPARATOR)
                                .append(part.description() == null ? "" : part.description())
                                .append(FIELD_SEPARATOR)
                                .append(part.durationMinutes() == null ? "" : part.durationMinutes())
                                .append(RECORD_SEPARATOR);
                    }
                });
        try {
            final MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(content.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException(DIGEST_ALGORITHM + " is required by the Java platform", exception);
        }
    }
}
