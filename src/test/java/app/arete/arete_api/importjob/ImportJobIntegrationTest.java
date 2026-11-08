package app.arete.arete_api.importjob;

import app.arete.arete_api.MutableClock;
import app.arete.arete_api.TestProgramTemplateCatalog;
import app.arete.arete_api.TestcontainersConfiguration;
import app.arete.arete_api.api.model.ProgramTemplateImportJobRequestDto;
import app.arete.arete_api.api.model.ProgramTemplateImportJobResponseDto;
import app.arete.arete_api.api.model.ProgramTemplateImportJobStatusDto;
import app.arete.arete_api.config.properties.ImportJobProperties;
import app.arete.arete_api.config.properties.ProgramTemplateProperties;
import app.arete.arete_api.service.ImportJobService;
import app.arete.arete_api.service.ProgramTemplateImportJobService;
import app.arete.arete_api.service.ProgramTemplateService;
import app.arete.arete_api.util.enums.ImportJobType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, ImportJobIntegrationTest.ClockConfiguration.class})
class ImportJobIntegrationTest {

    private static final Duration PAST_ANY_RETRY = Duration.ofDays(1);
    private static final Set<ImportJobType> ALL_TYPES = EnumSet.allOf(ImportJobType.class);

    @Autowired
    private ImportJobWorker importJobWorker;

    @Autowired
    private ImportJobService importJobService;

    @Autowired
    private ProgramTemplateImportJobService programTemplateImportJobService;

    @Autowired
    private ImportJobProperties importJobProperties;

    @Autowired
    private ProgramTemplateProperties programTemplateProperties;

    @Autowired
    private MutableClock mutableClock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private ProgramTemplateService programTemplateService;

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfiguration {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.now());
        }
    }

    @Test
    void aTemporaryFailureIsRetriedOnceTheBackoffHasPassed() {
        doThrow(new IllegalStateException("Database hiccup"))
                .doCallRealMethod()
                .when(programTemplateService).createProgramTemplate(any());
        final long jobId = queueTemplate();

        importJobWorker.runDueJobs(ALL_TYPES);

        final ProgramTemplateImportJobResponseDto retrying = programTemplateImportJobService.getProgramTemplateImportJob(jobId);
        assertThat(retrying.getStatus()).isEqualTo(ProgramTemplateImportJobStatusDto.PENDING);
        assertThat(retrying.getAttempts()).isEqualTo(1);
        assertThat(retrying.getError()).isEqualTo("Database hiccup");
        assertThat(importJobWorker.runDueJobs(ALL_TYPES)).isZero();

        mutableClock.advance(importJobProperties.retryBackoff());
        importJobWorker.runDueJobs(ALL_TYPES);

        final ProgramTemplateImportJobResponseDto completed = programTemplateImportJobService.getProgramTemplateImportJob(jobId);
        assertThat(completed.getStatus()).isEqualTo(ProgramTemplateImportJobStatusDto.COMPLETED);
        assertThat(completed.getAttempts()).isEqualTo(2);
        assertThat(completed.getProgramTemplateId()).isNotNull();
        assertThat(completed.getError()).isNull();
    }

    @Test
    void aJobThatKeepsFailingTemporarilyFailsForGoodAfterTheLastAttempt() {
        doThrow(new IllegalStateException("Database down"))
                .when(programTemplateService).createProgramTemplate(any());
        final long jobId = queueTemplate();

        for (int attempt = 1; attempt <= importJobProperties.maxAttempts(); attempt++) {
            importJobWorker.runDueJobs(ALL_TYPES);
            mutableClock.advance(PAST_ANY_RETRY);
        }

        final ProgramTemplateImportJobResponseDto failed = programTemplateImportJobService.getProgramTemplateImportJob(jobId);
        assertThat(failed.getStatus()).isEqualTo(ProgramTemplateImportJobStatusDto.FAILED);
        assertThat(failed.getAttempts()).isEqualTo(importJobProperties.maxAttempts());
        assertThat(failed.getError()).isEqualTo("Database down");
        assertThat(failed.getCompletedAt()).isNotNull();
        assertThat(importJobWorker.runDueJobs(ALL_TYPES)).isZero();
    }

    @Test
    void anEntryThatDisappearedAfterQueueingFailsWithoutARetry() {
        final long jobId = queueTemplate();
        jdbcTemplate.update("UPDATE import_job SET catalog_entry = ? WHERE id = ?", "Gone " + UUID.randomUUID(), jobId);

        importJobWorker.runDueJobs(ALL_TYPES);

        final ProgramTemplateImportJobResponseDto failed = programTemplateImportJobService.getProgramTemplateImportJob(jobId);
        assertThat(failed.getStatus()).isEqualTo(ProgramTemplateImportJobStatusDto.FAILED);
        assertThat(failed.getAttempts()).isEqualTo(1);
        assertThat(failed.getError()).contains("no longer exists");
    }

    @Test
    void aJobLeftInProgressByAStoppedWorkerIsTakenOverOnceItsLeaseExpires() {
        final long jobId = queueTemplate();
        // Claimed but never processed, as if the worker stopped mid-job
        assertThat(importJobService.claimDueJobs(ALL_TYPES)).contains(jobId);

        assertThat(importJobWorker.runDueJobs(ALL_TYPES)).isZero();
        assertThat(programTemplateImportJobService.getProgramTemplateImportJob(jobId).getStatus()).isEqualTo(ProgramTemplateImportJobStatusDto.PROCESSING);

        mutableClock.advance(importJobProperties.lease().plusMinutes(1));
        importJobWorker.runDueJobs(ALL_TYPES);

        final ProgramTemplateImportJobResponseDto completed = programTemplateImportJobService.getProgramTemplateImportJob(jobId);
        assertThat(completed.getStatus()).isEqualTo(ProgramTemplateImportJobStatusDto.COMPLETED);
        assertThat(completed.getAttempts()).isEqualTo(2);
    }

    @Test
    void aCompletedJobIsNotRunAgain() {
        doCallRealMethod().when(programTemplateService).createProgramTemplate(any());
        final long jobId = queueTemplate();
        importJobWorker.runDueJobs(ALL_TYPES);

        importJobService.processJob(jobId);

        assertThat(programTemplateImportJobService.getProgramTemplateImportJob(jobId).getStatus()).isEqualTo(ProgramTemplateImportJobStatusDto.COMPLETED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM program_template WHERE id = ?",
                Long.class,
                programTemplateImportJobService.getProgramTemplateImportJob(jobId).getProgramTemplateId())).isEqualTo(1);
    }

    private long queueTemplate() {
        final String marker = UUID.randomUUID().toString();
        final String entry = "Queued " + marker;
        new TestProgramTemplateCatalog(programTemplateProperties.catalog().directory())
                .writeDays(entry, "Squat " + marker, 0);
        final ProgramTemplateImportJobRequestDto importJobRequestDto = new ProgramTemplateImportJobRequestDto();
        importJobRequestDto.setCatalogEntry(entry);
        return programTemplateImportJobService.enqueueImportJob(importJobRequestDto).getId();
    }
}
