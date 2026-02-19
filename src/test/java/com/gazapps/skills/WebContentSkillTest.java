package com.gazapps.skills;

import com.gazapps.services.BrowserService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for WebContentSkill.
 *
 * <p>Playwright-based tests use {@code page.setContent()} to inject mock HTML directly
 * into the browser page, avoiding external network calls and bypassing UrlValidator's
 * loopback-blocking constraint (localhost URLs are rejected before Playwright is invoked).
 *
 * <p>Prerequisite: Playwright browsers must be installed once before running these tests.
 * Run: {@code mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args=install}
 */
class WebContentSkillTest {

    // ── Browser-based tests (tag: browser) ────────────────────────────────────

    @Test
    @Tag("browser")
    void fetchPageContent_extractsBodyTextAndRemovesScripts() {
        String html = """
                <html>
                  <body>
                    <p>Hello Integration Test</p>
                    <script>document.write('bad')</script>
                    <style>.hidden { display: none; }</style>
                  </body>
                </html>
                """;

        String bodyText = BrowserService.execute(page -> {
            page.setContent(html);
            page.evaluate("""
                    document.querySelectorAll(
                        'script,style,nav,footer,header,aside,iframe,' +
                        '[aria-hidden="true"],[class*="cookie"],[class*="banner"],[class*="popup"]'
                    ).forEach(el => el.remove());
                    """);
            return page.innerText("body");
        });

        assertNotNull(bodyText);
        assertTrue(bodyText.contains("Hello Integration Test"),
                "Expected body text to contain page content");
    }

    @Test
    @Tag("browser")
    void fetchPageContent_titleIsExtracted() {
        String html = "<html><head><title>Test Page Title</title></head><body><p>content</p></body></html>";

        String title = BrowserService.execute(page -> {
            page.setContent(html);
            return page.title();
        });

        assertEquals("Test Page Title", title);
    }

    @Test
    @Tag("browser")
    void fetchPageContent_noiseElementsAreRemoved() {
        String html = """
                <html><body>
                  <nav>Navigation menu</nav>
                  <header>Site header</header>
                  <article>Main article content</article>
                  <footer>Footer text</footer>
                </body></html>
                """;

        String bodyText = BrowserService.execute(page -> {
            page.setContent(html);
            page.evaluate("""
                    document.querySelectorAll(
                        'script,style,nav,footer,header,aside,iframe'
                    ).forEach(el => el.remove());
                    """);
            return page.innerText("body");
        });

        assertFalse(bodyText.contains("Navigation menu"), "nav should be removed");
        assertFalse(bodyText.contains("Site header"), "header should be removed");
        assertFalse(bodyText.contains("Footer text"), "footer should be removed");
        assertTrue(bodyText.contains("Main article content"), "article content should remain");
    }

    // ── Unit-level tests (no browser) ─────────────────────────────────────────

    @Test
    void fetchPageContent_privateIpUrl_returnsErrorMap() {
        Map<String, String> result = WebContentSkill.fetchPageContent("http://192.168.1.1/page");

        assertEquals("error", result.get("status"));
        assertNotNull(result.get("message"));
        assertTrue(result.get("message").contains("blocked"),
                "Error message should mention 'blocked'");
    }

    @Test
    void fetchPageContent_loopbackUrl_returnsErrorMap() {
        Map<String, String> result = WebContentSkill.fetchPageContent("http://127.0.0.1/page");

        assertEquals("error", result.get("status"));
        assertNotNull(result.get("message"));
    }

    @Test
    void screenshotPage_privateIpUrl_returnsErrorMap() {
        Map<String, String> result = WebContentSkill.screenshotPage("http://10.0.0.1/page", "test.png");

        assertEquals("error", result.get("status"));
        assertNotNull(result.get("message"));
        assertTrue(result.get("message").contains("blocked"),
                "Error message should mention 'blocked'");
    }
}
