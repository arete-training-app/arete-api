package app.arete.arete_api.controller;

import app.arete.arete_api.TestProgramTemplateCatalog;
import app.arete.arete_api.config.properties.ProgramTemplateProperties;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The only test with polling on: a queued import job is picked up by the worker without anything triggering it.
 */
@TestPropertySource(properties = {
        "arete.import-job.polling-enabled=true",
        "arete.import-job.poll-interval=200ms"
})
class ImportJobPollingIntegrationTest extends AbstractProgramIntegrationTest {

    private static final String IMPORT_JOBS_PATH = "/admin/program-templates/import-jobs";
    private static final Duration PROCESSING_TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private ProgramTemplateProperties programTemplateProperties;

    @Test
    void aQueuedImportJobIsPickedUpByThePollingWorker() throws Exception {
        final String marker = UUID.randomUUID().toString();
        final String entry = "Polled " + marker;
        new TestProgramTemplateCatalog(programTemplateProperties.catalog().directory())
                .writeDays(entry, "Squat " + marker, 0, 1);
        final Cookie admin = registerAdminAndGetAccessCookie();

        final MockHttpServletResponse queued = perform(withCsrf(post(IMPORT_JOBS_PATH))
                .cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"catalogEntry\":\"%s\"}".formatted(entry)));
        assertThat(queued.getStatus()).isEqualTo(202);
        final long importJobId = JsonPath.<Number>read(queued.getContentAsString(), "$.id").longValue();

        await().atMost(PROCESSING_TIMEOUT).untilAsserted(() -> {
            final String importJob = perform(get(IMPORT_JOBS_PATH + "/" + importJobId).cookie(admin))
                    .getContentAsString();
            assertThat(JsonPath.<String>read(importJob, "$.status")).isEqualTo("COMPLETED");
            assertThat(JsonPath.<Number>read(importJob, "$.programTemplateId")).isNotNull();
        });
    }
}
