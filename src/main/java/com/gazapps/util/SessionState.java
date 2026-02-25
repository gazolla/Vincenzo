package com.gazapps.util;

import com.google.adk.tools.ToolContext;

import java.util.Optional;

/**
 * Null-safe helper for reading and writing values to
 * {@link ToolContext#state()}.
 *
 * <p>
 * All methods silently no-op when {@code ctx} is {@code null}, making them
 * safe to call from unit tests that invoke skills directly (without ADK
 * injection).
 *
 * <p>
 * All state keys are defined as constants here (DRY): every skill reads/writes
 * the same key names, enabling cross-skill data sharing within a single
 * session.
 */
public final class SessionState {

    // ── State keys ────────────────────────────────────────────────────────────

    /** The last search query submitted to searchWeb. */
    public static final String KEY_LAST_QUERY = "last_query";

    /** Plain-text content of the last search results page. */
    public static final String KEY_LAST_SEARCH_TEXT = "last_search_text";

    /** URL of the last page fetched by fetchPageContent. */
    public static final String KEY_LAST_FETCH_URL = "last_fetch_url";

    /** Body text of the last page fetched by fetchPageContent. */
    public static final String KEY_LAST_FETCH_CONTENT = "last_fetch_content";

    /** URL of the last page processed by extractStructuredData. */
    public static final String KEY_LAST_EXTRACT_URL = "last_extract_url";

    /** JSON string of data extracted by the last extractStructuredData call. */
    public static final String KEY_LAST_EXTRACT_DATA = "last_extract_data";

    private SessionState() {
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Stores {@code value} under {@code key} in the session state.
     * No-op when {@code ctx} or {@code value} is {@code null}.
     */
    public static void put(ToolContext ctx, String key, Object value) {
        if (ctx == null || value == null)
            return;
        ctx.state().put(key, value);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Returns the raw value stored under {@code key}, or {@link Optional#empty()}
     * when {@code ctx} is {@code null} or the key is absent.
     */
    public static Optional<Object> get(ToolContext ctx, String key) {
        if (ctx == null)
            return Optional.empty();
        return Optional.ofNullable(ctx.state().get(key));
    }

    /**
     * Returns the value stored under {@code key} as a {@link String},
     * or {@link Optional#empty()} when absent, {@code null}, or not a String.
     */
    public static Optional<String> getString(ToolContext ctx, String key) {
        return get(ctx, key)
                .filter(v -> v instanceof String)
                .map(String.class::cast);
    }
}
