package com.gazapps.services;

import com.gazapps.config.AppConfig;
import com.gazapps.logging.LogService;
import com.microsoft.playwright.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Handles Playwright browser creation and execution with anti-detection
 * measures.
 * Follows KISS & DRY principles by centralizing browser logic.
 *
 * <p>User-Agent rotation: on each {@link #execute} call a different UA profile
 * is selected from a built-in pool of 5 realistic profiles (Chrome Win/Mac/Linux,
 * Edge Win, Safari Mac). If the operator has overridden {@code browser.user-agent}
 * in {@code application.properties} with a custom value, that single UA is used
 * for every call (no rotation).</p>
 */
public class BrowserService {

    private static final LogService LOG = LogService.getInstance();

    // ── User-Agent rotation ───────────────────────────────────────────────────

    /**
     * Internal UA profile. Carries the UA string together with matching
     * {@code sec-ch-ua} and {@code sec-ch-ua-platform} hint headers so
     * the browser context stays internally consistent.
     */
    record UaProfile(String userAgent, String secChUa, String secChUaPlatform) {}

    /**
     * Default UA used by AppConfig when no override is set in application.properties.
     * Must stay in sync with the default value in AppConfig.
     */
    static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/133.0.0.0 Safari/537.36";

    static final List<UaProfile> UA_POOL = List.of(
        new UaProfile(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36",
            "\"Not(A:Brand\";v=\"99\", \"Google Chrome\";v=\"133\", \"Chromium\";v=\"133\"",
            "\"Windows\""),
        new UaProfile(
            DEFAULT_USER_AGENT,
            "\"Not(A:Brand\";v=\"99\", \"Google Chrome\";v=\"133\", \"Chromium\";v=\"133\"",
            "\"macOS\""),
        new UaProfile(
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36",
            "\"Not(A:Brand\";v=\"99\", \"Google Chrome\";v=\"133\", \"Chromium\";v=\"133\"",
            "\"Linux\""),
        new UaProfile(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36 Edg/133.0.0.0",
            "\"Not(A:Brand\";v=\"99\", \"Microsoft Edge\";v=\"133\", \"Chromium\";v=\"133\"",
            "\"Windows\""),
        new UaProfile(
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.3 Safari/605.1.15",
            "\"Not(A:Brand\";v=\"99\"",
            "\"macOS\"")
    );

    /** Monotonically increasing index; modulo UA_POOL.size() gives round-robin position. */
    static final AtomicInteger UA_INDEX = new AtomicInteger(0);

    /**
     * Returns the next {@link UaProfile} from the pool (round-robin).
     * If the operator has set a custom UA in {@code application.properties},
     * that UA is returned unchanged (no rotation).
     */
    static UaProfile nextUaProfile() {
        if (!AppConfig.getInstance().USER_AGENT.equals(DEFAULT_USER_AGENT)) {
            // Custom UA configured — use it as-is with generic hint headers
            return new UaProfile(
                AppConfig.getInstance().USER_AGENT,
                "\"Not(A:Brand\";v=\"99\"",
                "\"Windows\""
            );
        }
        int idx = Math.abs(UA_INDEX.getAndIncrement() % UA_POOL.size());
        return UA_POOL.get(idx);
    }

    // ── Execute ───────────────────────────────────────────────────────────────

    /**
     * Executes a browser action within a stealth Playwright context and returns a
     * result.
     * Automatically handles resource cleanup (try-with-resources).
     *
     * @param action The function to execute with the page.
     * @param <T>    The return type.
     * @return The result of the action.
     */
    public static <T> T execute(Function<Page, T> action) {
        try (Playwright playwright = Playwright.create()) {
            BrowserContext context = launchStealthContext(playwright);
            Page page = context.newPage();

            // Setup centralized logging
            page.onRequest(req -> {
                if (req.isNavigationRequest())
                    LOG.debug("BrowserService", "[NAV→] " + req.method() + " " + req.url());
            });
            page.onResponse(res -> {
                if (res.request().isNavigationRequest()) {
                    int status = res.status();
                    if (status >= 400)
                        LOG.warn("BrowserService", "[NAV←] HTTP " + status + " " + res.url());
                    else
                        LOG.debug("BrowserService", "[NAV←] HTTP " + status + " " + res.url());
                }
            });

            T result = action.apply(page);
            context.close();
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Browser execution failed: " + e.getMessage(), e);
        }
    }

    private static BrowserContext launchStealthContext(Playwright playwright) {
        Path downloadsDir = ensureDir(AppConfig.getInstance().DOWNLOADS_DIR);

        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setDownloadsPath(downloadsDir)
                .setArgs(java.util.List.of(
                        "--disable-blink-features=AutomationControlled",
                        "--no-sandbox",
                        "--disable-setuid-sandbox"));

        Browser browser = playwright.chromium().launch(launchOptions);

        UaProfile ua = nextUaProfile();
        LOG.debug("BrowserService", "UA profile: " + ua.userAgent().substring(0, Math.min(60, ua.userAgent().length())) + "…");

        Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                .setUserAgent(ua.userAgent())
                .setLocale(AppConfig.getInstance().LOCALE)
                .setAcceptDownloads(true)
                .setExtraHTTPHeaders(Map.of(
                        "Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8",
                        "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                        "sec-ch-ua",          ua.secChUa(),
                        "sec-ch-ua-mobile",   "?0",
                        "sec-ch-ua-platform", ua.secChUaPlatform(),
                        "Upgrade-Insecure-Requests", "1"));

        BrowserContext context = browser.newContext(contextOptions);

        // Stealth script to mask webdriver
        context.addInitScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined});");

        return context;
    }

    /**
     * Creates the directory at {@code dirPath} if it does not exist and returns its {@link Path}.
     * Logs a warning to stderr if creation fails (non-fatal — Playwright will fall back to a temp dir).
     */
    public static Path ensureDir(String dirPath) {
        Path dir = Paths.get(dirPath);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            System.err.println("[BrowserService] Could not create directory " + dirPath + ": " + e.getMessage());
        }
        return dir;
    }
}
