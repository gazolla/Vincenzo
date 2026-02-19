package com.gazapps.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SearchCacheTest {

    @BeforeEach
    void clearCache() {
        // Garante que cada teste parte de um cache vazio
        SearchCache.clear();
    }

    // ── get/put básico ────────────────────────────────────────────────────────

    @Test
    void get_onEmptyCache_returnsNull() {
        assertNull(SearchCache.get("qualquer query"));
    }

    @Test
    void putAndGet_returnsSameResult() {
        Map<String, String> result = Map.of("status", "success", "page_text", "conteúdo");
        SearchCache.put("cotação dólar", result);
        assertEquals(result, SearchCache.get("cotação dólar"));
    }

    // ── normalização ──────────────────────────────────────────────────────────

    @Test
    void normalize_lowercasesQuery() {
        assertEquals("ia", SearchCache.normalize("IA"));
    }

    @Test
    void normalize_collapsesMultipleSpaces() {
        assertEquals("ola mundo", SearchCache.normalize("ola  mundo"));
    }

    @Test
    void normalize_trimsBothEnds() {
        assertEquals("query", SearchCache.normalize("  query  "));
    }

    @Test
    void normalize_nullQuery_returnsEmpty() {
        assertEquals("", SearchCache.normalize(null));
    }

    @Test
    void normalize_emptyQuery_returnsEmpty() {
        assertEquals("", SearchCache.normalize(""));
    }

    // ── case insensitivity via normalização ───────────────────────────────────

    @Test
    void get_caseInsensitive_returnsResult() {
        Map<String, String> result = Map.of("status", "success");
        // Armazenar com chave normalizada lowercase
        SearchCache.put(SearchCache.normalize("IA"), result);
        // Buscar com query em uppercase — deve normalizar antes de consultar
        assertEquals(result, SearchCache.get(SearchCache.normalize("IA")));
        // E buscar com lowercase direto também deve funcionar
        assertEquals(result, SearchCache.get("ia"));
    }

    @Test
    void get_afterPutWithSpaces_retrievedNormalized() {
        Map<String, String> result = Map.of("status", "success");
        SearchCache.put(SearchCache.normalize("cotação  dólar"), result);
        assertEquals(result, SearchCache.get("cotação dólar"));
    }

    // ── invalidação ───────────────────────────────────────────────────────────

    @Test
    void invalidate_removesEntry() {
        Map<String, String> result = Map.of("status", "success");
        SearchCache.put("query", result);
        assertNotNull(SearchCache.get("query"));

        SearchCache.invalidate("query");
        assertNull(SearchCache.get("query"));
    }

    @Test
    void invalidate_nonExistentKey_doesNotThrow() {
        assertDoesNotThrow(() -> SearchCache.invalidate("nao existe"));
    }

    // ── clear ─────────────────────────────────────────────────────────────────

    @Test
    void clear_emptiesAllEntries() {
        SearchCache.put("query1", Map.of("status", "success"));
        SearchCache.put("query2", Map.of("status", "success"));
        SearchCache.put("query3", Map.of("status", "success"));

        SearchCache.clear();

        assertNull(SearchCache.get("query1"));
        assertNull(SearchCache.get("query2"));
        assertNull(SearchCache.get("query3"));
    }

    // ── stats ─────────────────────────────────────────────────────────────────

    @Test
    void stats_returnsNonBlank() {
        String s = SearchCache.stats();
        assertNotNull(s);
        assertFalse(s.isBlank());
    }

    @Test
    void stats_containsExpectedFields() {
        String s = SearchCache.stats();
        assertTrue(s.contains("hits="), "deve conter hits=");
        assertTrue(s.contains("misses="), "deve conter misses=");
        assertTrue(s.contains("hitRate="), "deve conter hitRate=");
        assertTrue(s.contains("size="), "deve conter size=");
    }

    // ── múltiplas entradas independentes ─────────────────────────────────────

    @Test
    void multipleEntries_storedAndRetrievedIndependently() {
        Map<String, String> r1 = new HashMap<>(); r1.put("engine", "DuckDuckGo");
        Map<String, String> r2 = new HashMap<>(); r2.put("engine", "Bing");

        SearchCache.put("query a", r1);
        SearchCache.put("query b", r2);

        assertEquals(r1, SearchCache.get("query a"));
        assertEquals(r2, SearchCache.get("query b"));
        assertNotEquals(r1, r2);
    }
}
