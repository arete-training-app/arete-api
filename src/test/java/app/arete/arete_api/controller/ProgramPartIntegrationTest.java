package app.arete.arete_api.controller;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

class ProgramPartIntegrationTest extends AbstractProgramIntegrationTest {

    private static final int MAX_PARTS_PER_DAY = 20;

    private final LocalDate date = LocalDate.now(ZoneOffset.UTC).plusDays(3);

    private Cookie owner;
    private long programId;

    @BeforeEach
    void createOwnerAndProgram() throws Exception {
        owner = registerAndGetAccessCookie();
        programId = createProgramAndGetId(owner);
    }

    @Test
    void theFirstPartPlansARestDayAndLaterPartsAreAppended() throws Exception {
        assertThat(getDay().getStatus()).isEqualTo(404);

        final MockHttpServletResponse created = createPart("Warm-up", "Mobility drills", 15);
        createPart("Intervals");

        assertThat(created.getStatus()).isEqualTo(201);
        final String part = created.getContentAsString();
        assertThat(JsonPath.<String>read(part, "$.title")).isEqualTo("Warm-up");
        assertThat(JsonPath.<String>read(part, "$.description")).isEqualTo("Mobility drills");
        assertThat(JsonPath.<Integer>read(part, "$.durationMinutes")).isEqualTo(15);
        assertThat(JsonPath.<Boolean>read(part, "$.completed")).isFalse();
        final String day = getDay().getContentAsString();
        assertThat(JsonPath.<String>read(day, "$.status")).isEqualTo("PLANNED");
        assertThat(readStrings(day, "$.parts[*].title")).containsExactly("Warm-up", "Intervals");
    }

    @Test
    void aDayHoldsAtMostTheConfiguredNumberOfParts() throws Exception {
        for (int index = 0; index < MAX_PARTS_PER_DAY; index++) {
            assertThat(createPart("Part " + index).getStatus()).isEqualTo(201);
        }

        final MockHttpServletResponse response = createPart("One too many");

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(readStrings(getDay().getContentAsString(), "$.parts[*].title")).hasSize(MAX_PARTS_PER_DAY);
    }

    @Test
    void updatingAPartKeepsItsPositionAndFeedback() throws Exception {
        createPart("Strength");
        final long conditioningId = createPartAndGetId("Conditioning");
        giveFeedback(conditioningId);

        final MockHttpServletResponse response = updatePart(conditioningId, "Intervals", "6 x 800 m", 45);

        assertThat(response.getStatus()).isEqualTo(200);
        final String day = getDay().getContentAsString();
        assertThat(readStrings(day, "$.parts[*].title")).containsExactly("Strength", "Intervals");
        assertThat(JsonPath.<String>read(day, "$.parts[1].description")).isEqualTo("6 x 800 m");
        assertThat(JsonPath.<Boolean>read(day, "$.parts[1].completed")).isTrue();
        assertThat(JsonPath.<String>read(day, "$.parts[1].resultNotes")).isEqualTo("Done");
    }

    @Test
    void deletingAPartClosesTheGapAndDeletingTheLastOneMakesARestDay() throws Exception {
        final long firstId = createPartAndGetId("A");
        final long secondId = createPartAndGetId("B");
        createPart("C");

        assertThat(deletePart(firstId).getStatus()).isEqualTo(204);

        assertThat(readStrings(getDay().getContentAsString(), "$.parts[*].title")).containsExactly("B", "C");
        createPart("D");
        assertThat(readStrings(getDay().getContentAsString(), "$.parts[*].title")).containsExactly("B", "C", "D");

        assertThat(deletePart(secondId).getStatus()).isEqualTo(204);
        for (final String title : new String[]{"C", "D"}) {
            final long partId = findPartId(title);
            assertThat(deletePart(partId).getStatus()).isEqualTo(204);
        }
        assertThat(getDay().getStatus()).isEqualTo(404);
    }

    @Test
    void reorderingSetsTheOrderOfTheParts() throws Exception {
        final long firstId = createPartAndGetId("A");
        final long secondId = createPartAndGetId("B");
        final long thirdId = createPartAndGetId("C");

        final MockHttpServletResponse response = reorderParts(thirdId, firstId, secondId);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(readStrings(response.getContentAsString(), "$.parts[*].title")).containsExactly("C", "A", "B");
        assertThat(readStrings(getDay().getContentAsString(), "$.parts[*].title")).containsExactly("C", "A", "B");
    }

    @Test
    void reorderingWithAnyOtherSetOfIdsIsBadRequest() throws Exception {
        final long firstId = createPartAndGetId("A");
        final long secondId = createPartAndGetId("B");

        assertThat(reorderParts(firstId).getStatus()).isEqualTo(400);
        assertThat(reorderParts(firstId, firstId).getStatus()).isEqualTo(400);
        assertThat(reorderParts(firstId, secondId, secondId + 1000).getStatus()).isEqualTo(400);
        assertThat(readStrings(getDay().getContentAsString(), "$.parts[*].title")).containsExactly("A", "B");
    }

    @Test
    void invalidPartsAreBadRequest() throws Exception {
        final MockHttpServletResponse response = createPartWithBody("""
                {"title":"","durationMinutes":0}
                """);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(getDay().getStatus()).isEqualTo(404);
    }

    @Test
    void unknownPartsAndRestDaysAreNotFound() throws Exception {
        final long partId = createPartAndGetId("A");
        final LocalDate restDay = date.plusDays(1);

        assertThat(updatePart(partId + 1000, "X", null, null).getStatus()).isEqualTo(404);
        assertThat(deletePart(partId + 1000).getStatus()).isEqualTo(404);
        assertThat(perform(withCsrf(delete("/programs/{programId}/days/{date}/parts/{partId}", programId, restDay, partId))
                .cookie(owner)).getStatus()).isEqualTo(404);
    }

    private MockHttpServletResponse createPart(final String title) throws Exception {
        return createPartWithBody("{\"title\":\"%s\"}".formatted(title));
    }

    private MockHttpServletResponse createPart(
            final String title,
            final String description,
            final int durationMinutes) throws Exception {
        return createPartWithBody("""
                {"title":"%s","description":"%s","durationMinutes":%d}
                """.formatted(title, description, durationMinutes));
    }

    private long createPartAndGetId(final String title) throws Exception {
        return JsonPath.<Number>read(createPart(title).getContentAsString(), "$.id").longValue();
    }

    private MockHttpServletResponse createPartWithBody(final String body) throws Exception {
        return perform(withCsrf(post("/programs/{programId}/days/{date}/parts", programId, date))
                .cookie(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private MockHttpServletResponse updatePart(
            final long partId,
            final String title,
            final String description,
            final Integer durationMinutes) throws Exception {
        final String body = description == null
                ? "{\"title\":\"%s\"}".formatted(title)
                : "{\"title\":\"%s\",\"description\":\"%s\",\"durationMinutes\":%d}"
                        .formatted(title, description, durationMinutes);
        return perform(withCsrf(put("/programs/{programId}/days/{date}/parts/{partId}", programId, date, partId))
                .cookie(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private MockHttpServletResponse deletePart(final long partId) throws Exception {
        return perform(withCsrf(delete("/programs/{programId}/days/{date}/parts/{partId}", programId, date, partId))
                .cookie(owner));
    }

    private MockHttpServletResponse reorderParts(final long... partIds) throws Exception {
        final String ids = Arrays.stream(partIds).mapToObj(Long::toString).collect(Collectors.joining(","));
        return perform(withCsrf(post("/programs/{programId}/days/{date}/parts/reorder", programId, date))
                .cookie(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"partIds\":[%s]}".formatted(ids)));
    }

    private void giveFeedback(final long partId) throws Exception {
        final MockHttpServletResponse response = perform(
                withCsrf(put("/programs/{programId}/days/{date}/feedback", programId, date))
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"SKIPPED","parts":[{"partId":%d,"completed":true,"resultNotes":"Done"}]}
                                """.formatted(partId)));
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private long findPartId(final String title) throws Exception {
        final String day = getDay().getContentAsString();
        return JsonPath.<java.util.List<Number>>read(day, "$.parts[?(@.title=='%s')].id".formatted(title))
                .getFirst().longValue();
    }

    private MockHttpServletResponse getDay() throws Exception {
        return perform(get("/programs/{programId}/days/{date}", programId, date).cookie(owner));
    }
}
