package app.arete.arete_api.controller;

import app.arete.arete_api.TestProgramTemplateCatalog;
import app.arete.arete_api.config.properties.ProgramTemplateProperties;
import app.arete.arete_api.importjob.ImportJobWorker;
import app.arete.arete_api.util.enums.ImportJobType;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static app.arete.arete_api.TestProgramTemplateCatalog.FIRST_DATE;
import static app.arete.arete_api.TestProgramTemplateCatalog.dayJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@TestPropertySource(properties = "arete.program-template.blocked-terms=forbidden-term")
class ProgramTemplateIntegrationTest extends AbstractProgramIntegrationTest {

    private static final int MAX_PARTS_PER_DAY = 20;
    private static final int MAX_DAYS = 366;
    private static final int RANGE_MAX_DAYS = 92;
    private static final String IMPORT_JOBS_PATH = "/admin/program-templates/import-jobs";
    private static final String CATALOG_PATH = "/admin/program-templates/catalog";

    @Autowired
    private ImportJobWorker importJobWorker;

    @Autowired
    private ProgramTemplateProperties programTemplateProperties;

    private TestProgramTemplateCatalog catalog;
    private Cookie admin;
    // Makes the entries and days of every test different, because identical days can only be imported once
    private String marker;

    @BeforeEach
    void setUp() throws Exception {
        catalog = new TestProgramTemplateCatalog(programTemplateProperties.catalog().directory());
        admin = registerAdminAndGetAccessCookie();
        marker = UUID.randomUUID().toString();
    }

    @Test
    void anImportJobCreatesTheTemplateFromTheCatalogEntryWhenTheWorkerRuns() throws Exception {
        final String entry = entry("Strength Block");
        catalog.writeDays(entry, "Squat " + marker, 0, 1, 3, 7);

        final MockHttpServletResponse queued = queueImportJob(entry);

        assertThat(queued.getStatus()).isEqualTo(202);
        final long importJobId = JsonPath.<Number>read(queued.getContentAsString(), "$.id").longValue();
        assertThat(queued.getHeader(HttpHeaders.LOCATION)).endsWith(IMPORT_JOBS_PATH + "/" + importJobId);
        assertThat(JsonPath.<String>read(queued.getContentAsString(), "$.status")).isEqualTo("PENDING");
        assertThat(JsonPath.<String>read(queued.getContentAsString(), "$.catalogEntry")).isEqualTo(entry);

        drainQueue();

        final String job = getImportJob(importJobId).getContentAsString();
        assertThat(JsonPath.<String>read(job, "$.status")).isEqualTo("COMPLETED");
        assertThat(JsonPath.<Integer>read(job, "$.attempts")).isEqualTo(1);
        assertThat(JsonPath.<String>read(job, "$.completedAt")).isNotNull();
        final long templateId = JsonPath.<Number>read(job, "$.programTemplateId").longValue();
        final String template = perform(get("/admin/program-templates/" + templateId).cookie(admin))
                .getContentAsString();
        assertThat(JsonPath.<String>read(template, "$.name")).isEqualTo(entry);
        assertThat(JsonPath.<Integer>read(template, "$.dayCount")).isEqualTo(4);
        assertThat(JsonPath.<Integer>read(template, "$.lastPosition")).isEqualTo(8);
        assertThat(JsonPath.<Integer>read(template, "$.weekCount")).isEqualTo(2);

        final String days = getDays(templateId, 1, 14).getContentAsString();

        assertThat(JsonPath.<List<Integer>>read(days, "$[*].position")).containsExactly(1, 2, 4, 8);
        // FIRST_DATE is a Thursday, so the source's first Monday-based week holds positions 1 to 4
        assertThat(JsonPath.<List<Integer>>read(days, "$[*].week")).containsExactly(1, 1, 1, 2);
        assertThat(JsonPath.<List<String>>read(days, "$[*].dayOfWeek"))
                .containsExactly("THURSDAY", "FRIDAY", "SUNDAY", "THURSDAY");
        assertThat(JsonPath.<String>read(days, "$[0].parts[0].title")).isEqualTo("Squat " + marker + " 0");
    }

    @Test
    void partsKeepTheirOrderAndDurations() throws Exception {
        final String entry = entry("Ordered");
        catalog.writeFile(entry, "day.json", """
                {"date":"%s","parts":[{"title":"C %2$s"},{"title":"A %2$s"},{"title":"B %2$s","durationMinutes":20}]}
                """.formatted(FIRST_DATE, marker));

        final long templateId = importAndGetTemplateId(entry);
        final String days = getDays(templateId, 1, 1).getContentAsString();

        assertThat(JsonPath.<List<String>>read(days, "$[0].parts[*].title"))
                .containsExactly("C " + marker, "A " + marker, "B " + marker);
        assertThat(JsonPath.<Integer>read(days, "$[0].parts[2].durationMinutes")).isEqualTo(20);
    }

    @Test
    void aPeriodOnlyReturnsDaysInsideItsRange() throws Exception {
        final String entry = entry("Periods");
        catalog.writeDays(entry, marker, 0, 9, 19);

        final long templateId = importAndGetTemplateId(entry);

        assertThat(JsonPath.<List<Integer>>read(getDays(templateId, 5, 15).getContentAsString(), "$[*].position"))
                .containsExactly(10);
        assertThat(JsonPath.<List<Integer>>read(getDays(templateId, 11, 19).getContentAsString(), "$[*].position"))
                .isEmpty();
    }

    @Test
    void anUnknownEntryIsNotFoundAndAnInvalidNameIsRejected() throws Exception {
        assertThat(queueImportJob(entry("Missing")).getStatus()).isEqualTo(404);
        assertThat(queueImportJob("../outside").getStatus()).isEqualTo(400);
        assertThat(perform(withCsrf(post(IMPORT_JOBS_PATH))
                .cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")).getStatus()).isEqualTo(400);
    }

    @Test
    void anEntryCantBeQueuedTwiceAtOnceAndItsDaysCantBeImportedTwice() throws Exception {
        final String entry = entry("Twice");
        catalog.writeDays(entry, marker, 0, 1);

        assertThat(queueImportJob(entry).getStatus()).isEqualTo(202);
        assertThat(queueImportJob(entry).getStatus()).isEqualTo(409);
        drainQueue();

        assertThat(importAndGetError(entry)).contains("already imported as program template");
    }

    @Test
    void theSameDaysUnderAnotherEntryFailAsADuplicate() throws Exception {
        final String original = entry("Original");
        final String copy = entry("Copy");
        catalog.writeDays(original, marker, 0, 2);
        catalog.writeDays(copy, marker, 0, 2);
        importAndGetTemplateId(original);

        assertThat(importAndGetError(copy)).contains("already imported as program template");
    }

    @Test
    void aBrokenFileFailsTheJobWithoutARetryAndNamesTheFile() throws Exception {
        final String entry = entry("Broken");
        catalog.writeDays(entry, marker, 0);
        catalog.writeFile(entry, "broken.json", "{ not json");

        final String importJob = importAndGetJob(entry);

        assertThat(JsonPath.<String>read(importJob, "$.status")).isEqualTo("FAILED");
        assertThat(JsonPath.<Integer>read(importJob, "$.attempts")).isEqualTo(1);
        assertThat(JsonPath.<String>read(importJob, "$.error")).contains("broken.json");
    }

    @Test
    void aDayWithoutPartsIsRejected() throws Exception {
        final String entry = entry("No parts");
        catalog.writeFile(entry, "empty-day.json", "{\"date\":\"%s\",\"parts\":[]}".formatted(FIRST_DATE));

        assertThat(importAndGetError(entry)).contains("empty-day.json").contains("parts");
    }

    @Test
    void twoFilesWithTheSameDateAreRejected() throws Exception {
        final String entry = entry("Same date");
        catalog.writeFile(entry, "a.json", dayJson(FIRST_DATE, "A " + marker));
        catalog.writeFile(entry, "b.json", dayJson(FIRST_DATE, "B " + marker));

        assertThat(importAndGetError(entry)).contains("a.json").contains("b.json").contains(FIRST_DATE.toString());
    }

    @Test
    void aTemplateCanSpanAtMostTheMaximumNumberOfDays() throws Exception {
        final String longest = entry("Longest");
        final String tooLong = entry("Too long");
        catalog.writeDays(longest, marker + " longest", 0, MAX_DAYS - 1);
        catalog.writeDays(tooLong, marker + " too long", 0, MAX_DAYS);

        importAndGetTemplateId(longest);

        assertThat(importAndGetError(tooLong)).contains(FIRST_DATE.plusDays(MAX_DAYS) + ".json");
    }

    @Test
    void aDayWithTooManyPartsIsRejected() throws Exception {
        final String entry = entry("Crowded");
        final String[] titles = IntStream.rangeClosed(1, MAX_PARTS_PER_DAY + 1)
                .mapToObj(index -> "Part %d %s".formatted(index, marker))
                .toArray(String[]::new);
        catalog.writeFile(entry, "crowded.json", dayJson(FIRST_DATE, titles));

        assertThat(importAndGetError(entry)).contains("crowded.json").contains("more than " + MAX_PARTS_PER_DAY);
    }

    @Test
    void contentWithABlockedTermIsRejectedIgnoringCase() throws Exception {
        final String entry = entry("Blocked");
        catalog.writeFile(entry, "day.json", """
                {"date":"%s","parts":[{"title":"Squat %s","description":"See FORBIDDEN-Term.example/video"}]}
                """.formatted(FIRST_DATE.plusDays(2), marker));

        assertThat(importAndGetError(entry)).contains("day 1 (day.json)").contains("forbidden-term");
    }

    @Test
    void aBlockedTermInTheEntryNameIsRejected() throws Exception {
        final String entry = entry("My forbidden-term plan");
        catalog.writeDays(entry, marker, 0);

        assertThat(importAndGetError(entry)).contains("name").contains("forbidden-term");
    }

    @Test
    void anEntryWithoutDayFilesIsRejected() throws Exception {
        final String entry = entry("Notes only");
        catalog.writeFile(entry, "notes.txt", "not a day file");

        assertThat(importAndGetError(entry)).contains("no day files");
    }

    @Test
    void theCatalogListsEntriesWithTheirDayFilesAndLatestImportJob() throws Exception {
        final String token = "Zq" + marker.substring(0, 8);
        catalog.writeDays(token + " Alpha", marker + " alpha", 0, 1);
        catalog.writeFile(token + " Alpha", "notes.txt", "ignored, not a day file");
        catalog.writeDays(token + " Bravo", marker + " bravo", 0);
        final long templateId = importAndGetTemplateId(token + " Alpha");

        final String byName = listCatalog("name", token.toLowerCase()).getContentAsString();
        final String firstPage = listCatalog("name", token, "size", "1").getContentAsString();

        assertThat(JsonPath.<List<String>>read(byName, "$.content[*].name"))
                .containsExactly(token + " Alpha", token + " Bravo");
        assertThat(JsonPath.<List<Integer>>read(byName, "$.content[*].dayFileCount")).containsExactly(2, 1);
        assertThat(JsonPath.<String>read(byName, "$.content[0].latestImportJob.status")).isEqualTo("COMPLETED");
        assertThat(JsonPath.<Number>read(byName, "$.content[0].latestImportJob.programTemplateId").longValue())
                .isEqualTo(templateId);
        assertThat(JsonPath.<Map<String, Object>>read(byName, "$.content[1]").get("latestImportJob")).isNull();
        assertThat(JsonPath.<List<String>>read(firstPage, "$.content[*].name")).containsExactly(token + " Alpha");
        assertThat(JsonPath.<Number>read(firstPage, "$.totalElements").longValue()).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(firstPage, "$.totalPages")).isEqualTo(2);
    }

    @Test
    void filesInSubfoldersOfAnEntryAreIgnored() throws Exception {
        final String entry = entry("Nested");
        catalog.writeDays(entry, marker, 10, 11);
        // Earlier dates, which would shift every position if they were read
        catalog.writeFile(entry, "old_free_trial/trial.json", dayJson(FIRST_DATE, "Trial " + marker));

        final String catalogPage = listCatalog("name", entry).getContentAsString();
        final long templateId = importAndGetTemplateId(entry);

        assertThat(JsonPath.<List<Integer>>read(catalogPage, "$.content[*].dayFileCount")).containsExactly(2);
        assertThat(JsonPath.<List<Integer>>read(getDays(templateId, 1, 30).getContentAsString(), "$[*].position"))
                .containsExactly(1, 2);
    }

    @Test
    void aFolderStillBeingWrittenIsNotACatalogEntry() throws Exception {
        final String entry = entry("Incoming");
        catalog.writeDays(entry + ".tmp", marker, 0);

        assertThat(JsonPath.<List<String>>read(listCatalog("name", entry).getContentAsString(), "$.content"))
                .isEmpty();
        assertThat(queueImportJob(entry).getStatus()).isEqualTo(404);
    }

    @Test
    void templateListIsPaginatedSortedAndFilteredByName() throws Exception {
        final String token = "Zq" + marker.substring(0, 8);
        catalog.writeDays(token + " Charlie", marker + " charlie", 0, 1, 2);
        catalog.writeDays(token + " Alpha", marker + " alpha", 0);
        catalog.writeDays(token + " Bravo", marker + " bravo", 0, 1);
        queueImportJob(token + " Charlie");
        queueImportJob(token + " Alpha");
        queueImportJob(token + " Bravo");
        drainQueue();

        final String byName = listTemplates("name", token.toLowerCase(), "sort", "NAME", "direction", "ASC")
                .getContentAsString();
        final String byDayCount = listTemplates("name", token, "sort", "DAY_COUNT", "direction", "DESC")
                .getContentAsString();
        final String firstPage = listTemplates("name", token, "sort", "NAME", "direction", "ASC", "size", "2")
                .getContentAsString();

        assertThat(JsonPath.<List<String>>read(byName, "$.content[*].name"))
                .containsExactly(token + " Alpha", token + " Bravo", token + " Charlie");
        assertThat(JsonPath.<List<Integer>>read(byDayCount, "$.content[*].dayCount")).containsExactly(3, 2, 1);
        assertThat(JsonPath.<List<String>>read(firstPage, "$.content[*].name"))
                .containsExactly(token + " Alpha", token + " Bravo");
        assertThat(JsonPath.<Number>read(firstPage, "$.totalElements").longValue()).isEqualTo(3);
        assertThat(JsonPath.<Integer>read(firstPage, "$.totalPages")).isEqualTo(2);
    }

    @Test
    void importJobListFiltersByStatusAndCatalogEntry() throws Exception {
        final String entry = entry("Tracked");
        catalog.writeDays(entry, marker, 0);
        final long importJobId = JsonPath.<Number>read(queueImportJob(entry).getContentAsString(), "$.id").longValue();

        final List<Integer> pendingBefore = listImportJobIds("status", "PENDING");
        drainQueue();
        final List<Integer> pendingAfter = listImportJobIds("status", "PENDING");
        final List<Integer> completedAfter = listImportJobIds("status", "COMPLETED");
        final List<Integer> ofEntry = listImportJobIds("catalogEntry", entry);

        assertThat(pendingBefore).contains((int) importJobId);
        assertThat(pendingAfter).doesNotContain((int) importJobId);
        assertThat(completedAfter).contains((int) importJobId);
        assertThat(ofEntry).containsExactly((int) importJobId);
    }

    @Test
    void aPeriodMustBeOrderedAndWithinTheConfiguredLimit() throws Exception {
        final String entry = entry("Limits");
        catalog.writeDays(entry, marker, 0);
        final long templateId = importAndGetTemplateId(entry);

        assertThat(getDays(templateId, 10, 9).getStatus()).isEqualTo(400);
        assertThat(getDays(templateId, 1, RANGE_MAX_DAYS).getStatus()).isEqualTo(200);
        assertThat(getDays(templateId, 1, RANGE_MAX_DAYS + 1).getStatus()).isEqualTo(400);
        assertThat(perform(get("/admin/program-templates/%d/days".formatted(templateId))
                .cookie(admin)).getStatus()).isEqualTo(400);
    }

    @Test
    void deletingATemplateRemovesItAndItsDays() throws Exception {
        final String entry = entry("Doomed");
        catalog.writeDays(entry, marker, 0, 1);
        final long templateId = importAndGetTemplateId(entry);

        final MockHttpServletResponse deleted =
                perform(withCsrf(delete("/admin/program-templates/" + templateId)).cookie(admin));

        assertThat(deleted.getStatus()).isEqualTo(204);
        assertThat(perform(get("/admin/program-templates/" + templateId).cookie(admin)).getStatus())
                .isEqualTo(404);
        assertThat(getDays(templateId, 1, 7).getStatus()).isEqualTo(404);
        assertThat(perform(withCsrf(delete("/admin/program-templates/" + templateId)).cookie(admin)).getStatus())
                .isEqualTo(404);
    }

    @Test
    void unknownTemplatesAndImportJobsAreNotFound() throws Exception {
        assertThat(perform(get("/admin/program-templates/999999999").cookie(admin)).getStatus()).isEqualTo(404);
        assertThat(getDays(999999999L, 1, 7).getStatus()).isEqualTo(404);
        assertThat(getImportJob(999999999L).getStatus()).isEqualTo(404);
    }

    @Test
    void aTemplateKeepsTheDateItsSourceStartedOn() throws Exception {
        final String entry = entry("Dated");
        catalog.writeDays(entry, marker, 3, 5);

        final long templateId = importAndGetTemplateId(entry);
        final String template = perform(get("/admin/program-templates/" + templateId).cookie(admin))
                .getContentAsString();

        assertThat(JsonPath.<String>read(template, "$.originalStartDate"))
                .isEqualTo(FIRST_DATE.plusDays(3).toString());
    }

    @Test
    void aProgramFromATemplateStartingOnItsOriginalStartDateReplaysTheSourceDates() throws Exception {
        final String entry = entry("Original dates");
        catalog.writeDays(entry, marker, 0, 3);
        catalog.writeFile(entry, "second.json", dayJson(FIRST_DATE.plusDays(1), "B " + marker, "A " + marker));
        final long templateId = importAndGetTemplateId(entry);
        final String originalStartDate = JsonPath.read(
                perform(get("/admin/program-templates/" + templateId).cookie(admin)).getContentAsString(),
                "$.originalStartDate");

        final MockHttpServletResponse created = createProgramFromTemplate(templateId, """
                {"name":"My trial","description":"Copied","startWeek":"%s"}
                """.formatted(originalStartDate));

        assertThat(created.getStatus()).isEqualTo(201);
        final long programId = JsonPath.<Number>read(created.getContentAsString(), "$.id").longValue();
        assertThat(created.getHeader(HttpHeaders.LOCATION)).endsWith("/programs/" + programId);
        assertThat(JsonPath.<String>read(created.getContentAsString(), "$.name")).isEqualTo("My trial");
        assertThat(JsonPath.<String>read(created.getContentAsString(), "$.description")).isEqualTo("Copied");

        final String calendar = getCalendar(programId, FIRST_DATE, FIRST_DATE.plusDays(3));

        assertThat(JsonPath.<String>read(calendar, "$.days[0].day.status")).isEqualTo("PLANNED");
        assertThat(JsonPath.<List<String>>read(calendar, "$.days[0].day.parts[*].title"))
                .containsExactly(marker + " 0");
        assertThat(JsonPath.<List<String>>read(calendar, "$.days[1].day.parts[*].title"))
                .containsExactly("B " + marker, "A " + marker);
        assertThat(JsonPath.<Map<String, Object>>read(calendar, "$.days[2]").get("day")).isNull();
        assertThat(JsonPath.<List<String>>read(calendar, "$.days[3].day.parts[*].title"))
                .containsExactly(marker + " 3");
    }

    @Test
    void aProgramFromATemplateInAnotherWeekKeepsEveryWorkoutOnItsWeekday() throws Exception {
        final String entry = entry("Shifted");
        // FIRST_DATE is a Thursday, so the source's days are on Thursday and Saturday
        catalog.writeDays(entry, marker, 0, 2);
        final long templateId = importAndGetTemplateId(entry);
        final LocalDate wednesdayOfTargetWeek = LocalDate.of(2026, 11, 11);
        final LocalDate thursdayOfTargetWeek = LocalDate.of(2026, 11, 12);

        final MockHttpServletResponse created = createProgramFromTemplate(templateId, """
                {"name":"Shifted","startWeek":"%s"}
                """.formatted(wednesdayOfTargetWeek));

        assertThat(created.getStatus()).isEqualTo(201);
        final long programId = JsonPath.<Number>read(created.getContentAsString(), "$.id").longValue();
        final String calendar = getCalendar(programId, thursdayOfTargetWeek, thursdayOfTargetWeek.plusDays(2));

        assertThat(JsonPath.<String>read(calendar, "$.days[0].day.dayOfWeek")).isEqualTo("THURSDAY");
        assertThat(JsonPath.<List<String>>read(calendar, "$.days[0].day.parts[*].title"))
                .containsExactly(marker + " 0");
        assertThat(JsonPath.<Map<String, Object>>read(calendar, "$.days[1]").get("day")).isNull();
        assertThat(JsonPath.<String>read(calendar, "$.days[2].day.dayOfWeek")).isEqualTo("SATURDAY");
        assertThat(JsonPath.<List<String>>read(calendar, "$.days[2].day.parts[*].title"))
                .containsExactly(marker + " 2");
        assertThat(JsonPath.<List<Object>>read(
                getCalendar(programId, wednesdayOfTargetWeek.minusDays(2), wednesdayOfTargetWeek), "$.days[*].day"))
                .containsOnlyNulls();
    }

    @Test
    void aStartWeekGivenByItsSundayStartsAMondaySourceOnThatWeeksMonday() throws Exception {
        final String entry = entry("Monday start");
        // 2026-01-05 is a Monday
        catalog.writeDays(entry, marker, 4, 5);
        final long templateId = importAndGetTemplateId(entry);

        final MockHttpServletResponse created = createProgramFromTemplate(templateId, """
                {"name":"Monday start","startWeek":"2026-11-15"}
                """);

        final long programId = JsonPath.<Number>read(created.getContentAsString(), "$.id").longValue();
        final String calendar = getCalendar(programId, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 10));
        assertThat(JsonPath.<List<String>>read(calendar, "$.days[*].day.dayOfWeek"))
                .containsExactly("MONDAY", "TUESDAY");
        assertThat(JsonPath.<List<String>>read(calendar, "$.days[0].day.parts[*].title"))
                .containsExactly(marker + " 4");
    }

    @Test
    void aProgramFromATemplateNeedsAValidRequestAndAnExistingTemplate() throws Exception {
        final String entry = entry("Requests");
        catalog.writeDays(entry, marker, 0);
        final long templateId = importAndGetTemplateId(entry);

        final String validBody = "{\"name\":\"X\",\"startWeek\":\"2026-11-09\"}";

        assertThat(createProgramFromTemplate(templateId, "{\"name\":\"X\"}").getStatus()).isEqualTo(400);
        assertThat(createProgramFromTemplate(templateId, "{\"startWeek\":\"2026-11-09\"}").getStatus())
                .isEqualTo(400);
        assertThat(createProgramFromTemplate(999999999L, validBody).getStatus()).isEqualTo(404);
        assertThat(perform(withCsrf(post("/admin/program-templates/%d/programs".formatted(templateId)))
                .cookie(registerAndGetAccessCookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)).getStatus()).isEqualTo(403);
    }

    @Test
    void onlyAdminsCanUseTemplatesTheCatalogAndImportJobs() throws Exception {
        final String entry = entry("Private");
        catalog.writeDays(entry, marker, 0);
        final MockHttpServletResponse queued = queueImportJob(entry);
        final long importJobId = JsonPath.<Number>read(queued.getContentAsString(), "$.id").longValue();
        drainQueue();
        final long templateId = JsonPath.<Number>read(
                getImportJob(importJobId).getContentAsString(), "$.programTemplateId").longValue();
        final Cookie user = registerAndGetAccessCookie();

        assertThat(perform(get("/admin/program-templates").cookie(user)).getStatus()).isEqualTo(403);
        assertThat(perform(get("/admin/program-templates/" + templateId).cookie(user)).getStatus()).isEqualTo(403);
        assertThat(perform(get("/admin/program-templates/%d/days".formatted(templateId))
                .param("fromDay", "1").param("toDay", "7").cookie(user)).getStatus()).isEqualTo(403);
        assertThat(perform(withCsrf(delete("/admin/program-templates/" + templateId)).cookie(user)).getStatus())
                .isEqualTo(403);
        assertThat(perform(get(CATALOG_PATH).cookie(user)).getStatus()).isEqualTo(403);
        assertThat(perform(withCsrf(post(IMPORT_JOBS_PATH))
                .cookie(user)
                .contentType(MediaType.APPLICATION_JSON)
                .content(importJobBody(entry))).getStatus()).isEqualTo(403);
        assertThat(perform(get(IMPORT_JOBS_PATH).cookie(user)).getStatus()).isEqualTo(403);
        assertThat(perform(get(IMPORT_JOBS_PATH + "/" + importJobId).cookie(user)).getStatus()).isEqualTo(403);
        assertThat(perform(get("/admin/program-templates")).getStatus()).isEqualTo(401);
        assertThat(perform(get(CATALOG_PATH)).getStatus()).isEqualTo(401);
        assertThat(perform(get(IMPORT_JOBS_PATH)).getStatus()).isEqualTo(401);
    }

    private String entry(final String name) {
        return name + " " + marker;
    }

    private void drainQueue() {
        importJobWorker.runDueJobs(EnumSet.allOf(ImportJobType.class));
    }

    private String importAndGetJob(final String entry) throws Exception {
        final MockHttpServletResponse queued = queueImportJob(entry);
        assertThat(queued.getStatus()).isEqualTo(202);
        final long importJobId = JsonPath.<Number>read(queued.getContentAsString(), "$.id").longValue();
        drainQueue();
        return getImportJob(importJobId).getContentAsString();
    }

    private long importAndGetTemplateId(final String entry) throws Exception {
        final String importJob = importAndGetJob(entry);
        assertThat(JsonPath.<String>read(importJob, "$.status")).isEqualTo("COMPLETED");
        return JsonPath.<Number>read(importJob, "$.programTemplateId").longValue();
    }

    private String importAndGetError(final String entry) throws Exception {
        final String importJob = importAndGetJob(entry);
        assertThat(JsonPath.<String>read(importJob, "$.status")).isEqualTo("FAILED");
        return JsonPath.read(importJob, "$.error");
    }

    private MockHttpServletResponse queueImportJob(final String entry) throws Exception {
        return perform(withCsrf(post(IMPORT_JOBS_PATH))
                .cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(importJobBody(entry)));
    }

    private MockHttpServletResponse getImportJob(final long importJobId) throws Exception {
        return perform(get(IMPORT_JOBS_PATH + "/" + importJobId).cookie(admin));
    }

    private List<Integer> listImportJobIds(final String... paramNamesAndValues) throws Exception {
        final String page = perform(withParams(get(IMPORT_JOBS_PATH).cookie(admin).param("size", "100"),
                paramNamesAndValues)).getContentAsString();
        return JsonPath.read(page, "$.content[*].id");
    }

    private MockHttpServletResponse getDays(final long templateId, final int fromDay, final int toDay)
            throws Exception {
        return perform(get("/admin/program-templates/%d/days".formatted(templateId))
                .param("fromDay", String.valueOf(fromDay))
                .param("toDay", String.valueOf(toDay))
                .cookie(admin));
    }

    private MockHttpServletResponse listTemplates(final String... paramNamesAndValues) throws Exception {
        return perform(withParams(get("/admin/program-templates").cookie(admin), paramNamesAndValues));
    }

    private MockHttpServletResponse listCatalog(final String... paramNamesAndValues) throws Exception {
        return perform(withParams(get(CATALOG_PATH).cookie(admin), paramNamesAndValues));
    }

    private MockHttpServletResponse createProgramFromTemplate(final long templateId, final String body)
            throws Exception {
        return perform(withCsrf(post("/admin/program-templates/%d/programs".formatted(templateId)))
                .cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String getCalendar(final long programId, final LocalDate from, final LocalDate to) throws Exception {
        final MockHttpServletResponse calendar = perform(get("/programs/%d/calendar".formatted(programId))
                .param("from", from.toString())
                .param("to", to.toString())
                .cookie(admin));
        assertThat(calendar.getStatus()).isEqualTo(200);
        return calendar.getContentAsString();
    }

    private static String importJobBody(final String entry) {
        return "{\"catalogEntry\":\"%s\"}".formatted(entry);
    }
}
