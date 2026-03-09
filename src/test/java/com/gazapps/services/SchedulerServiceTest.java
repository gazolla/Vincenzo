package com.gazapps.services;

import com.gazapps.config.AppConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SchedulerService}.
 *
 * <p>Tests use reflection to reset the singleton between test methods,
 * ensuring each test starts with a clean state.
 */
class SchedulerServiceTest {

    @TempDir
    Path tempDir;

    private String originalJobsFileProp;

    @BeforeEach
    void setUp() throws Exception {
        // Save and override the scheduler.jobs.file property in PROPS
        originalJobsFileProp = getJobsFileProp();
        overrideJobsFilePath(tempDir.resolve("test-scheduler-jobs.json").toString());
        resetSingleton();
    }

    @AfterEach
    void tearDown() throws Exception {
        resetSingleton();
        // Restore original property value
        overrideJobsFilePath(originalJobsFileProp != null ? originalJobsFileProp : "work/scheduler-jobs.json");
    }

    // ── listMonitors — empty state ────────────────────────────────────────────

    @Test
    void listMonitors_freshStart_returnsEmptyList() {
        List<SchedulerService.MonitorJob> jobs = SchedulerService.getInstance().listMonitors();
        assertNotNull(jobs);
        assertTrue(jobs.isEmpty());
    }

    // ── scheduleMonitor ───────────────────────────────────────────────────────

    @Test
    void scheduleMonitor_validArgs_returnsJobId() {
        String jobId = SchedulerService.getInstance()
                .scheduleMonitor("https://example.com/rss", "java", 10, "Test job");

        assertNotNull(jobId);
        assertTrue(jobId.startsWith("job-"), "Job id should start with 'job-'");
    }

    @Test
    void scheduleMonitor_addsJobToList() {
        SchedulerService.getInstance()
                .scheduleMonitor("https://example.com/rss", "java", 10, "Test job");

        List<SchedulerService.MonitorJob> jobs = SchedulerService.getInstance().listMonitors();
        assertEquals(1, jobs.size());
        assertEquals("https://example.com/rss", jobs.get(0).url);
        assertEquals("java", jobs.get(0).keyword);
        assertEquals(10, jobs.get(0).intervalMinutes);
        assertEquals("pending", jobs.get(0).lastResult);
        assertTrue(jobs.get(0).enabled);
    }

    @Test
    void scheduleMonitor_multipleJobs_allListed() {
        SchedulerService svc = SchedulerService.getInstance();
        svc.scheduleMonitor("https://example.com/rss1", "java", 10, "Job 1");
        svc.scheduleMonitor("https://example.com/rss2", "python", 15, "Job 2");
        svc.scheduleMonitor("https://example.com/news", "AI", 30, "Job 3");

        assertEquals(3, svc.listMonitors().size());
    }

    // ── cancelMonitor ─────────────────────────────────────────────────────────

    @Test
    void cancelMonitor_unknownId_returnsFalse() {
        boolean result = SchedulerService.getInstance().cancelMonitor("job-nonexistent");
        assertFalse(result);
    }

    @Test
    void cancelMonitor_existingJob_returnsTrueAndRemoves() {
        SchedulerService svc = SchedulerService.getInstance();
        String jobId = svc.scheduleMonitor("https://example.com/rss", "java", 10, "Test");

        boolean cancelled = svc.cancelMonitor(jobId);

        assertTrue(cancelled);
        assertTrue(svc.listMonitors().isEmpty(), "Job should be removed after cancellation");
    }

    @Test
    void cancelMonitor_afterCancel_jobNotInList() {
        SchedulerService svc = SchedulerService.getInstance();
        String jobId1 = svc.scheduleMonitor("https://example.com/rss1", "java", 10, "Job 1");
        String jobId2 = svc.scheduleMonitor("https://example.com/rss2", "python", 15, "Job 2");

        svc.cancelMonitor(jobId1);

        List<SchedulerService.MonitorJob> remaining = svc.listMonitors();
        assertEquals(1, remaining.size());
        assertEquals(jobId2, remaining.get(0).id);
    }

    // ── JSON persistence — round-trip ─────────────────────────────────────────

    @Test
    void saveAndLoad_roundTrip_preservesAllFields() throws Exception {
        Path jobsFile = tempDir.resolve("test-scheduler-jobs.json");
        overrideJobsFilePath(jobsFile.toString());
        resetSingleton();

        // Schedule a job — triggers saveJobs()
        SchedulerService svc = SchedulerService.getInstance();
        String jobId = svc.scheduleMonitor(
                "https://example.com/rss",
                "machine learning",
                60,
                "ML news monitor");

        SchedulerService.MonitorJob original = svc.listMonitors().get(0);

        // File must exist after scheduling
        assertTrue(Files.exists(jobsFile), "Jobs file should be created after scheduling");

        String json = Files.readString(jobsFile, StandardCharsets.UTF_8);
        assertFalse(json.isBlank(), "Jobs file should not be empty");
        assertTrue(json.contains("machine learning"), "JSON should contain keyword");
        assertTrue(json.contains(jobId), "JSON should contain job id");

        // Reset singleton and reload
        resetSingleton();
        overrideJobsFilePath(jobsFile.toString());

        SchedulerService loaded = SchedulerService.getInstance();
        List<SchedulerService.MonitorJob> loadedJobs = loaded.listMonitors();

        assertEquals(1, loadedJobs.size(), "Loaded service should have 1 job");

        SchedulerService.MonitorJob loadedJob = loadedJobs.get(0);
        assertEquals(original.id,              loadedJob.id);
        assertEquals(original.url,             loadedJob.url);
        assertEquals(original.keyword,         loadedJob.keyword);
        assertEquals(original.intervalMinutes, loadedJob.intervalMinutes);
        assertEquals(original.description,     loadedJob.description);
        assertEquals(original.lastResult,      loadedJob.lastResult);
        assertTrue(loadedJob.enabled);
    }

    @Test
    void saveAndLoad_cancelledJob_isNotReloaded() throws Exception {
        Path jobsFile = tempDir.resolve("test-scheduler-jobs.json");
        overrideJobsFilePath(jobsFile.toString());
        resetSingleton();

        SchedulerService svc = SchedulerService.getInstance();
        String jobId = svc.scheduleMonitor("https://example.com/rss", "java", 10, "Test");
        svc.cancelMonitor(jobId); // removes from map → saveJobs writes empty array

        // Reset and reload
        resetSingleton();
        overrideJobsFilePath(jobsFile.toString());

        SchedulerService loaded = SchedulerService.getInstance();
        assertTrue(loaded.listMonitors().isEmpty(), "Cancelled job should not be reloaded");
    }

    @Test
    void saveAndLoad_missingFile_startsEmpty() throws Exception {
        // Point to a file that doesn't exist
        Path nonExistent = tempDir.resolve("does-not-exist.json");
        overrideJobsFilePath(nonExistent.toString());
        resetSingleton();

        SchedulerService svc = SchedulerService.getInstance();
        assertTrue(svc.listMonitors().isEmpty(), "Should start with empty job list when file is absent");
    }

    // ── Job state fields ──────────────────────────────────────────────────────

    @Test
    void scheduleMonitor_nextRunTime_isSetInFuture() {
        SchedulerService svc = SchedulerService.getInstance();
        svc.scheduleMonitor("https://example.com/rss", "java", 60, "Test");

        SchedulerService.MonitorJob job = svc.listMonitors().get(0);
        assertNotNull(job.nextRunTime, "nextRunTime should be set");
        assertFalse(job.nextRunTime.isBlank());
    }

    @Test
    void scheduleMonitor_jobIdIsUnique() {
        SchedulerService svc = SchedulerService.getInstance();
        String id1 = svc.scheduleMonitor("https://example.com/rss1", "java", 10, "Job 1");
        String id2 = svc.scheduleMonitor("https://example.com/rss2", "java", 10, "Job 2");

        assertNotEquals(id1, id2, "Each job should get a unique id");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Reset the SchedulerService singleton via reflection so each test starts fresh.
     */
    private static void resetSingleton() throws Exception {
        Field field = SchedulerService.class.getDeclaredField("instance");
        field.setAccessible(true);
        SchedulerService old = (SchedulerService) field.get(null);
        if (old != null) {
            // Best-effort shutdown of the thread pool to avoid test interference
            try {
                Field poolField = SchedulerService.class.getDeclaredField("pool");
                poolField.setAccessible(true);
                java.util.concurrent.ScheduledExecutorService pool =
                        (java.util.concurrent.ScheduledExecutorService) poolField.get(old);
                pool.shutdownNow();
            } catch (Exception ignored) {}
        }
        field.set(null, null);
    }

    /**
     * Override the {@code scheduler.jobs.file} property in AppConfig's internal
     * {@code Properties} object via reflection (Java 21 compatible).
     * This avoids touching real job files during tests.
     */
    private static void overrideJobsFilePath(String path) throws Exception {
        Field propsField = AppConfig.class.getDeclaredField("props");
        propsField.setAccessible(true);
        java.util.Properties props = (java.util.Properties) propsField.get(AppConfig.getInstance());
        props.setProperty("scheduler.jobs.file", path);
    }

    /** Read current value of scheduler.jobs.file from AppConfig's PROPS. */
    private static String getJobsFileProp() throws Exception {
        Field propsField = AppConfig.class.getDeclaredField("props");
        propsField.setAccessible(true);
        java.util.Properties props = (java.util.Properties) propsField.get(AppConfig.getInstance());
        return props.getProperty("scheduler.jobs.file", "work/scheduler-jobs.json");
    }
}
