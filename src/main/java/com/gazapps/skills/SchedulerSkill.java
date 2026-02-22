package com.gazapps.skills;

import com.gazapps.config.AppConfig;
import com.gazapps.logging.LogService;
import com.gazapps.services.SchedulerService;
import com.gazapps.services.SchedulerService.MonitorJob;
import com.gazapps.util.SkillStatus;
import com.gazapps.util.UrlValidator;
import com.google.adk.tools.Annotations.Schema;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ADK skill for managing periodic monitor jobs.
 *
 * <p>Three tools:
 * <ul>
 *   <li>{@link #scheduleMonitor} — create a recurring keyword-watch job</li>
 *   <li>{@link #listMonitors} — list all jobs and their current state</li>
 *   <li>{@link #cancelMonitor} — stop and remove a job</li>
 * </ul>
 *
 * <p>Delegates execution to {@link SchedulerService}, which uses a daemon
 * {@link java.util.concurrent.ScheduledExecutorService} and persists jobs to
 * {@code work/scheduler-jobs.json}.
 */
public class SchedulerSkill {

    private static final LogService LOG = LogService.getInstance();

    // ── Tool 1: scheduleMonitor ───────────────────────────────────────────────

    @Schema(description = """
            Schedule a recurring monitor that periodically checks a URL (RSS feed or web page)
            for a keyword and sends a notification when found.
            Works with both RSS/Atom feeds and regular web pages.
            Jobs are persisted to disk and survive application restarts.
            Use listMonitors() to view active jobs and cancelMonitor() to stop one.
            IMPORTANT: confirm the URL, keyword and interval with the user before calling this.
            """)
    public static Map<String, String> scheduleMonitor(
            @Schema(name = "feedUrlOrWebUrl", description = "Full URL of the RSS feed or web page to monitor (e.g. https://g1.globo.com/feed/ or https://news.ycombinator.com)") String feedUrlOrWebUrl,
            @Schema(name = "keyword", description = "The keyword to watch for (case-insensitive). The monitor triggers when this word appears in a feed item title/description or page content.") String keyword,
            @Schema(name = "intervalMinutes", description = "How often to check the URL, in minutes. Minimum is scheduler.min.interval.minutes (default 5). Recommended: 60 for news feeds, 30 for high-frequency sites.") int intervalMinutes,
            @Schema(name = "description", description = "A short human-readable description of what this monitor is watching (e.g. 'Java news on G1')") String description) {

        LOG.section("TOOL CALL: scheduleMonitor");
        LOG.info("SchedulerSkill", "URL=" + feedUrlOrWebUrl + " keyword=" + keyword
                + " interval=" + intervalMinutes + "min");

        // Validate URL
        try {
            UrlValidator.validate(feedUrlOrWebUrl);
        } catch (IllegalArgumentException e) {
            LOG.warn("SchedulerSkill", "Blocked unsafe URL: " + e.getMessage());
            return errorMap(feedUrlOrWebUrl, "URL blocked for security reasons: " + e.getMessage());
        }

        // Validate interval
        if (intervalMinutes < AppConfig.SCHEDULER_MIN_INTERVAL_MINUTES) {
            String msg = "intervalMinutes (" + intervalMinutes + ") is below the minimum allowed ("
                    + AppConfig.SCHEDULER_MIN_INTERVAL_MINUTES + " min)";
            LOG.warn("SchedulerSkill", msg);
            return errorMap(feedUrlOrWebUrl, msg);
        }

        // Validate job count
        int currentCount = SchedulerService.getInstance().listMonitors().size();
        if (currentCount >= AppConfig.SCHEDULER_MAX_JOBS) {
            String msg = "Maximum number of monitor jobs reached (" + AppConfig.SCHEDULER_MAX_JOBS + ")."
                    + " Cancel an existing job before adding a new one.";
            LOG.warn("SchedulerSkill", msg);
            return errorMap(feedUrlOrWebUrl, msg);
        }

        // Validate keyword
        if (keyword == null || keyword.isBlank()) {
            return errorMap(feedUrlOrWebUrl, "keyword must not be blank");
        }

        String jobId = SchedulerService.getInstance()
                .scheduleMonitor(feedUrlOrWebUrl, keyword, intervalMinutes,
                        description != null ? description : "");

        // Retrieve the job to populate the result map
        MonitorJob job = SchedulerService.getInstance().listMonitors().stream()
                .filter(j -> j.id.equals(jobId))
                .findFirst()
                .orElse(null);

        Map<String, String> result = new HashMap<>();
        result.put("status",           SkillStatus.SUCCESS.value());
        result.put("job_id",           jobId);
        result.put("url",              feedUrlOrWebUrl);
        result.put("keyword",          keyword);
        result.put("interval_minutes", String.valueOf(intervalMinutes));
        result.put("next_run",         job != null ? job.nextRunTime : "unknown");
        result.put("description",      description != null ? description : "");

        LOG.toolResult("scheduleMonitor", result);
        return result;
    }

    // ── Tool 2: listMonitors ─────────────────────────────────────────────────

    @Schema(description = """
            List all active monitor jobs and their current state.
            Shows each job's id, URL, keyword, interval, last result, and next scheduled run.
            Use the job_id from this list when calling cancelMonitor().
            lastResult values: "pending" (never run), "match" (keyword found), "no_match", "error".
            """)
    public static Map<String, String> listMonitors() {

        LOG.section("TOOL CALL: listMonitors");

        List<MonitorJob> monitorList = SchedulerService.getInstance().listMonitors();

        JsonArray jsonJobs = new JsonArray();
        for (MonitorJob job : monitorList) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id",               job.id);
            obj.addProperty("url",              job.url);
            obj.addProperty("keyword",          job.keyword);
            obj.addProperty("interval_minutes", job.intervalMinutes);
            obj.addProperty("description",      job.description);
            obj.addProperty("next_run",         job.nextRunTime);
            obj.addProperty("last_result",      job.lastResult);
            obj.addProperty("enabled",          job.enabled);
            jsonJobs.add(obj);
        }

        Map<String, String> result = new HashMap<>();
        result.put("status",    SkillStatus.SUCCESS.value());
        result.put("job_count", String.valueOf(monitorList.size()));
        result.put("jobs",      jsonJobs.toString());

        LOG.toolResult("listMonitors", result);
        return result;
    }

    // ── Tool 3: cancelMonitor ────────────────────────────────────────────────

    @Schema(description = """
            Cancel and permanently remove a monitor job by its id.
            Use listMonitors() first to confirm the correct job id.
            The job is stopped immediately and removed from persistent storage.
            """)
    public static Map<String, String> cancelMonitor(
            @Schema(name = "jobId", description = "The job id to cancel (e.g. 'job-a1b2c3d4'). Use listMonitors() to find this id.") String jobId) {

        LOG.section("TOOL CALL: cancelMonitor");
        LOG.info("SchedulerSkill", "jobId=" + jobId);

        if (jobId == null || jobId.isBlank()) {
            return errorMap(null, "jobId must not be blank");
        }

        boolean cancelled = SchedulerService.getInstance().cancelMonitor(jobId);

        Map<String, String> result = new HashMap<>();
        if (cancelled) {
            result.put("status",  SkillStatus.SUCCESS.value());
            result.put("job_id", jobId);
            result.put("message", "Monitor job cancelled and removed");
        } else {
            result.put("status",  SkillStatus.ERROR.value());
            result.put("job_id", jobId);
            result.put("message", "Job not found: " + jobId + ". Use listMonitors() to see existing jobs.");
        }

        LOG.toolResult("cancelMonitor", result);
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Map<String, String> errorMap(String url, String message) {
        Map<String, String> err = new HashMap<>();
        err.put("status",  SkillStatus.ERROR.value());
        err.put("url",     url != null ? url : "");
        err.put("message", message);
        return err;
    }
}
