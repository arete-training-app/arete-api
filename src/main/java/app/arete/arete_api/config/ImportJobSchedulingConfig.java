package app.arete.arete_api.config;

import app.arete.arete_api.config.properties.ImportJobProperties;
import app.arete.arete_api.importjob.ImportJobWorker;
import app.arete.arete_api.util.enums.ImportJobType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.EnumSet;

/**
 * Polls the import queue of every type, waiting {@code arete.import-job.poll-interval} after each run finishes, so
 * runs never overlap and a newly queued job starts within one interval. Registered here rather than with
 * {@code @Scheduled}, so the interval comes from {@link ImportJobProperties}. Off when
 * {@code arete.import-job.polling-enabled} is false.
 */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "arete.import-job.polling-enabled", havingValue = "true")
public class ImportJobSchedulingConfig implements SchedulingConfigurer {

    private final ImportJobWorker importJobWorker;
    private final ImportJobProperties importJobProperties;

    @Override
    public void configureTasks(final ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addFixedDelayTask(
                () -> importJobWorker.runDueJobs(EnumSet.allOf(ImportJobType.class)),
                importJobProperties.pollInterval());
    }
}
