package com.gazapps.skills;

import com.gazapps.config.AppConfig;
import com.gazapps.logging.LogService;
import com.gazapps.util.SearchCache;
import com.gazapps.util.StringUtils;

import com.google.adk.tools.Annotations.Schema;

import java.util.Map;

/**
 * ADK tool that orchestrates the search pipeline:
 * DDG Instant Answer → DDG HTML → Bing fallback.
 */
public class WebOrchestrator {

    private static final LogService LOG = LogService.getInstance();

    // ─────────────────────────────────────────────────────────────────────────
    // searchWeb (DDG JSON + DDG HTML → Bing fallback)
    // ─────────────────────────────────────────────────────────────────────────

    @Schema(description = """
            Search the internet and return results with titles, URLs, and descriptions.
            ALWAYS use this tool for: flight prices, hotel prices, tickets, news, weather,
            sports scores, exchange rates, product prices, events, schedules, and ANY
            real-world data that changes over time.
            Never answer those topics from memory - always search first.
            """)
    public static Map<String, String> searchWeb(
            @Schema(name = "query", description = "The search query in the user's language.") String query) {

        LOG.section("TOOL CALL: searchWeb");
        LOG.info("WebOrchestrator", "Query: \"" + query + "\"");
        long start = System.currentTimeMillis();

        // ── Cache lookup ────────────────────────────────────────────────────
        if (AppConfig.SEARCH_CACHE_ENABLED) {
            String cacheKey = SearchCache.normalize(query);
            Map<String, String> cached = SearchCache.get(cacheKey);
            if (cached != null) {
                LOG.info("WebOrchestrator", "Cache HIT para: \"" + query + "\"");
                LOG.timing("WebOrchestrator", "searchWeb (cache hit)", System.currentTimeMillis() - start);
                return cached;
            }
            LOG.debug("WebOrchestrator", "Cache MISS para: \"" + query + "\"");
        }

        // ── Step 1: DDG Instant Answer JSON (fast, no browser) ────────────
        LOG.info("WebOrchestrator", "Step 1 — DDG Instant Answer JSON API");
        String instantJson = com.gazapps.services.DuckDuckGoService.fetchInstantAnswer(query);
        LOG.debug("WebOrchestrator", "DDG instant answer: " + StringUtils.truncate(instantJson, 300));

        // ── Step 2: DDG HTML search results (Playwright) ──────────────────
        LOG.info("WebOrchestrator", "Step 2 — DDG HTML search (Playwright)");
        Map<String, String> html = com.gazapps.services.DuckDuckGoService.searchHtml(query);

        // ── Step 3: Bing fallback if DDG HTML had no results ──────────────
        boolean ddgHasResults = "success".equals(html.get("status"))
                && html.get("page_text") != null
                && html.get("page_text").length() > 300;

        Map<String, String> webResults;
        if (ddgHasResults) {
            LOG.info("WebOrchestrator", "DDG HTML returned results — skipping Bing");
            webResults = html;
        } else {
            LOG.warn("WebOrchestrator", "DDG HTML insufficient — falling back to Bing");
            webResults = com.gazapps.services.BingService.search(query);
        }

        // ── Merge instant answer into final result ─────────────────────────
        if (instantJson != null && !instantJson.isBlank() && !instantJson.equals("{}")) {
            webResults.put("instant_answer", instantJson);
        }

        // ── Armazenar no cache (somente sucessos) ───────────────────────────
        if (AppConfig.SEARCH_CACHE_ENABLED && "success".equals(webResults.get("status"))) {
            SearchCache.put(SearchCache.normalize(query), webResults);
            LOG.debug("WebOrchestrator", "Resultado cacheado. Stats: " + SearchCache.stats());
        }

        LOG.timing("WebOrchestrator", "searchWeb total", System.currentTimeMillis() - start);
        LOG.toolResult("searchWeb", webResults);
        return webResults;
    }

}
