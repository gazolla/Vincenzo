package com.gazapps.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testa AppConfig observando os campos public static final diretamente.
 * Sem mocking — testa invariantes (positivo, não-nulo) e valores concretos
 * que coincidem tanto com os defaults hardcoded quanto com o application.properties.
 */
class AppConfigTest {

    // ── Campos int: devem ser positivos ──────────────────────────────────────

    @Test
    void httpConnectTimeoutSeconds_isPositive() {
        assertTrue(AppConfig.HTTP_CONNECT_TIMEOUT_SECONDS > 0);
    }

    @Test
    void fetchPageNavigateTimeoutMs_isPositive() {
        assertTrue(AppConfig.FETCH_PAGE_NAVIGATE_TIMEOUT_MS > 0);
    }

    @Test
    void screenshotNavigateTimeoutMs_isPositive() {
        assertTrue(AppConfig.SCREENSHOT_NAVIGATE_TIMEOUT_MS > 0);
    }

    @Test
    void summarizeNavigateTimeoutMs_isPositive() {
        assertTrue(AppConfig.SUMMARIZE_NAVIGATE_TIMEOUT_MS > 0);
    }

    @Test
    void extractNavigateTimeoutMs_isPositive() {
        assertTrue(AppConfig.EXTRACT_NAVIGATE_TIMEOUT_MS > 0);
    }

    @Test
    void formNavigateTimeoutMs_isPositive() {
        assertTrue(AppConfig.FORM_NAVIGATE_TIMEOUT_MS > 0);
    }

    @Test
    void fetchPageMaxChars_isPositive() {
        assertTrue(AppConfig.FETCH_PAGE_MAX_CHARS > 0);
    }

    @Test
    void ddgPageTextMaxChars_isPositive() {
        assertTrue(AppConfig.DDG_PAGE_TEXT_MAX_CHARS > 0);
    }

    @Test
    void bingPageTextMaxChars_isPositive() {
        assertTrue(AppConfig.BING_PAGE_TEXT_MAX_CHARS > 0);
    }

    @Test
    void summarizeMaxChars_isPositive() {
        assertTrue(AppConfig.SUMMARIZE_MAX_CHARS > 0);
    }

    @Test
    void extractMaxItems_isPositive() {
        assertTrue(AppConfig.EXTRACT_MAX_ITEMS > 0);
    }

    @Test
    void formAfterSubmitWaitMs_isNonNegative() {
        assertTrue(AppConfig.FORM_AFTER_SUBMIT_WAIT_MS >= 0);
    }

    @Test
    void formResultMaxChars_isPositive() {
        assertTrue(AppConfig.FORM_RESULT_MAX_CHARS > 0);
    }

    @Test
    void pdfHttpTimeoutSeconds_isPositive() {
        assertTrue(AppConfig.PDF_HTTP_TIMEOUT_SECONDS > 0);
    }

    @Test
    void pdfMaxChars_isPositive() {
        assertTrue(AppConfig.PDF_MAX_CHARS > 0);
    }

    // ── Campos String: não nulos e não vazios ─────────────────────────────────

    @Test
    void userAgent_isNonBlank() {
        assertNotNull(AppConfig.USER_AGENT);
        assertFalse(AppConfig.USER_AGENT.isBlank());
    }

    // ── Valores concretos (coincidem com default e com application.properties) ─

    @Test
    void httpConnectTimeoutSeconds_equals8() {
        assertEquals(8, AppConfig.HTTP_CONNECT_TIMEOUT_SECONDS);
    }

    @Test
    void fetchPageNavigateTimeoutMs_equals20000() {
        assertEquals(20_000, AppConfig.FETCH_PAGE_NAVIGATE_TIMEOUT_MS);
    }

    @Test
    void fetchPageMaxChars_equals5000() {
        assertEquals(5_000, AppConfig.FETCH_PAGE_MAX_CHARS);
    }

    @Test
    void pdfMaxChars_equals10000() {
        assertEquals(10_000, AppConfig.PDF_MAX_CHARS);
    }

    // ── Novos campos da Fase 2 ────────────────────────────────────────────────

    @Test
    void retryMaxAttempts_isPositive() {
        assertTrue(AppConfig.RETRY_MAX_ATTEMPTS > 0);
    }

    @Test
    void retryInitialDelayMs_isNonNegative() {
        assertTrue(AppConfig.RETRY_INITIAL_DELAY_MS >= 0);
    }

    @Test
    void locale_isNonBlank() {
        assertNotNull(AppConfig.LOCALE);
        assertFalse(AppConfig.LOCALE.isBlank());
    }

    @Test
    void ddgRegion_isNonBlank() {
        assertNotNull(AppConfig.DDG_REGION);
        assertFalse(AppConfig.DDG_REGION.isBlank());
    }

    @Test
    void retryMaxAttempts_equals3() {
        assertEquals(3, AppConfig.RETRY_MAX_ATTEMPTS);
    }

    @Test
    void retryInitialDelayMs_equals500() {
        assertEquals(500L, AppConfig.RETRY_INITIAL_DELAY_MS);
    }

    // ── Novos campos da Fase 3 (cache de busca) ───────────────────────────────

    @Test
    void searchCacheEnabled_isBoolean() {
        // apenas acessa o campo — se compilar e não lançar exceção, está correto
        boolean ignored = AppConfig.SEARCH_CACHE_ENABLED;
    }

    @Test
    void searchCacheMaxSize_isPositive() {
        assertTrue(AppConfig.SEARCH_CACHE_MAX_SIZE > 0);
    }

    @Test
    void searchCacheTtlMinutes_isPositive() {
        assertTrue(AppConfig.SEARCH_CACHE_TTL_MINUTES > 0);
    }

    // ── Log management ────────────────────────────────────────────────────────

    @Test
    void logMaxFiles_isPositive() {
        assertTrue(AppConfig.LOG_MAX_FILES > 0);
    }

    @Test
    void logMaxSizeKb_isNonNegative() {
        assertTrue(AppConfig.LOG_MAX_SIZE_KB >= 0);
    }

    // ── LLM / Model ───────────────────────────────────────────────────────────

    @Test
    void llmModel_isNonBlank() {
        assertNotNull(AppConfig.LLM_MODEL);
        assertFalse(AppConfig.LLM_MODEL.isBlank());
    }
}
