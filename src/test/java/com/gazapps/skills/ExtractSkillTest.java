package com.gazapps.skills;

import com.gazapps.services.BrowserService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ExtractSkill.
 *
 * <p>Tests use {@code page.setContent()} to inject mock HTML and then run
 * the same JavaScript extraction script that extractStructuredData uses,
 * verifying the CSS-selector-to-JSON pipeline works correctly.
 *
 * <p>Prerequisite: run {@code mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args=install}
 */
class ExtractSkillTest {

    private static final int MAX_ITEMS = 50;

    /** Replicates the extraction script from ExtractSkill. */
    private static String runExtractionScript(com.microsoft.playwright.Page page,
                                               Map<String, String> selectors) {
        String script = """
                (function(sels) {
                    try {
                        var maxItems = %d;
                        var fields = Object.keys(sels);
                        if (fields.length === 0) return JSON.stringify([]);
                        var lists = {};
                        var maxLen = 0;
                        fields.forEach(function(field) {
                            var nodes = document.querySelectorAll(sels[field]);
                            lists[field] = nodes;
                            if (nodes.length > maxLen) maxLen = nodes.length;
                        });
                        var results = [];
                        var limit = Math.min(maxLen, maxItems);
                        for (var i = 0; i < limit; i++) {
                            var item = {};
                            fields.forEach(function(field) {
                                var node = lists[field][i];
                                item[field] = node ? (node.innerText || node.textContent || '').trim() : '';
                            });
                            results.push(item);
                        }
                        return JSON.stringify(results);
                    } catch(e) {
                        return JSON.stringify({error: e.message});
                    }
                })(arg)
                """.formatted(MAX_ITEMS);
        Object raw = page.evaluate(script, selectors);
        return raw != null ? raw.toString() : "[]";
    }

    @Test
    @Tag("browser")
    void extractStructuredData_singleSelectorMatchesMultipleElements() {
        String html = """
                <html><body>
                  <span class="price">$10.00</span>
                  <span class="price">$20.00</span>
                  <span class="price">$30.00</span>
                </body></html>
                """;

        String json = BrowserService.execute(page -> {
            page.setContent(html);
            return runExtractionScript(page, Map.of("price", ".price"));
        });

        assertNotNull(json);
        assertTrue(json.contains("$10.00"), "First price should be in result");
        assertTrue(json.contains("$20.00"), "Second price should be in result");
        assertTrue(json.contains("$30.00"), "Third price should be in result");
    }

    @Test
    @Tag("browser")
    void extractStructuredData_multipleSelectorsExtractAlignedFields() {
        String html = """
                <html><body>
                  <div class="product">
                    <span class="name">Widget A</span>
                    <span class="cost">$5</span>
                  </div>
                  <div class="product">
                    <span class="name">Widget B</span>
                    <span class="cost">$8</span>
                  </div>
                </body></html>
                """;

        String json = BrowserService.execute(page -> {
            page.setContent(html);
            return runExtractionScript(page,
                    Map.of("name", ".name", "cost", ".cost"));
        });

        assertNotNull(json);
        assertTrue(json.contains("Widget A"), "First product name should appear");
        assertTrue(json.contains("Widget B"), "Second product name should appear");
        assertTrue(json.contains("$5"), "First price should appear");
        assertTrue(json.contains("$8"), "Second price should appear");
    }

    @Test
    @Tag("browser")
    void extractStructuredData_emptySelectorsReturnsEmptyArray() {
        String html = "<html><body><p>Some content</p></body></html>";

        String json = BrowserService.execute(page -> {
            page.setContent(html);
            return runExtractionScript(page, Map.of());
        });

        assertEquals("[]", json, "Empty selectors map should return empty JSON array");
    }

    @Test
    @Tag("browser")
    void extractStructuredData_selectorMatchesNothing_returnsEmptyStrings() {
        String html = "<html><body><p>No matching elements</p></body></html>";

        String json = BrowserService.execute(page -> {
            page.setContent(html);
            return runExtractionScript(page, Map.of("price", ".nonexistent-class"));
        });

        // No elements matched — maxLen stays 0 — result is empty array
        assertEquals("[]", json, "Non-matching selector should produce empty result array");
    }
}
