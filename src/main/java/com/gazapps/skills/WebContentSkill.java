package com.gazapps.skills;

import com.gazapps.config.AppConfig;
import com.gazapps.logging.LogService;
import com.gazapps.services.BrowserService;
import com.gazapps.util.BrowserErrors;
import com.gazapps.util.RetryUtils;
import com.gazapps.util.SessionState;
import com.gazapps.util.SkillStatus;
import com.gazapps.util.UrlValidator;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.ToolContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

import java.util.HashMap;
import java.util.Map;

public class WebContentSkill {

    private static final LogService LOG = LogService.getInstance();

    @Schema(description = "Fetch the full text content of a web page given its URL. Use this to read articles, blog posts, documentation, or any page after getting URLs from search results.")
    public static Map<String, String> fetchPageContent(
            @Schema(name = "url", description = "The full URL of the page to read") String url,
            @Schema(name = "toolContext") ToolContext toolContext) {

        LOG.section("TOOL CALL: fetchPageContent");
        LOG.info("WebContentSkill", "URL: " + url);
        long start = System.currentTimeMillis();

        try {
            UrlValidator.validate(url);
        } catch (IllegalArgumentException e) {
            LOG.warn("WebContentSkill", "Blocked unsafe URL: " + e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("status", SkillStatus.ERROR.value());
            error.put("url", url);
            error.put("message", "URL blocked for security reasons: " + e.getMessage());
            return error;
        }

        try {
            return RetryUtils.withRetry(
                    "WebContentSkill.fetchPageContent",
                    AppConfig.RETRY_MAX_ATTEMPTS,
                    AppConfig.RETRY_INITIAL_DELAY_MS,
                    () -> {
                        // BrowserService.execute wraps any internal exception in RuntimeException.
                        // We catch it here (before RetryUtils sees it as an unhandled throw) so
                        // the retry predicate can evaluate the returned error-map instead.
                        try {
                            return BrowserService.execute(page -> {
                                try {
                                    page.navigate(url,
                                            new Page.NavigateOptions()
                                                    .setTimeout(AppConfig.FETCH_PAGE_NAVIGATE_TIMEOUT_MS));
                                    // Wait for NETWORKIDLE so JS-rendered content (prices, dynamic
                                    // data) is fully loaded before extraction.
                                    // Gracefully degrade if the site keeps long-polling and never
                                    // reaches idle within the configured window.
                                    try {
                                        page.waitForLoadState(LoadState.NETWORKIDLE,
                                                new Page.WaitForLoadStateOptions()
                                                        .setTimeout(AppConfig.FETCH_PAGE_NETWORKIDLE_TIMEOUT_MS));
                                    } catch (Exception networkIdleEx) {
                                        LOG.debug("WebContentSkill",
                                                "NETWORKIDLE not reached within "
                                                + AppConfig.FETCH_PAGE_NETWORKIDLE_TIMEOUT_MS
                                                + " ms — proceeding with available content");
                                    }
                                    LOG.timing("WebContentSkill", "Navigation",
                                            System.currentTimeMillis() - start);

                                    // Remove noise before text extraction
                                    page.evaluate(
                                            """
                                                    document.querySelectorAll(
                                                        'script,style,nav,footer,header,aside,iframe,' +
                                                        '[aria-hidden="true"],[class*="cookie"],[class*="banner"],[class*="popup"]'
                                                    ).forEach(el => el.remove());
                                                    """);

                                    String title = page.title();
                                    String bodyText = page.innerText("body");
                                    int rawLen = bodyText.length();

                                    LOG.info("WebContentSkill", "Page title: \"" + title + "\"");
                                    LOG.info("WebContentSkill",
                                            "Raw body text length: " + rawLen + " chars");

                                    // Detect bot-blocking interstitials (e.g. Amazon "continuar
                                    // comprando", Cloudflare challenges). Return a descriptive error
                                    // so the LLM knows to fall back to search-result snippets.
                                    if (rawLen < 500 && isBotBlockPage(bodyText)) {
                                        LOG.warn("WebContentSkill",
                                                "Bot-blocking page detected for: " + url);
                                        Map<String, String> botErr = new HashMap<>();
                                        botErr.put("status",     SkillStatus.ERROR.value());
                                        botErr.put("url",        url);
                                        botErr.put("error_type", "BOT_DETECTED");
                                        botErr.put("message",
                                                "The site is blocking automated access. "
                                                + "Use search result snippets instead of fetching this URL directly.");
                                        return botErr;
                                    }

                                    if (bodyText.length() > AppConfig.FETCH_PAGE_MAX_CHARS) {
                                        bodyText = bodyText.substring(0, AppConfig.FETCH_PAGE_MAX_CHARS)
                                                + "... [content truncated]";
                                        LOG.debug("WebContentSkill",
                                                "Body text truncated to " + AppConfig.FETCH_PAGE_MAX_CHARS
                                                        + " chars");
                                    }

                                    Map<String, String> result = new HashMap<>();
                                    result.put("status", SkillStatus.SUCCESS.value());
                                    result.put("url", url);
                                    result.put("title", title);
                                    result.put("content", bodyText);

                                    // Save to session.state so SummarizeSkill/FormSkill can reuse
                                    SessionState.put(toolContext, SessionState.KEY_LAST_FETCH_URL, url);
                                    SessionState.put(toolContext, SessionState.KEY_LAST_FETCH_CONTENT,
                                            bodyText);

                                    LOG.timing("WebContentSkill", "Total execution",
                                            System.currentTimeMillis() - start);
                                    LOG.toolResult("fetchPageContent", result);
                                    return result;

                                } catch (Exception inner) {
                                    // Caught inside the Page lambda — BrowserService will
                                    // re-wrap this in RuntimeException; we absorb it below.
                                    LOG.error("WebContentSkill", "Page error for " + url, inner);
                                    Map<String, String> err = new HashMap<>();
                                    err.put("status", SkillStatus.ERROR.value());
                                    err.put("url", url);
                                    err.put("error_type", BrowserErrors.classify(inner));
                                    err.put("message", "Failed to fetch page: " + inner.getMessage());
                                    return err;
                                }
                            });
                        } catch (RuntimeException re) {
                            // BrowserService.execute wraps all exceptions in RuntimeException.
                            // Absorb it here and return an error-map so RetryUtils can retry.
                            Throwable cause = re.getCause() != null ? re.getCause() : re;
                            LOG.error("WebContentSkill", "Failed for " + url, cause);
                            Map<String, String> err = new HashMap<>();
                            err.put("status", SkillStatus.ERROR.value());
                            err.put("url", url);
                            err.put("error_type", BrowserErrors.classify(cause));
                            err.put("message", "Failed to fetch page: " + cause.getMessage());
                            return err;
                        }
                    },
                    // Do NOT retry bot-blocking pages — no amount of retries will bypass them.
                    result -> SkillStatus.ERROR.value().equals(result.get("status"))
                            && !"BOT_DETECTED".equals(result.get("error_type")));
        } catch (Throwable t) {
            // Defense: catch absolutely anything that escapes — RetryUtils, reflection,
            // etc.
            LOG.error("WebContentSkill", "Unhandled error for " + url + " after retries", t);
            Map<String, String> error = new HashMap<>();
            error.put("status", SkillStatus.ERROR.value());
            error.put("url", url);
            error.put("error_type", BrowserErrors.classify(t));
            error.put("message", "Failed to fetch page: " + t.getMessage());
            return error;
        }
    }

    /**
     * Returns {@code true} if the page body looks like a bot-blocking interstitial
     * (Cloudflare challenge, Amazon CAPTCHA, generic "access denied" pages, etc.).
     * Only called when {@code rawLen < 500} to avoid false positives on thin pages.
     */
    private static boolean isBotBlockPage(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return lower.contains("continuar comprando")
                || lower.contains("clique no botão abaixo")
                || lower.contains("verify you are human")
                || lower.contains("enable javascript and cookies")
                || lower.contains("enable javascript")
                || lower.contains("access denied")
                || lower.contains("acesso negado")
                || lower.contains("robot check")
                || lower.contains("checking your browser")
                || lower.contains("captcha")
                || lower.contains("just a moment")
                || lower.contains("before you continue");
    }

    @Schema(description = "Take a screenshot of a web page and save it locally. Returns the saved file path.")
    public static Map<String, String> screenshotPage(
            @Schema(name = "url", description = "The full URL of the page to screenshot") String url,
            @Schema(name = "filename", description = "Output filename, e.g. 'result.png'") String filename) {

        LOG.section("TOOL CALL: screenshotPage");
        LOG.info("WebContentSkill", "URL: " + url + "  filename: " + filename);
        long start = System.currentTimeMillis();

        try {
            UrlValidator.validate(url);
        } catch (IllegalArgumentException e) {
            LOG.warn("WebContentSkill", "Blocked unsafe URL for screenshot: " + e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("status", SkillStatus.ERROR.value());
            error.put("url", url);
            error.put("message", "URL blocked for security reasons: " + e.getMessage());
            return error;
        }

        return BrowserService.execute(page -> {
            try {
                page.setViewportSize(1280, 800);
                page.navigate(url, new Page.NavigateOptions().setTimeout(AppConfig.SCREENSHOT_NAVIGATE_TIMEOUT_MS));
                page.waitForLoadState(LoadState.NETWORKIDLE);
                LOG.timing("WebContentSkill", "Navigation", System.currentTimeMillis() - start);

                String safeFile = filename.replaceAll("[^a-zA-Z0-9._\\-]", "_");
                if (!safeFile.endsWith(".png"))
                    safeFile += ".png";

                java.nio.file.Path outputPath = com.gazapps.services.BrowserService.ensureDir(AppConfig.SCREENSHOTS_DIR)
                        .resolve(safeFile);
                page.screenshot(new Page.ScreenshotOptions().setPath(outputPath).setFullPage(false));

                LOG.info("WebContentSkill", "Screenshot saved: " + outputPath.toAbsolutePath());

                Map<String, String> result = new HashMap<>();
                result.put("status", SkillStatus.SUCCESS.value());
                result.put("url", url);
                result.put("file", outputPath.toString());
                result.put("title", page.title());

                LOG.timing("WebContentSkill", "Total execution", System.currentTimeMillis() - start);
                LOG.toolResult("screenshotPage", result);
                return result;

            } catch (Exception e) {
                LOG.error("WebContentSkill", "Screenshot failed", e);
                Map<String, String> error = new HashMap<>();
                error.put("status", SkillStatus.ERROR.value());
                error.put("url", url);
                error.put("error_type", BrowserErrors.classify(e));
                error.put("message", "Screenshot failed: " + e.getMessage());
                return error;
            }
        });
    }
}
