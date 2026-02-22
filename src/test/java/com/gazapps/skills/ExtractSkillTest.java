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

    /**
     * Replicates the extraction script from ExtractSkill, including the
     * "selector|attr" syntax for extracting HTML attributes (href, src, data-*…).
     */
    private static String runExtractionScript(com.microsoft.playwright.Page page,
                                               Map<String, String> selectors) {
        String script = """
                sels => {
                    try {
                        var maxItems = %d;
                        var fields = Object.keys(sels);
                        if (fields.length === 0) return JSON.stringify([]);

                        // Parse each selector into {css, attr}
                        var parsed = {};
                        fields.forEach(function(field) {
                            var raw = sels[field];
                            var pipe = raw.lastIndexOf('|');
                            if (pipe !== -1) {
                                parsed[field] = {css: raw.substring(0, pipe), attr: raw.substring(pipe + 1)};
                            } else {
                                parsed[field] = {css: raw, attr: null};
                            }
                        });

                        var lists = {};
                        var maxLen = 0;
                        fields.forEach(function(field) {
                            var nodes = document.querySelectorAll(parsed[field].css);
                            lists[field] = nodes;
                            if (nodes.length > maxLen) maxLen = nodes.length;
                        });

                        var results = [];
                        var limit = Math.min(maxLen, maxItems);
                        for (var i = 0; i < limit; i++) {
                            var item = {};
                            fields.forEach(function(field) {
                                var node = lists[field][i];
                                if (!node) { item[field] = ''; return; }
                                var attr = parsed[field].attr;
                                if (attr) {
                                    item[field] = (node.getAttribute(attr) || '').trim();
                                } else {
                                    item[field] = (node.innerText || node.textContent || '').trim();
                                }
                            });
                            results.push(item);
                        }
                        return JSON.stringify(results);
                    } catch(e) {
                        return JSON.stringify({error: e.message});
                    }
                }
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

    // ── Attribute extraction tests (|attr syntax) ────────────────────────────

    @Test
    @Tag("browser")
    void extractStructuredData_pipeHref_extractsLinkUrl() {
        String html = """
                <html><body>
                  <a class="item" href="https://example.com/a">Article A</a>
                  <a class="item" href="https://example.com/b">Article B</a>
                </body></html>
                """;

        String json = BrowserService.execute(page -> {
            page.setContent(html);
            return runExtractionScript(page,
                    Map.of("title", ".item", "link", ".item|href"));
        });

        assertTrue(json.contains("Article A"), "Text should be extracted for title");
        assertTrue(json.contains("Article B"), "Text should be extracted for title");
        assertTrue(json.contains("https://example.com/a"), "href should be extracted for link");
        assertTrue(json.contains("https://example.com/b"), "href should be extracted for link");
    }

    @Test
    @Tag("browser")
    void extractStructuredData_pipeSrc_extractsImageUrl() {
        String html = """
                <html><body>
                  <img class="thumb" src="/images/cat.jpg" alt="Cat">
                  <img class="thumb" src="/images/dog.jpg" alt="Dog">
                </body></html>
                """;

        String json = BrowserService.execute(page -> {
            page.setContent(html);
            return runExtractionScript(page, Map.of("image", ".thumb|src"));
        });

        assertTrue(json.contains("/images/cat.jpg"), "First image src should be extracted");
        assertTrue(json.contains("/images/dog.jpg"), "Second image src should be extracted");
    }

    @Test
    @Tag("browser")
    void extractStructuredData_pipeDataAttr_extractsCustomAttribute() {
        String html = """
                <html><body>
                  <div class="card" data-id="101">Product One</div>
                  <div class="card" data-id="202">Product Two</div>
                </body></html>
                """;

        String json = BrowserService.execute(page -> {
            page.setContent(html);
            return runExtractionScript(page,
                    Map.of("name", ".card", "id", ".card|data-id"));
        });

        assertTrue(json.contains("Product One"), "Text should be extracted for name");
        assertTrue(json.contains("101"), "data-id should be extracted");
        assertTrue(json.contains("202"), "data-id should be extracted");
    }

    @Test
    @Tag("browser")
    void extractStructuredData_pipeMissingAttr_returnsEmptyString() {
        String html = """
                <html><body>
                  <a class="link" href="https://example.com">No data-foo here</a>
                </body></html>
                """;

        String json = BrowserService.execute(page -> {
            page.setContent(html);
            return runExtractionScript(page, Map.of("val", ".link|data-foo"));
        });

        // getAttribute returns null → empty string
        assertTrue(json.contains("\"val\":\"\""), "Missing attribute should produce empty string");
    }

    @Test
    @Tag("browser")
    void extractStructuredData_textAndHrefMixed_extractsBoth() {
        // Simulates the Hacker News use case: title text + href from the same <a>
        String html = """
                <html><body>
                  <span class="titleline"><a href="https://hn.com/1">Story One</a></span>
                  <span class="titleline"><a href="https://hn.com/2">Story Two</a></span>
                  <span class="titleline"><a href="https://hn.com/3">Story Three</a></span>
                </body></html>
                """;

        String json = BrowserService.execute(page -> {
            page.setContent(html);
            return runExtractionScript(page,
                    Map.of("title", ".titleline > a",
                           "link",  ".titleline > a|href"));
        });

        assertTrue(json.contains("Story One"),          "Title text should be extracted");
        assertTrue(json.contains("Story Three"),        "Title text should be extracted");
        assertTrue(json.contains("https://hn.com/1"),   "href should be extracted");
        assertTrue(json.contains("https://hn.com/3"),   "href should be extracted");
        // Verify they are not the same value (the old bug)
        assertFalse(json.contains("\"link\":\"Story One\""), "link field must NOT contain the text");
    }
}
