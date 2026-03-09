package com.gazapps.services;

import com.gazapps.config.AppConfig;
import com.gazapps.logging.LogService;
import com.gazapps.util.RetryUtils;
import com.gazapps.util.StringUtils;
import com.microsoft.playwright.options.LoadState;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class BingService {

    private static final LogService LOG = LogService.getInstance();

    public static Map<String, String> search(String query) {
        String searchUrl = "https://www.bing.com/search?q="
                + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&setlang=pt-BR&count=8";
        LOG.debug("BingService", "Bing URL: " + searchUrl);

        try {
            return RetryUtils.withRetry(
                    "BingService.search",
                    AppConfig.getInstance().RETRY_MAX_ATTEMPTS,
                    AppConfig.getInstance().RETRY_INITIAL_DELAY_MS,
                    () -> BrowserService.execute(page -> {
                        try {
                            long navStart = System.currentTimeMillis();
                            page.navigate(searchUrl);
                            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                            page.waitForTimeout(1000);
                            LOG.timing("BingService", "Page load", System.currentTimeMillis() - navStart);

                            String extractScript = """
                                    (function() {
                                        var results = [];
                                        var items = document.querySelectorAll('li.b_algo');
                                        for (var i = 0; i < items.length && i < 8; i++) {
                                            var titleEl = items[i].querySelector('h2 a');
                                            var snipEl  = items[i].querySelector('p, .b_caption p');
                                            if (titleEl) {
                                                results.push({
                                                    title:   titleEl.innerText.trim(),
                                                    url:     titleEl.href,
                                                    snippet: snipEl ? snipEl.innerText.trim().substring(0, 400) : ''
                                                });
                                            }
                                        }
                                        return JSON.stringify(results);
                                    })()
                                    """;

                            Object raw = page.evaluate(extractScript);
                            String json = raw != null ? raw.toString() : "[]";
                            LOG.debug("BingService", "Structured results: " + StringUtils.truncate(json, 400));

                            String pageText = page.innerText("body");
                            LOG.info("BingService", "Page_text length: " + pageText.length() + " chars");
                            if (pageText.length() > AppConfig.getInstance().BING_PAGE_TEXT_MAX_CHARS)
                                pageText = pageText.substring(0, AppConfig.getInstance().BING_PAGE_TEXT_MAX_CHARS) + "... [truncated]";

                            Map<String, String> result = new HashMap<>();
                            result.put("query", query);
                            result.put("engine", "Bing");
                            result.put("results", json);
                            result.put("page_text", pageText);
                            result.put("status", "success");
                            return result;

                        } catch (Exception e) {
                            LOG.error("BingService", "Search failed", e);
                            Map<String, String> error = new HashMap<>();
                            error.put("status", "error");
                            error.put("engine", "Bing");
                            error.put("message", e.getMessage());
                            return error;
                        }
                    }),
                    result -> "error".equals(result.get("status"))
            );
        } catch (Exception e) {
            LOG.error("BingService", "Search failed after retries", e);
            Map<String, String> error = new HashMap<>();
            error.put("status", "error");
            error.put("engine", "Bing");
            error.put("message", e.getMessage());
            return error;
        }
    }

}
