package com.gazapps.util;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SessionState}.
 *
 * <p>
 * These tests focus on null-safety (ctx == null → no-op / empty Optional),
 * which is the core guarantee that allows existing skill unit tests to call
 * skill methods with {@code null} as the ToolContext argument without NPE.
 *
 * <p>
 * Full round-trip tests (put + getString with a real ToolContext) require
 * an integration environment with a live ADK runner and are not covered here.
 */
class SessionStateTest {

    // ── Null-safety (ctx == null) ─────────────────────────────────────────────

    @Test
    void put_nullContext_doesNotThrow() {
        // Must be a no-op — not throw NullPointerException
        assertDoesNotThrow(() -> SessionState.put(null, SessionState.KEY_LAST_QUERY, "test"));
    }

    @Test
    void put_nullValue_doesNotThrow() {
        assertDoesNotThrow(() -> SessionState.put(null, SessionState.KEY_LAST_QUERY, null));
    }

    @Test
    void get_nullContext_returnsEmpty() {
        Optional<Object> result = SessionState.get(null, SessionState.KEY_LAST_QUERY);
        assertNotNull(result);
        assertTrue(result.isEmpty(), "Expected Optional.empty() for null context");
    }

    @Test
    void getString_nullContext_returnsEmpty() {
        Optional<String> result = SessionState.getString(null, SessionState.KEY_LAST_FETCH_URL);
        assertNotNull(result);
        assertTrue(result.isEmpty(), "Expected Optional.empty() for null context");
    }

    @Test
    void getString_nullContext_anyKey_returnsEmpty() {
        // All keys should return empty for null ctx
        for (String key : new String[] {
                SessionState.KEY_LAST_QUERY,
                SessionState.KEY_LAST_SEARCH_TEXT,
                SessionState.KEY_LAST_FETCH_URL,
                SessionState.KEY_LAST_FETCH_CONTENT,
                SessionState.KEY_LAST_EXTRACT_URL,
                SessionState.KEY_LAST_EXTRACT_DATA
        }) {
            assertTrue(SessionState.getString(null, key).isEmpty(),
                    "Key '" + key + "' should return empty Optional for null context");
        }
    }

    // ── Key constant sanity ───────────────────────────────────────────────────

    @Test
    void keyConstants_areNonBlank() {
        assertFalse(SessionState.KEY_LAST_QUERY.isBlank());
        assertFalse(SessionState.KEY_LAST_SEARCH_TEXT.isBlank());
        assertFalse(SessionState.KEY_LAST_FETCH_URL.isBlank());
        assertFalse(SessionState.KEY_LAST_FETCH_CONTENT.isBlank());
        assertFalse(SessionState.KEY_LAST_EXTRACT_URL.isBlank());
        assertFalse(SessionState.KEY_LAST_EXTRACT_DATA.isBlank());
    }

    @Test
    void keyConstants_areUnique() {
        java.util.Set<String> keys = java.util.Set.of(
                SessionState.KEY_LAST_QUERY,
                SessionState.KEY_LAST_SEARCH_TEXT,
                SessionState.KEY_LAST_FETCH_URL,
                SessionState.KEY_LAST_FETCH_CONTENT,
                SessionState.KEY_LAST_EXTRACT_URL,
                SessionState.KEY_LAST_EXTRACT_DATA);
        assertEquals(6, keys.size(), "All key constants must be unique");
    }
}
