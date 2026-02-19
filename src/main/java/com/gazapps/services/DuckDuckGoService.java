package com.gazapps.services;

import com.gazapps.config.AppConfig;
import com.gazapps.logging.LogService;
import com.gazapps.util.RetryUtils;
import com.gazapps.util.StringUtils;
import com.microsoft.playwright.options.LoadState;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class DuckDuckGoService {

    private static final LogService LOG = LogService.getInstance();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(AppConfig.HTTP_CONNECT_TIMEOUT_SECONDS))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public static String fetchInstantAnswer(String query) {
        String url = "https://api.duckduckgo.com/?q="
                + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&format=json&no_html=1&skip_disambig=1";
        LOG.debug("DuckDuckGoService", "JSON URL: " + url);
        // Build request once — HttpRequest is immutable and safe to reuse across retries
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", AppConfig.USER_AGENT)
                .header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
                .GET()
                .build();
        try {
            return RetryUtils.withRetry(
                    "DuckDuckGo.fetchInstantAnswer",
                    AppConfig.RETRY_MAX_ATTEMPTS,
                    AppConfig.RETRY_INITIAL_DELAY_MS,
                    () -> {
                        long t0 = System.currentTimeMillis();
                        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                        LOG.timing("DuckDuckGoService", "JSON API call", System.currentTimeMillis() - t0);
                        if (resp.statusCode() == 200) return resp.body();
                        LOG.warn("DuckDuckGoService", "JSON API returned HTTP " + resp.statusCode());
                        return null;
                    },
                    result -> result == null  // null = falha → retry
            );
        } catch (Exception e) {
            LOG.error("DuckDuckGoService", "JSON API failed after retries", e);
            return null;
        }
    }

    public static Map<String, String> searchHtml(String query) {
        String searchUrl = "https://html.duckduckgo.com/html/?q="
                + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&kl=" + AppConfig.DDG_REGION;
        LOG.debug("DuckDuckGoService", "HTML URL: " + searchUrl);

        try {
            return RetryUtils.withRetry(
                    "DuckDuckGo.searchHtml",
                    AppConfig.RETRY_MAX_ATTEMPTS,
                    AppConfig.RETRY_INITIAL_DELAY_MS,
                    () -> BrowserService.execute(page -> {
                        try {
                            long navStart = System.currentTimeMillis();
                            page.navigate(searchUrl);
                            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                            LOG.timing("DuckDuckGoService", "HTML page load", System.currentTimeMillis() - navStart);

                            // Extract result links — DDG HTML uses <a class="result__a">
                            String extractScript = """
                                    (function() {
                                        var results = [];
                                        var links    = document.querySelectorAll('a.result__a');
                                        var snippets = document.querySelectorAll('.result__snippet');
                                        for (var i = 0; i < links.length && i < 8; i++) {
                                            results.push({
                                                title:   links[i].innerText.trim(),
                                                url:     links[i].href,
                                                snippet: snippets[i] ? snippets[i].innerText.trim().substring(0, 400) : ''
                                            });
                                        }
                                        return JSON.stringify(results);
                                    })()
                                    """;

                            Object raw = page.evaluate(extractScript);
                            String json = raw != null ? raw.toString() : "[]";
                            LOG.debug("DuckDuckGoService", "HTML structured results: " + StringUtils.truncate(json, 400));

                            String pageText = page.innerText("body");
                            LOG.info("DuckDuckGoService", "HTML page_text length: " + pageText.length() + " chars");
                            if (pageText.length() > AppConfig.DDG_PAGE_TEXT_MAX_CHARS)
                                pageText = pageText.substring(0, AppConfig.DDG_PAGE_TEXT_MAX_CHARS) + "... [truncated]";

                            Map<String, String> result = new HashMap<>();
                            result.put("query", query);
                            result.put("engine", "DuckDuckGo");
                            result.put("results", json);
                            result.put("page_text", pageText);
                            result.put("status", "success");
                            return result;

                        } catch (Exception e) {
                            LOG.error("DuckDuckGoService", "HTML search failed", e);
                            Map<String, String> error = new HashMap<>();
                            error.put("status", "error");
                            error.put("engine", "DuckDuckGo");
                            error.put("message", e.getMessage());
                            return error;
                        }
                    }),
                    result -> "error".equals(result.get("status"))
            );
        } catch (Exception e) {
            LOG.error("DuckDuckGoService", "HTML search failed after retries", e);
            Map<String, String> error = new HashMap<>();
            error.put("status", "error");
            error.put("engine", "DuckDuckGo");
            error.put("message", e.getMessage());
            return error;
        }
    }

}
