package com.gazapps.skills;

import com.gazapps.config.AppConfig;
import com.gazapps.logging.LogService;
import com.gazapps.services.BrowserService;
import com.gazapps.util.RetryUtils;
import com.gazapps.util.UrlValidator;
import com.google.adk.tools.Annotations.Schema;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

import java.util.HashMap;
import java.util.Map;

public class WebContentSkill {

    private static final LogService LOG = LogService.getInstance();

    @Schema(description = "Fetch the full text content of a web page given its URL. Use this to read articles, blog posts, documentation, or any page after getting URLs from search results.")
    public static Map<String, String> fetchPageContent(
            @Schema(name = "url", description = "The full URL of the page to read") String url) {

        LOG.section("TOOL CALL: fetchPageContent");
        LOG.info("WebContentSkill", "URL: " + url);
        long start = System.currentTimeMillis();

        try {
            UrlValidator.validate(url);
        } catch (IllegalArgumentException e) {
            LOG.warn("WebContentSkill", "Blocked unsafe URL: " + e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("status", "error");
            error.put("url", url);
            error.put("message", "URL blocked for security reasons: " + e.getMessage());
            return error;
        }

        try {
            return RetryUtils.withRetry(
                    "WebContentSkill.fetchPageContent",
                    AppConfig.RETRY_MAX_ATTEMPTS,
                    AppConfig.RETRY_INITIAL_DELAY_MS,
                    () -> BrowserService.execute(page -> {
                        try {
                            page.navigate(url, new Page.NavigateOptions().setTimeout(AppConfig.FETCH_PAGE_NAVIGATE_TIMEOUT_MS));
                            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                            LOG.timing("WebContentSkill", "Navigation", System.currentTimeMillis() - start);

                            // Remove noise before text extraction
                            page.evaluate("""
                                    document.querySelectorAll(
                                        'script,style,nav,footer,header,aside,iframe,' +
                                        '[aria-hidden="true"],[class*="cookie"],[class*="banner"],[class*="popup"]'
                                    ).forEach(el => el.remove());
                                    """);

                            String title = page.title();
                            String bodyText = page.innerText("body");
                            int rawLen = bodyText.length();

                            LOG.info("WebContentSkill", "Page title: \"" + title + "\"");
                            LOG.info("WebContentSkill", "Raw body text length: " + rawLen + " chars");

                            if (bodyText.length() > AppConfig.FETCH_PAGE_MAX_CHARS) {
                                bodyText = bodyText.substring(0, AppConfig.FETCH_PAGE_MAX_CHARS) + "... [content truncated]";
                                LOG.debug("WebContentSkill", "Body text truncated to " + AppConfig.FETCH_PAGE_MAX_CHARS + " chars");
                            }

                            Map<String, String> result = new HashMap<>();
                            result.put("status", "success");
                            result.put("url", url);
                            result.put("title", title);
                            result.put("content", bodyText);

                            LOG.timing("WebContentSkill", "Total execution", System.currentTimeMillis() - start);
                            LOG.toolResult("fetchPageContent", result);
                            return result;

                        } catch (Exception e) {
                            LOG.error("WebContentSkill", "Failed for " + url, e);
                            Map<String, String> error = new HashMap<>();
                            error.put("status", "error");
                            error.put("url", url);
                            error.put("message", "Failed to fetch page: " + e.getMessage());
                            return error;
                        }
                    }),
                    result -> "error".equals(result.get("status"))
            );
        } catch (Exception e) {
            LOG.error("WebContentSkill", "Failed for " + url + " after retries", e);
            Map<String, String> error = new HashMap<>();
            error.put("status", "error");
            error.put("url", url);
            error.put("message", "Failed to fetch page: " + e.getMessage());
            return error;
        }
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
            error.put("status", "error");
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

                java.nio.file.Path outputPath = com.gazapps.services.BrowserService.ensureDir(AppConfig.SCREENSHOTS_DIR).resolve(safeFile);
                page.screenshot(new Page.ScreenshotOptions().setPath(outputPath).setFullPage(false));

                LOG.info("WebContentSkill", "Screenshot saved: " + outputPath.toAbsolutePath());

                Map<String, String> result = new HashMap<>();
                result.put("status", "success");
                result.put("url", url);
                result.put("file", outputPath.toString());
                result.put("title", page.title());

                LOG.timing("WebContentSkill", "Total execution", System.currentTimeMillis() - start);
                LOG.toolResult("screenshotPage", result);
                return result;

            } catch (Exception e) {
                LOG.error("WebContentSkill", "Screenshot failed", e);
                Map<String, String> error = new HashMap<>();
                error.put("status", "error");
                error.put("message", "Screenshot failed: " + e.getMessage());
                return error;
            }
        });
    }
}
