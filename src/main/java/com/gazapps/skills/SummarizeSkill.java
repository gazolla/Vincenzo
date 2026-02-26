package com.gazapps.skills;

import com.gazapps.config.AppConfig;
import com.gazapps.logging.LogService;
import com.gazapps.services.BrowserService;
import com.gazapps.util.BrowserErrors;
import com.gazapps.util.RetryUtils;
import com.gazapps.util.SessionState;
import com.gazapps.util.SkillStatus;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.ToolContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * ADK tool that fetches and cleans a web page's content for summarization.
 * The LLM (Gemini) performs the actual summarization; this skill provides
 * clean, noise-free text with a higher character limit than fetchPageContent.
 */
public class SummarizeSkill {

    private static final LogService LOG = LogService.getInstance();

    @Schema(description = """
            Fetch and clean the full text content of a web page for summarization.
            Use this when the user asks to summarize, review, or get the key points
            of a specific URL or article. Returns cleaned text without scripts,
            ads, or navigation elements, ready for the LLM to summarize.
            """)
    public static Map<String, String> summarizeUrl(
            @Schema(name = "url", description = "The full URL of the page to summarize") String url,
            @Schema(name = "toolContext") ToolContext toolContext) {

        LOG.section("TOOL CALL: summarizeUrl");
        LOG.info("SummarizeSkill", "URL: " + url);
        long start = System.currentTimeMillis();

        // Fast-path: reuse content already fetched by fetchPageContent for the same URL
        Optional<String> cachedContent = SessionState.getString(toolContext, SessionState.KEY_LAST_FETCH_CONTENT);
        String cachedUrl = SessionState.getString(toolContext, SessionState.KEY_LAST_FETCH_URL).orElse("");
        if (cachedContent.isPresent() && url.equals(cachedUrl)) {
            LOG.info("SummarizeSkill", "Fast-path: reusing content from session.state for " + url);
            Map<String, String> result = new HashMap<>();
            result.put("status", SkillStatus.SUCCESS.value());
            result.put("url", url);
            result.put("title", "");
            result.put("content", cachedContent.get());
            result.put("char_count", String.valueOf(cachedContent.get().length()));
            result.put("source", "session_cache");
            LOG.toolResult("summarizeUrl", result);
            return result;
        }

        try {
            return RetryUtils.withRetry(
                    "SummarizeSkill.summarizeUrl",
                    AppConfig.RETRY_MAX_ATTEMPTS,
                    AppConfig.RETRY_INITIAL_DELAY_MS,
                    () -> BrowserService.execute(page -> {
                        try {
                            page.navigate(url, new Page.NavigateOptions()
                                    .setTimeout(AppConfig.SUMMARIZE_NAVIGATE_TIMEOUT_MS));
                            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                            LOG.timing("SummarizeSkill", "Navigation", System.currentTimeMillis() - start);

                            // Remove noise before text extraction
                            page.evaluate("""
                                    document.querySelectorAll(
                                        'script,style,nav,footer,header,aside,iframe,' +
                                        '[aria-hidden="true"],[class*="cookie"],[class*="banner"],' +
                                        '[class*="popup"],[class*="ad-"],[class*="sidebar"]'
                                    ).forEach(el => el.remove());
                                    """);

                            String title = page.title();
                            String bodyText = page.innerText("body");
                            int rawLen = bodyText.length();

                            LOG.info("SummarizeSkill", "Page title: \"" + title + "\"");
                            LOG.info("SummarizeSkill", "Raw body text length: " + rawLen + " chars");

                            if (bodyText.length() > AppConfig.SUMMARIZE_MAX_CHARS) {
                                bodyText = bodyText.substring(0, AppConfig.SUMMARIZE_MAX_CHARS)
                                        + "... [content truncated]";
                                LOG.debug("SummarizeSkill", "Body text truncated to "
                                        + AppConfig.SUMMARIZE_MAX_CHARS + " chars");
                            }

                            Map<String, String> result = new HashMap<>();
                            result.put("status", SkillStatus.SUCCESS.value());
                            result.put("url", url);
                            result.put("title", title);
                            result.put("content", bodyText);
                            result.put("char_count", String.valueOf(rawLen));

                            LOG.timing("SummarizeSkill", "Total execution", System.currentTimeMillis() - start);
                            LOG.toolResult("summarizeUrl", result);
                            return result;

                        } catch (Exception e) {
                            LOG.error("SummarizeSkill", "Failed for " + url, e);
                            Map<String, String> error = new HashMap<>();
                            error.put("status", SkillStatus.ERROR.value());
                            error.put("url", url);
                            error.put("error_type", BrowserErrors.classify(e));
                            error.put("message", "Failed to fetch page for summarization: " + e.getMessage());
                            return error;
                        }
                    }),
                    result -> SkillStatus.ERROR.value().equals(result.get("status")));
        } catch (Exception e) {
            LOG.error("SummarizeSkill", "Failed for " + url + " after retries", e);
            Map<String, String> error = new HashMap<>();
            error.put("status", SkillStatus.ERROR.value());
            error.put("url", url);
            error.put("error_type", BrowserErrors.classify(e));
            error.put("message", "Failed to fetch page for summarization: " + e.getMessage());
            return error;
        }
    }
}
