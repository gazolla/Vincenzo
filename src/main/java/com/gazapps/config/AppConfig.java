package com.gazapps.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Central configuration for the AI Internet Search application.
 * Values are loaded from {@code application.properties} on the classpath.
 * Changing the file and restarting the app applies new values without recompiling.
 *
 * <p>Use {@link #getInstance()} to obtain the shared singleton. The class can also
 * be instantiated directly (e.g. in tests) to get an independent configuration object.</p>
 *
 * <p>All timeout values are in milliseconds unless the field name states otherwise.</p>
 */
public final class AppConfig {

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static final class Holder {
        static final AppConfig INSTANCE = new AppConfig();
    }

    /** Returns the shared application-wide configuration instance. */
    public static AppConfig getInstance() {
        return Holder.INSTANCE;
    }

    // ── Properties backing store ──────────────────────────────────────────────

    private final Properties props = load();

    // ── Loader ────────────────────────────────────────────────────────────────

    private static Properties load() {
        Properties p = new Properties();
        try (InputStream in = AppConfig.class.getResourceAsStream("/application.properties")) {
            if (in != null) {
                p.load(in);
            } else {
                System.err.println("[AppConfig] application.properties not found — using built-in defaults");
            }
        } catch (IOException e) {
            System.err.println("[AppConfig] Failed to load application.properties: " + e.getMessage());
        }
        return p;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    private String str(String key, String defaultVal) {
        return props.getProperty(key, defaultVal);
    }

    private boolean boolVal(String key, boolean defaultVal) {
        String val = props.getProperty(key);
        if (val == null) return defaultVal;
        return Boolean.parseBoolean(val.trim());
    }

    private int intVal(String key, int defaultVal) {
        String val = props.getProperty(key);
        if (val == null) return defaultVal;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            System.err.println("[AppConfig] Invalid integer for key '" + key + "': " + val
                    + " — using default " + defaultVal);
            return defaultVal;
        }
    }

    // ── SLF4J SimpleLogger ─────────────────────────────────────────────────────
    /** Default log level for SLF4J SimpleLogger (suppresses ADK/gRPC/Netty noise). */
    public final String SLF4J_DEFAULT_LOG_LEVEL  = str("slf4j.defaultLogLevel",  "warn");
    /** Whether SLF4J should print the date/time on each log line. */
    public final String SLF4J_SHOW_DATE_TIME     = str("slf4j.showDateTime",     "false");
    /** Whether SLF4J should print the thread name on each log line. */
    public final String SLF4J_SHOW_THREAD_NAME   = str("slf4j.showThreadName",   "false");
    /** Whether SLF4J should print the logger name on each log line. */
    public final String SLF4J_SHOW_LOG_NAME      = str("slf4j.showLogName",      "false");

    // ── Work directories ───────────────────────────────────────────────────────
    /** Directory for browser screenshots (created on startup if absent). */
    public final String SCREENSHOTS_DIR = str("work.screenshots.dir", "work/screenshots");

    /** Directory for browser downloads (created on startup if absent). */
    public final String DOWNLOADS_DIR = str("work.downloads.dir", "work/downloads");

    // ── HTTP / Browser User-Agent ──────────────────────────────────────────────
    public final String USER_AGENT = str("browser.user-agent",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/133.0.0.0 Safari/537.36");

    // ── HTTP client timeouts ───────────────────────────────────────────────────
    /** Connect timeout for the DDG JSON instant-answer HTTP client (seconds). */
    public final int HTTP_CONNECT_TIMEOUT_SECONDS =
            intVal("http.connect.timeout.seconds", 8);

    // ── Playwright navigation timeouts (milliseconds) ─────────────────────────
    /** Timeout for fetchPageContent navigation. */
    public final int FETCH_PAGE_NAVIGATE_TIMEOUT_MS =
            intVal("fetch.page.navigate.timeout.ms", 20_000);

    /**
     * Extra time (ms) to wait for NETWORKIDLE after navigation in fetchPageContent.
     * Ensures JS-rendered content (prices, dynamic data) is fully loaded before extraction.
     * If the site never reaches idle within this window, we proceed with what's available.
     */
    public final int FETCH_PAGE_NETWORKIDLE_TIMEOUT_MS =
            intVal("fetch.page.networkidle.timeout.ms", 8_000);

    /** Timeout for screenshotPage navigation. */
    public final int SCREENSHOT_NAVIGATE_TIMEOUT_MS =
            intVal("screenshot.navigate.timeout.ms", 15_000);

    /** Navigation timeout for summarizeUrl. */
    public final int SUMMARIZE_NAVIGATE_TIMEOUT_MS =
            intVal("summarize.navigate.timeout.ms", 20_000);

    /** Navigation timeout for extractStructuredData. */
    public final int EXTRACT_NAVIGATE_TIMEOUT_MS =
            intVal("extract.navigate.timeout.ms", 20_000);

    /** Navigation timeout for fillFormAndSubmit. */
    public final int FORM_NAVIGATE_TIMEOUT_MS =
            intVal("form.navigate.timeout.ms", 20_000);

    // ── Content truncation limits (characters) ────────────────────────────────
    /** Maximum body text length returned by fetchPageContent. */
    public final int FETCH_PAGE_MAX_CHARS =
            intVal("fetch.page.max.chars", 5_000);

    /** Maximum page text length returned by DuckDuckGoService HTML search. */
    public final int DDG_PAGE_TEXT_MAX_CHARS =
            intVal("ddg.page.text.max.chars", 6_000);

    /** Maximum page text length returned by BingService search. */
    public final int BING_PAGE_TEXT_MAX_CHARS =
            intVal("bing.page.text.max.chars", 6_000);

    /** Max chars of page content sent to LLM for summarization. */
    public final int SUMMARIZE_MAX_CHARS =
            intVal("summarize.max.chars", 8_000);

    /** Max number of items returned by extractStructuredData. */
    public final int EXTRACT_MAX_ITEMS =
            intVal("extract.max.items", 50);

    // ── FormSkill ──────────────────────────────────────────────────────────────
    /** Wait time after form submission for page to settle (ms). */
    public final int FORM_AFTER_SUBMIT_WAIT_MS =
            intVal("form.after.submit.wait.ms", 3_000);

    /** Max chars of result page content after form submission. */
    public final int FORM_RESULT_MAX_CHARS =
            intVal("form.result.max.chars", 5_000);

    // ── PdfSkill ───────────────────────────────────────────────────────────────
    /** HTTP connect timeout for PDF download (seconds). */
    public final int PDF_HTTP_TIMEOUT_SECONDS =
            intVal("pdf.http.timeout.seconds", 30);

    /** Max chars of extracted PDF text returned. */
    public final int PDF_MAX_CHARS =
            intVal("pdf.max.chars", 10_000);

    // ── LLM / Model ────────────────────────────────────────────────────────────
    /** Gemini model name passed to LlmAgent. Override via {@code llm.model} property. */
    public final String LLM_MODEL = str("llm.model", "gemini-2.5-flash");

    /**
     * Max LLM calls per user turn for the conversational SearchAgent (RunConfig).
     * Prevents infinite loops from ambiguous instructions or model drift.
     */
    public final int LLM_MAX_CALLS_AGENT =
            intVal("llm.max.calls.agent", 20);

    /**
     * Max LLM calls for a single deepResearch pipeline (RunConfig).
     * The research pipeline has 3 sub-agents; each may invoke tools multiple times.
     */
    public final int LLM_MAX_CALLS_RESEARCH =
            intVal("llm.max.calls.research", 30);

    // ── Retry ──────────────────────────────────────────────────────────────────
    /** Total number of attempts for retry operations (1 = no retry). */
    public final int RETRY_MAX_ATTEMPTS =
            intVal("retry.max.attempts", 3);

    /** Initial delay before the second attempt (ms). Doubles on each retry. */
    public final long RETRY_INITIAL_DELAY_MS =
            intVal("retry.initial.delay.ms", 500);

    // ── Locale / Region ────────────────────────────────────────────────────────
    /** BCP 47 locale tag for the browser context (e.g. pt-BR, en-US). */
    public final String LOCALE = str("browser.locale", "pt-BR");

    /** DuckDuckGo region code for the kl= parameter (e.g. br-pt, us-en). */
    public final String DDG_REGION = str("ddg.region", "br-pt");

    // ── Log management ─────────────────────────────────────────────────────────
    /** Maximum number of session log files to keep (oldest deleted when exceeded). */
    public final int LOG_MAX_FILES = intVal("log.max.files", 10);

    /** Maximum size of a single log file in KB before rotation (0 = disabled). */
    public final int LOG_MAX_SIZE_KB = intVal("log.max.size.kb", 512);

    // ── Search Cache ───────────────────────────────────────────────────────────
    /** Enables the in-memory cache for searchWeb results. */
    public final boolean SEARCH_CACHE_ENABLED =
            boolVal("search.cache.enabled", true);

    /** Maximum number of queries in the cache (LRU eviction when reached). */
    public final int SEARCH_CACHE_MAX_SIZE =
            intVal("search.cache.max.size", 500);

    /** Time-to-live for each cache entry in minutes. */
    public final int SEARCH_CACHE_TTL_MINUTES =
            intVal("search.cache.ttl.minutes", 60);

    // ── Search Pipeline ────────────────────────────────────────────────────────
    /** Run DDG HTML + Bing in parallel via CompletableFuture (default false = sequential). */
    public final boolean SEARCH_PARALLEL_ENABLED =
            boolVal("search.parallel.enabled", false);

    /** Timeout for the parallel CompletableFuture.allOf() call (ms). */
    public final int SEARCH_PARALLEL_TIMEOUT_MS =
            intVal("search.parallel.timeout.ms", 25_000);

    /** Number of consecutive DDG HTML failures before the circuit opens. */
    public final int SEARCH_CIRCUIT_DDG_FAILURE_THRESHOLD =
            intVal("search.circuit.ddg.failure.threshold", 5);

    /** Time (ms) the DDG circuit stays OPEN before transitioning to HALF_OPEN. */
    public final int SEARCH_CIRCUIT_DDG_RESET_MS =
            intVal("search.circuit.ddg.reset.ms", 30_000);

    // ── Telegram ───────────────────────────────────────────────────────────────
    /** Interface mode: "cli" (default) or "telegram". */
    public final String INTERFACE_MODE =
            str("interface.mode", "cli");

    /**
     * Telegram Bot Token from @BotFather.
     * Falls back to the {@code TELEGRAM_BOT_TOKEN} environment variable so the
     * token is never committed to source control.
     * Required when {@code INTERFACE_MODE} is {@code "telegram"}.
     */
    public final String TELEGRAM_BOT_TOKEN = resolveTelegramToken();

    /** Telegram update mode: "polling" (default, no server needed) or "webhook". */
    public final String TELEGRAM_MODE =
            str("telegram.mode", "polling");

    /** Port for the JDK HttpServer in webhook mode. */
    public final int TELEGRAM_WEBHOOK_PORT =
            intVal("telegram.webhook.port", 8443);

    /** Public HTTPS URL Telegram POSTs updates to (webhook mode only). */
    public final String TELEGRAM_WEBHOOK_URL =
            str("telegram.webhook.url", "");

    private String resolveTelegramToken() {
        String prop = props.getProperty("telegram.bot.token", "").trim();
        if (!prop.isEmpty()) return prop;
        String env = System.getenv("TELEGRAM_BOT_TOKEN");
        return (env != null) ? env.trim() : "";
    }

    // ── RssSkill ───────────────────────────────────────────────────────────────
    /** HTTP connect timeout for RSS/Atom feed fetch (seconds). */
    public final int RSS_FETCH_TIMEOUT_SECONDS =
            intVal("rss.fetch.timeout.seconds", 15);

    /** Maximum number of RSS items returned by readFeed. */
    public final int RSS_MAX_ITEMS =
            intVal("rss.max.items", 20);

    /** Maximum characters of each RSS item description returned. */
    public final int RSS_MAX_DESCRIPTION_CHARS =
            intVal("rss.max.description.chars", 500);

    // ── SchedulerSkill ─────────────────────────────────────────────────────────
    /** Maximum number of concurrent monitor jobs allowed. */
    public final int SCHEDULER_MAX_JOBS =
            intVal("scheduler.max.jobs", 20);

    /** Minimum allowed interval between job executions (minutes). */
    public final int SCHEDULER_MIN_INTERVAL_MINUTES =
            intVal("scheduler.min.interval.minutes", 5);

    /**
     * Dynamic accessor for the scheduler jobs file path.
     * Reads from {@code props} at call time, allowing test overrides.
     */
    public String schedulerJobsFile() {
        return str("scheduler.jobs.file", "work/scheduler-jobs.json");
    }

    // ── NotificationSkill ──────────────────────────────────────────────────────
    /** Telegram chat_id to send proactive notifications to. */
    public final String NOTIFICATION_TELEGRAM_CHAT_ID =
            str("notification.telegram.chat.id", "");

    /** Maximum number of notifications to hold in the in-memory queue (CLI fallback). */
    public final int NOTIFICATION_QUEUE_MAX_SIZE =
            intVal("notification.queue.max.size", 100);

    // ── MemorySkill ────────────────────────────────────────────────────────────

    /**
     * Dynamic accessor for the memory max items limit.
     * Reads from {@code props} at call time, allowing test overrides.
     */
    public int memoryMaxItems() {
        return intVal("memory.max.items", 500);
    }

    /**
     * Dynamic accessor for the memory storage file path.
     * Reads from {@code props} at call time, allowing test overrides.
     */
    public String memoryStorageFile() {
        return str("memory.storage.file", "work/memory-store.json");
    }
}
