package com.gazapps.util;

import com.gazapps.config.AppConfig;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheStats;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * In-memory cache for search results, backed by Guava Cache.
 *
 * <p>Keys are normalized queries (lowercase, collapsed spaces).
 * The cache is configurable via {@code AppConfig}: maximum size (LRU eviction),
 * TTL, and enable flag.
 *
 * <p>Guava ({@code com.google.guava:guava}) is available as a transitive
 * dependency of {@code google-adk} — no additional pom.xml entry is required.
 */
public final class SearchCache {

    private static final Cache<String, Map<String, String>> CACHE = buildCache();

    private SearchCache() {}

    /**
     * Builds the cache with parameters read from AppConfig.
     * Kept as a separate method to facilitate tests that need a custom TTL.
     */
    static Cache<String, Map<String, String>> buildCache() {
        return CacheBuilder.newBuilder()
                .maximumSize(AppConfig.getInstance().SEARCH_CACHE_MAX_SIZE)
                .expireAfterWrite(AppConfig.getInstance().SEARCH_CACHE_TTL_MINUTES, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    /**
     * Normalizes the query for use as a cache key.
     * Lowercase + trim + multiple-space collapse.
     * Never throws.
     */
    public static String normalize(String query) {
        if (query == null) return "";
        return query.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    /**
     * Returns the cached result for the normalized query,
     * or {@link Optional#empty()} if absent or expired.
     */
    public static Optional<Map<String, String>> get(String normalizedQuery) {
        return Optional.ofNullable(CACHE.getIfPresent(normalizedQuery));
    }

    /**
     * Stores the result in the cache with the normalized query as the key.
     */
    public static void put(String normalizedQuery, Map<String, String> result) {
        CACHE.put(normalizedQuery, result);
    }

    /**
     * Removes a specific entry from the cache (manual invalidation).
     */
    public static void invalidate(String normalizedQuery) {
        CACHE.invalidate(normalizedQuery);
    }

    /**
     * Removes all entries from the cache.
     */
    public static void clear() {
        CACHE.invalidateAll();
    }

    /**
     * Returns a string with cache statistics
     * (hits, misses, hitRate, current size).
     */
    public static String stats() {
        CacheStats s = CACHE.stats();
        return String.format("hits=%d misses=%d hitRate=%.1f%% size=%d",
                s.hitCount(), s.missCount(), s.hitRate() * 100, CACHE.size());
    }
}
