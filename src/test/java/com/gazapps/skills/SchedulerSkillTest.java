package com.gazapps.skills;

import com.gazapps.config.AppConfig;
import com.gazapps.services.SchedulerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SchedulerSkill}.
 *
 * <p>Focuses on input validation and correct delegation to
 * {@link SchedulerService}. Does not test network calls or actual scheduling.
 */
class SchedulerSkillTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        overrideJobsFilePath(tempDir.resolve("scheduler-skill-test.json").toString());
        resetSchedulerSingleton();
    }

    @AfterEach
    void tearDown() throws Exception {
        resetSchedulerSingleton();
    }

    // ── scheduleMonitor — URL validation ─────────────────────────────────────

    @Test
    void scheduleMonitor_privateIpUrl_returnsErrorMap() {
        Map<String, String> result = SchedulerSkill.scheduleMonitor(
                "http://192.168.0.1/rss", "java", 10, "Test");
        assertEquals("error", result.get("status"));
        assertTrue(result.get("message").toLowerCase().contains("blocked"));
    }

    @Test
    void scheduleMonitor_loopbackUrl_returnsErrorMap() {
        Map<String, String> result = SchedulerSkill.scheduleMonitor(
                "http://127.0.0.1/feed", "java", 10, "Test");
        assertEquals("error", result.get("status"));
        assertTrue(result.get("message").toLowerCase().contains("blocked"));
    }

    @Test
    void scheduleMonitor_internalIp10_returnsErrorMap() {
        Map<String, String> result = SchedulerSkill.scheduleMonitor(
                "http://10.0.0.1/rss", "java", 10, "Test");
        assertEquals("error", result.get("status"));
        assertTrue(result.get("message").toLowerCase().contains("blocked"));
    }

    @Test
    void scheduleMonitor_nullUrl_returnsErrorMap() {
        Map<String, String> result = SchedulerSkill.scheduleMonitor(
                null, "java", 10, "Test");
        assertEquals("error", result.get("status"));
        assertNotNull(result.get("message"));
    }

    // ── scheduleMonitor — interval validation ─────────────────────────────────

    @Test
    void scheduleMonitor_intervalBelowMinimum_returnsErrorMap() {
        int belowMin = AppConfig.SCHEDULER_MIN_INTERVAL_MINUTES - 1;
        Map<String, String> result = SchedulerSkill.scheduleMonitor(
                "https://example.com/rss", "java", belowMin, "Test");
        assertEquals("error", result.get("status"));
        assertTrue(result.get("message").toLowerCase().contains("minimum") ||
                   result.get("message").toLowerCase().contains("interval"),
                "Error message should mention interval/minimum");
    }

    @Test
    void scheduleMonitor_intervalZero_returnsErrorMap() {
        Map<String, String> result = SchedulerSkill.scheduleMonitor(
                "https://example.com/rss", "java", 0, "Test");
        assertEquals("error", result.get("status"));
    }

    @Test
    void scheduleMonitor_intervalAtMinimum_returnsSuccess() {
        int minInterval = AppConfig.SCHEDULER_MIN_INTERVAL_MINUTES;
        Map<String, String> result = SchedulerSkill.scheduleMonitor(
                "https://example.com/rss", "java", minInterval, "Test");
        assertEquals("success", result.get("status"));
        assertNotNull(result.get("job_id"));
    }

    // ── scheduleMonitor — keyword validation ──────────────────────────────────

    @Test
    void scheduleMonitor_blankKeyword_returnsErrorMap() {
        Map<String, String> result = SchedulerSkill.scheduleMonitor(
                "https://example.com/rss", "   ", 10, "Test");
        assertEquals("error", result.get("status"));
        assertTrue(result.get("message").toLowerCase().contains("keyword"));
    }

    @Test
    void scheduleMonitor_nullKeyword_returnsErrorMap() {
        Map<String, String> result = SchedulerSkill.scheduleMonitor(
                "https://example.com/rss", null, 10, "Test");
        assertEquals("error", result.get("status"));
        assertNotNull(result.get("message"));
    }

    // ── scheduleMonitor — success path ────────────────────────────────────────

    @Test
    void scheduleMonitor_validArgs_returnsSuccessWithJobId() {
        Map<String, String> result = SchedulerSkill.scheduleMonitor(
                "https://example.com/rss", "java", 10, "Test monitor");
        assertEquals("success", result.get("status"));
        assertNotNull(result.get("job_id"));
        assertTrue(result.get("job_id").startsWith("job-"));
        assertEquals("https://example.com/rss", result.get("url"));
        assertEquals("java", result.get("keyword"));
        assertEquals("10", result.get("interval_minutes"));
    }

    @Test
    void scheduleMonitor_nullDescription_doesNotFail() {
        Map<String, String> result = SchedulerSkill.scheduleMonitor(
                "https://example.com/rss", "java", 10, null);
        assertEquals("success", result.get("status"));
    }

    // ── listMonitors ─────────────────────────────────────────────────────────

    @Test
    void listMonitors_noJobs_returnsSuccessWithZeroCount() {
        Map<String, String> result = SchedulerSkill.listMonitors();
        assertEquals("success", result.get("status"));
        assertEquals("0", result.get("job_count"));
        assertEquals("[]", result.get("jobs").trim());
    }

    @Test
    void listMonitors_afterScheduling_showsJob() {
        SchedulerSkill.scheduleMonitor("https://example.com/rss", "java", 10, "Test");

        Map<String, String> result = SchedulerSkill.listMonitors();
        assertEquals("success", result.get("status"));
        assertEquals("1", result.get("job_count"));
        assertTrue(result.get("jobs").contains("java"), "Jobs JSON should contain keyword");
        assertTrue(result.get("jobs").contains("example.com"), "Jobs JSON should contain URL");
    }

    @Test
    void listMonitors_alwaysReturnsJobsKey() {
        Map<String, String> result = SchedulerSkill.listMonitors();
        assertNotNull(result.get("jobs"));
    }

    // ── cancelMonitor ─────────────────────────────────────────────────────────

    @Test
    void cancelMonitor_unknownJobId_returnsErrorMap() {
        Map<String, String> result = SchedulerSkill.cancelMonitor("job-nonexistent");
        assertEquals("error", result.get("status"));
        assertTrue(result.get("message").toLowerCase().contains("not found") ||
                   result.get("message").toLowerCase().contains("job-nonexistent"));
    }

    @Test
    void cancelMonitor_blankJobId_returnsErrorMap() {
        Map<String, String> result = SchedulerSkill.cancelMonitor("   ");
        assertEquals("error", result.get("status"));
        assertTrue(result.get("message").toLowerCase().contains("blank") ||
                   result.get("message").toLowerCase().contains("jobid"));
    }

    @Test
    void cancelMonitor_nullJobId_returnsErrorMap() {
        Map<String, String> result = SchedulerSkill.cancelMonitor(null);
        assertEquals("error", result.get("status"));
        assertNotNull(result.get("message"));
    }

    @Test
    void cancelMonitor_existingJob_returnsSuccess() {
        Map<String, String> scheduled = SchedulerSkill.scheduleMonitor(
                "https://example.com/rss", "java", 10, "Test");
        String jobId = scheduled.get("job_id");

        Map<String, String> result = SchedulerSkill.cancelMonitor(jobId);
        assertEquals("success", result.get("status"));
        assertEquals(jobId, result.get("job_id"));
    }

    @Test
    void cancelMonitor_existingJob_removedFromList() {
        Map<String, String> scheduled = SchedulerSkill.scheduleMonitor(
                "https://example.com/rss", "java", 10, "Test");
        String jobId = scheduled.get("job_id");

        SchedulerSkill.cancelMonitor(jobId);

        Map<String, String> listResult = SchedulerSkill.listMonitors();
        assertEquals("0", listResult.get("job_count"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void resetSchedulerSingleton() throws Exception {
        Field field = SchedulerService.class.getDeclaredField("instance");
        field.setAccessible(true);
        SchedulerService old = (SchedulerService) field.get(null);
        if (old != null) {
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

    private static void overrideJobsFilePath(String path) throws Exception {
        Field propsField = AppConfig.class.getDeclaredField("PROPS");
        propsField.setAccessible(true);
        java.util.Properties props = (java.util.Properties) propsField.get(null);
        props.setProperty("scheduler.jobs.file", path);
    }
}
