package com.gazapps.services;

import com.gazapps.config.AppConfig;
import com.gazapps.logging.LogService;
import com.gazapps.skills.RssSkill;
import com.gazapps.skills.WebContentSkill;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Singleton service that manages periodic monitor jobs.
 *
 * <p>
 * Jobs are persisted to {@code work/scheduler-jobs.json} (configurable via
 * {@code scheduler.jobs.file}) using Gson. On startup the service reloads all
 * enabled jobs from disk and re-schedules them with a
 * {@link ScheduledExecutorService} backed by daemon threads.
 * </p>
 *
 * <p>
 * Each job checks a URL (RSS feed or web page) for a keyword and triggers a
 * notification via {@link NotificationService} when a match is found.
 * </p>
 */
public class SchedulerService {

    private static final LogService LOG = LogService.getInstance();
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static volatile SchedulerService instance;

    public static SchedulerService getInstance() {
        if (instance == null) {
            synchronized (SchedulerService.class) {
                if (instance == null) {
                    instance = new SchedulerService();
                }
            }
        }
        return instance;
    }

    // ── Thread pool ───────────────────────────────────────────────────────────

    private final ScheduledExecutorService pool = Executors.newScheduledThreadPool(4, r -> {
        Thread t = new Thread(r, "scheduler-job");
        t.setDaemon(true);
        return t;
    });

    // ── State ─────────────────────────────────────────────────────────────────

    private final ConcurrentHashMap<String, MonitorJob> jobs = new ConcurrentHashMap<>();

    // ── MonitorJob ────────────────────────────────────────────────────────────

    /**
     * Represents a periodic monitor job. The {@code future} field is transient so
     * Gson skips it.
     */
    public static class MonitorJob {
        public String id;
        public String url;
        public String keyword;
        public int intervalMinutes;
        public String description;
        public String nextRunTime; // ISO-8601
        public String lastResult; // "pending" | "match" | "no_match" | "error"
        public boolean enabled;

        /** Not serialized — holds the live ScheduledFuture reference. */
        transient ScheduledFuture<?> future;
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    private SchedulerService() {
        LOG.info("SchedulerService", "Initializing...");
        ensureWorkDir();
        loadJobs();
        LOG.info("SchedulerService", "Ready — " + jobs.size() + " job(s) loaded");
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Register and schedule a new monitor job.
     *
     * @return the new job id (e.g. {@code "job-a1b2c3d4"})
     */
    public String scheduleMonitor(String url, String keyword, int intervalMinutes, String description) {
        MonitorJob job = new MonitorJob();
        job.id = "job-" + UUID.randomUUID().toString().substring(0, 8);
        job.url = url;
        job.keyword = keyword;
        job.intervalMinutes = intervalMinutes;
        job.description = description != null ? description : "";
        job.nextRunTime = LocalDateTime.now().plusMinutes(intervalMinutes).format(ISO);
        job.lastResult = "pending";
        job.enabled = true;

        jobs.put(job.id, job);
        scheduleJob(job);
        saveJobs();

        LOG.info("SchedulerService", "Scheduled job " + job.id
                + " for url=" + url + " keyword=" + keyword
                + " interval=" + intervalMinutes + "min");
        return job.id;
    }

    /** Return a snapshot list of all registered jobs. */
    public List<MonitorJob> listMonitors() {
        return new ArrayList<>(jobs.values());
    }

    /**
     * Cancel and remove a monitor job by id.
     *
     * @return {@code true} if found and cancelled; {@code false} if not found
     */
    public boolean cancelMonitor(String jobId) {
        MonitorJob job = jobs.remove(jobId);
        if (job == null) {
            LOG.warn("SchedulerService", "Cancel requested for unknown job: " + jobId);
            return false;
        }
        if (job.future != null) {
            job.future.cancel(false);
        }
        job.enabled = false;
        saveJobs();
        LOG.info("SchedulerService", "Cancelled and removed job " + jobId);
        return true;
    }

    // ── Job scheduling ────────────────────────────────────────────────────────

    private void scheduleJob(MonitorJob job) {
        long initialDelay = computeInitialDelay(job);
        long periodSeconds = (long) job.intervalMinutes * 60L;

        ScheduledFuture<?> future = pool.scheduleAtFixedRate(
                () -> runJob(job),
                initialDelay,
                periodSeconds,
                TimeUnit.SECONDS);
        job.future = future;
        LOG.debug("SchedulerService",
                "Job " + job.id + " scheduled — initialDelay=" + initialDelay + "s, period=" + periodSeconds + "s");
    }

    /** Compute seconds until the job's next planned run (0 if in the past). */
    private long computeInitialDelay(MonitorJob job) {
        try {
            LocalDateTime next = LocalDateTime.parse(job.nextRunTime, ISO);
            long seconds = ChronoUnit.SECONDS.between(LocalDateTime.now(), next);
            return Math.max(0L, seconds);
        } catch (Exception e) {
            return 0L; // run immediately on parse failure
        }
    }

    // ── Job execution ─────────────────────────────────────────────────────────

    private void runJob(MonitorJob job) {
        LOG.info("SchedulerService", "Running job " + job.id
                + " [" + job.keyword + "] @ " + job.url);
        long start = System.currentTimeMillis();

        try {
            boolean matched = false;
            String contentType = detectFeedOrWeb(job.url);

            if ("rss".equals(contentType)) {
                Map<String, String> result = RssSkill.searchInFeed(job.url, job.keyword);
                int matchCount = 0;
                try {
                    matchCount = Integer.parseInt(result.getOrDefault("match_count", "0"));
                } catch (NumberFormatException ignored) {
                }
                matched = "success".equals(result.get("status")) && matchCount > 0;
            } else {
                Map<String, String> result = WebContentSkill.fetchPageContent(job.url, null);
                String content = result.getOrDefault("content", "").toLowerCase();
                matched = "success".equals(result.get("status"))
                        && content.contains(job.keyword.toLowerCase());
            }

            job.lastResult = matched ? "match" : "no_match";
            job.nextRunTime = LocalDateTime.now().plusMinutes(job.intervalMinutes).format(ISO);

            if (matched) {
                String msg = "🔔 [Monitor] Palavra-chave \"" + job.keyword + "\" encontrada em:\n"
                        + job.url
                        + (job.description.isBlank() ? "" : "\n📝 " + job.description);
                NotificationService.getInstance().send(msg);
                LOG.info("SchedulerService", "Match found for job " + job.id + " — notification sent");
            } else {
                LOG.debug("SchedulerService", "No match for job " + job.id
                        + " (keyword=" + job.keyword + ")");
            }

            saveJobs();
            LOG.timing("SchedulerService", "Job " + job.id, System.currentTimeMillis() - start);

        } catch (Exception e) {
            job.lastResult = "error";
            LOG.error("SchedulerService", "Job " + job.id + " failed: " + e.getMessage(), e);
            saveJobs();
        }
    }

    /**
     * Heuristically determine whether a URL points to an RSS/Atom feed or a web
     * page.
     * Uses URL path patterns — no network call required.
     */
    private String detectFeedOrWeb(String url) {
        String lower = url.toLowerCase();
        if (lower.contains("/rss") || lower.contains("/feed")
                || lower.contains("/atom") || lower.endsWith(".xml")
                || lower.endsWith(".rss") || lower.endsWith(".atom")) {
            return "rss";
        }
        return "web";
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void ensureWorkDir() {
        try {
            Files.createDirectories(Paths.get("work"));
        } catch (IOException e) {
            LOG.warn("SchedulerService", "Could not create work directory: " + e.getMessage());
        }
    }

    /**
     * Returns the jobs file path — reads dynamically so tests can override via
     * PROPS.
     */
    private static Path jobsFilePath() {
        return Paths.get(AppConfig.schedulerJobsFile());
    }

    private void loadJobs() {
        Path path = jobsFilePath();
        if (!Files.exists(path)) {
            LOG.info("SchedulerService", "No jobs file found at " + path + " — starting empty");
            return;
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            Type listType = new TypeToken<List<MonitorJob>>() {
            }.getType();
            List<MonitorJob> loaded = GSON.fromJson(json, listType);
            if (loaded == null)
                return;

            int rescheduled = 0;
            for (MonitorJob job : loaded) {
                jobs.put(job.id, job);
                if (job.enabled) {
                    scheduleJob(job);
                    rescheduled++;
                }
            }
            LOG.info("SchedulerService", "Loaded " + loaded.size() + " job(s), rescheduled " + rescheduled);
        } catch (Exception e) {
            LOG.error("SchedulerService", "Failed to load jobs from " + path, e);
        }
    }

    private synchronized void saveJobs() {
        Path path = jobsFilePath();
        try {
            String json = GSON.toJson(new ArrayList<>(jobs.values()));
            Files.writeString(path, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            LOG.debug("SchedulerService", "Jobs persisted to " + path + " (" + jobs.size() + " job(s))");
        } catch (IOException e) {
            LOG.error("SchedulerService", "Failed to save jobs to " + path, e);
        }
    }
}
