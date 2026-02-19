package com.gazapps.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Central configuration for the AI Internet Search application.
 * Values are loaded from {@code application.properties} on the classpath.
 * Changing the file and restarting the app applies new values without recompiling.
 *
 * <p>All timeout values are in milliseconds unless the field name states otherwise.</p>
 */
public final class AppConfig {

    private static final Properties PROPS = load();

    private AppConfig() {}

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

    private static String str(String key, String defaultVal) {
        return PROPS.getProperty(key, defaultVal);
    }

    private static int intVal(String key, int defaultVal) {
        String val = PROPS.getProperty(key);
        if (val == null) return defaultVal;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            System.err.println("[AppConfig] Invalid integer for key '" + key + "': " + val
                    + " — using default " + defaultVal);
            return defaultVal;
        }
    }

    // ── HTTP / Browser User-Agent ──────────────────────────────────────────────
    public static final String USER_AGENT = str("browser.user-agent",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/133.0.0.0 Safari/537.36");

    // ── HTTP client timeouts ───────────────────────────────────────────────────
    /** Connect timeout for the DDG JSON instant-answer HTTP client (seconds). */
    public static final int HTTP_CONNECT_TIMEOUT_SECONDS =
            intVal("http.connect.timeout.seconds", 8);

    // ── Playwright navigation timeouts (milliseconds) ─────────────────────────
    /** Timeout for fetchPageContent navigation. */
    public static final int FETCH_PAGE_NAVIGATE_TIMEOUT_MS =
            intVal("fetch.page.navigate.timeout.ms", 20_000);

    /** Timeout for screenshotPage navigation. */
    public static final int SCREENSHOT_NAVIGATE_TIMEOUT_MS =
            intVal("screenshot.navigate.timeout.ms", 15_000);

    /** Navigation timeout for summarizeUrl. */
    public static final int SUMMARIZE_NAVIGATE_TIMEOUT_MS =
            intVal("summarize.navigate.timeout.ms", 20_000);

    /** Navigation timeout for extractStructuredData. */
    public static final int EXTRACT_NAVIGATE_TIMEOUT_MS =
            intVal("extract.navigate.timeout.ms", 20_000);

    /** Navigation timeout for fillFormAndSubmit. */
    public static final int FORM_NAVIGATE_TIMEOUT_MS =
            intVal("form.navigate.timeout.ms", 20_000);

    // ── Content truncation limits (characters) ────────────────────────────────
    /** Maximum body text length returned by fetchPageContent. */
    public static final int FETCH_PAGE_MAX_CHARS =
            intVal("fetch.page.max.chars", 5_000);

    /** Maximum page text length returned by DuckDuckGoService HTML search. */
    public static final int DDG_PAGE_TEXT_MAX_CHARS =
            intVal("ddg.page.text.max.chars", 6_000);

    /** Maximum page text length returned by BingService search. */
    public static final int BING_PAGE_TEXT_MAX_CHARS =
            intVal("bing.page.text.max.chars", 6_000);

    /** Max chars of page content sent to LLM for summarization. */
    public static final int SUMMARIZE_MAX_CHARS =
            intVal("summarize.max.chars", 8_000);

    /** Max number of items returned by extractStructuredData. */
    public static final int EXTRACT_MAX_ITEMS =
            intVal("extract.max.items", 50);

    // ── FormSkill ──────────────────────────────────────────────────────────────
    /** Wait time after form submission for page to settle (ms). */
    public static final int FORM_AFTER_SUBMIT_WAIT_MS =
            intVal("form.after.submit.wait.ms", 3_000);

    /** Max chars of result page content after form submission. */
    public static final int FORM_RESULT_MAX_CHARS =
            intVal("form.result.max.chars", 5_000);

    // ── PdfSkill ───────────────────────────────────────────────────────────────
    /** HTTP connect timeout for PDF download (seconds). */
    public static final int PDF_HTTP_TIMEOUT_SECONDS =
            intVal("pdf.http.timeout.seconds", 30);

    /** Max chars of extracted PDF text returned. */
    public static final int PDF_MAX_CHARS =
            intVal("pdf.max.chars", 10_000);

    // ── Retry ──────────────────────────────────────────────────────────────────
    /** Número total de tentativas para operações com retry (1 = sem retry). */
    public static final int RETRY_MAX_ATTEMPTS =
            intVal("retry.max.attempts", 3);

    /** Delay inicial antes da segunda tentativa (ms). Dobra a cada tentativa. */
    public static final long RETRY_INITIAL_DELAY_MS =
            intVal("retry.initial.delay.ms", 500);

    // ── Locale / Region ────────────────────────────────────────────────────────
    /** Tag de locale BCP 47 para o contexto do browser (ex: pt-BR, en-US). */
    public static final String LOCALE = str("browser.locale", "pt-BR");

    /** Código de região do DuckDuckGo para o parâmetro kl= (ex: br-pt, us-en). */
    public static final String DDG_REGION = str("ddg.region", "br-pt");
}
