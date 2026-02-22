package com.gazapps.skills;

import com.gazapps.config.AppConfig;
import com.gazapps.logging.LogService;
import com.gazapps.util.CircuitBreaker;
import com.gazapps.util.SearchCache;
import com.gazapps.util.SkillStatus;
import com.gazapps.util.StringUtils;

import com.google.adk.tools.Annotations.Schema;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

/**
 * ADK tool that orchestrates the search pipeline:
 * DDG Instant Answer → DDG HTML → Bing fallback.
 *
 * <p>When {@code search.parallel.enabled=true} the DDG HTML and Bing searches
 * are dispatched in parallel via {@link CompletableFuture}; DDG is preferred if
 * its result is sufficient, otherwise Bing wins.</p>
 *
 * <p>A {@link CircuitBreaker} protects the DDG HTML path: after
 * {@code search.circuit.ddg.failure.threshold} consecutive failures the circuit
 * opens and DDG is skipped entirely until {@code search.circuit.ddg.reset.ms}
 * milliseconds have elapsed.</p>
 */
public class WebOrchestrator {

    private static final LogService LOG = LogService.getInstance();

    // ── Thread pool for parallel search (daemon threads — won't block JVM exit) ──
    private static final ExecutorService SEARCH_POOL =
            Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "search-parallel");
                t.setDaemon(true);
                return t;
            });

    // ── Circuit breaker protecting the DDG HTML path ──────────────────────────
    static final CircuitBreaker DDG_CIRCUIT_BREAKER = new CircuitBreaker(
            "DDG-HTML",
            AppConfig.SEARCH_CIRCUIT_DDG_FAILURE_THRESHOLD,
            AppConfig.SEARCH_CIRCUIT_DDG_RESET_MS
    );

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
            Optional<Map<String, String>> cached = SearchCache.get(cacheKey);
            if (cached.isPresent()) {
                LOG.info("WebOrchestrator", "Cache HIT for: \"" + query + "\"");
                LOG.timing("WebOrchestrator", "searchWeb (cache hit)", System.currentTimeMillis() - start);
                return cached.get();
            }
            LOG.debug("WebOrchestrator", "Cache MISS for: \"" + query + "\"");
        }

        // ── Step 1: DDG Instant Answer JSON (fast, no browser) ────────────
        LOG.info("WebOrchestrator", "Step 1 — DDG Instant Answer JSON API");
        String instantJson = com.gazapps.services.DuckDuckGoService.fetchInstantAnswer(query);
        LOG.debug("WebOrchestrator", "DDG instant answer: " + StringUtils.truncate(instantJson, 300));

        // ── Steps 2+3: parallel or sequential ─────────────────────────────
        Map<String, String> webResults = AppConfig.SEARCH_PARALLEL_ENABLED
                ? executeParallel(query, instantJson)
                : executeSequential(query, instantJson);

        // ── Store in cache (successes only) ────────────────────────────────
        if (AppConfig.SEARCH_CACHE_ENABLED && SkillStatus.SUCCESS.value().equals(webResults.get("status"))) {
            SearchCache.put(SearchCache.normalize(query), webResults);
            LOG.debug("WebOrchestrator", "Result cached. Stats: " + SearchCache.stats());
        }

        LOG.timing("WebOrchestrator", "searchWeb total", System.currentTimeMillis() - start);
        LOG.toolResult("searchWeb", webResults);
        return webResults;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sequential path (default) — preserves the original behaviour exactly
    // ─────────────────────────────────────────────────────────────────────────

    private static Map<String, String> executeSequential(String query, String instantJson) {
        // Step 2: DDG HTML (circuit-breaker protected)
        LOG.info("WebOrchestrator", "Step 2 — DDG HTML search (sequential, Playwright)");
        Map<String, String> html = fetchDdgHtml(query);

        // Step 3: Bing fallback if DDG HTML had no results
        boolean ddgHasResults = isDdgSufficient(html);
        Map<String, String> webResults;
        if (ddgHasResults) {
            LOG.info("WebOrchestrator", "DDG HTML returned results — skipping Bing");
            webResults = html;
        } else {
            LOG.warn("WebOrchestrator", "DDG HTML insufficient — falling back to Bing");
            webResults = com.gazapps.services.BingService.search(query);
        }

        mergeInstantAnswer(webResults, instantJson);
        return webResults;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parallel path (search.parallel.enabled=true)
    // ─────────────────────────────────────────────────────────────────────────

    private static Map<String, String> executeParallel(String query, String instantJson) {
        LOG.info("WebOrchestrator", "Step 2+3 — DDG HTML + Bing in parallel (CompletableFuture)");

        CompletableFuture<Map<String, String>> ddgFuture =
                CompletableFuture.supplyAsync(() -> fetchDdgHtml(query), SEARCH_POOL);

        CompletableFuture<Map<String, String>> bingFuture =
                CompletableFuture.supplyAsync(() -> com.gazapps.services.BingService.search(query), SEARCH_POOL);

        Map<String, String> ddgResult;
        Map<String, String> bingResult;

        try {
            CompletableFuture.allOf(ddgFuture, bingFuture)
                    .get(AppConfig.SEARCH_PARALLEL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            ddgResult  = ddgFuture.getNow(Map.of("status", SkillStatus.ERROR.value()));
            bingResult = bingFuture.getNow(Map.of("status", SkillStatus.ERROR.value()));
        } catch (TimeoutException e) {
            LOG.warn("WebOrchestrator", "Parallel search timed out after "
                    + AppConfig.SEARCH_PARALLEL_TIMEOUT_MS + "ms — using available results");
            ddgResult  = ddgFuture.isDone()  ? ddgFuture.getNow(Map.of("status", SkillStatus.ERROR.value()))  : Map.of("status", SkillStatus.ERROR.value());
            bingResult = bingFuture.isDone() ? bingFuture.getNow(Map.of("status", SkillStatus.ERROR.value())) : Map.of("status", SkillStatus.ERROR.value());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("WebOrchestrator", "Parallel search interrupted — using Bing result only");
            ddgResult  = Map.of("status", SkillStatus.ERROR.value());
            bingResult = bingFuture.getNow(Map.of("status", SkillStatus.ERROR.value()));
        } catch (ExecutionException e) {
            LOG.warn("WebOrchestrator", "Parallel search execution error: " + e.getCause().getMessage());
            ddgResult  = ddgFuture.isCompletedExceptionally()  ? Map.of("status", SkillStatus.ERROR.value()) : ddgFuture.getNow(Map.of("status", SkillStatus.ERROR.value()));
            bingResult = bingFuture.isCompletedExceptionally() ? Map.of("status", SkillStatus.ERROR.value()) : bingFuture.getNow(Map.of("status", SkillStatus.ERROR.value()));
        }

        // Prefer DDG if sufficient; otherwise use Bing
        Map<String, String> webResults;
        if (isDdgSufficient(ddgResult)) {
            LOG.info("WebOrchestrator", "Parallel: DDG HTML sufficient — using DDG result");
            webResults = ddgResult;
        } else {
            LOG.warn("WebOrchestrator", "Parallel: DDG insufficient — using Bing result");
            webResults = bingResult;
        }

        mergeInstantAnswer(webResults, instantJson);
        return webResults;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Calls DDG HTML search with circuit-breaker protection.
     * Returns {@code Map("status" → "error")} when the circuit is OPEN or on exception.
     */
    private static Map<String, String> fetchDdgHtml(String query) {
        if (!DDG_CIRCUIT_BREAKER.allowCall()) {
            LOG.warn("WebOrchestrator",
                    "DDG circuit OPEN [" + DDG_CIRCUIT_BREAKER.getState() + "] — skipping DDG HTML");
            return Map.of("status", SkillStatus.ERROR.value());
        }
        try {
            Map<String, String> result = com.gazapps.services.DuckDuckGoService.searchHtml(query);
            DDG_CIRCUIT_BREAKER.recordSuccess();
            return result;
        } catch (Exception ex) {
            DDG_CIRCUIT_BREAKER.recordFailure();
            LOG.warn("WebOrchestrator", "DDG HTML failed (circuit breaker notified): " + ex.getMessage());
            return Map.of("status", SkillStatus.ERROR.value());
        }
    }

    /** Returns {@code true} if the DDG HTML result is considered sufficient. */
    private static boolean isDdgSufficient(Map<String, String> html) {
        return SkillStatus.SUCCESS.value().equals(html.get("status"))
                && html.get("page_text") != null
                && html.get("page_text").length() > 300;
    }

    /** Merges the DDG instant-answer JSON into the final result map (mutates {@code result}). */
    private static void mergeInstantAnswer(Map<String, String> result, String instantJson) {
        if (instantJson != null && !instantJson.isBlank() && !instantJson.equals("{}")) {
            result.put("instant_answer", instantJson);
        }
    }
}
