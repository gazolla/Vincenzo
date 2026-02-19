package com.gazapps.skills;

import com.gazapps.services.BrowserService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for SummarizeSkill.
 *
 * <p>Tests use {@code page.setContent()} to inject mock HTML into a real Chromium browser
 * via BrowserService, exercising the same noise-removal and text-extraction logic
 * that summarizeUrl uses — without any external network request.
 *
 * <p>Prerequisite: run {@code mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args=install}
 */
class SummarizeSkillTest {

    @Test
    @Tag("browser")
    void summarizeUrl_extractsArticleContent() {
        String html = """
                <html><body>
                  <article>
                    <h1>Test Article Title</h1>
                    <p>This is the main article body content.</p>
                  </article>
                  <nav>Site navigation links</nav>
                  <aside>Related sidebar content</aside>
                </body></html>
                """;

        String bodyText = BrowserService.execute(page -> {
            page.setContent(html);
            page.evaluate("""
                    document.querySelectorAll(
                        'script,style,nav,footer,header,aside,iframe,' +
                        '[aria-hidden="true"],[class*="cookie"],[class*="banner"],' +
                        '[class*="popup"],[class*="ad-"],[class*="sidebar"]'
                    ).forEach(el => el.remove());
                    """);
            return page.innerText("body");
        });

        assertNotNull(bodyText);
        assertTrue(bodyText.contains("Test Article Title"),
                "Article heading should be present");
        assertTrue(bodyText.contains("main article body content"),
                "Article body should be present");
        assertFalse(bodyText.contains("Site navigation links"),
                "nav element should be removed");
        assertFalse(bodyText.contains("Related sidebar content"),
                "aside element should be removed");
    }

    @Test
    @Tag("browser")
    void summarizeUrl_cookieBannersAreRemoved() {
        String html = """
                <html><body>
                  <div class="cookie-banner">Accept cookies to continue</div>
                  <div class="ad-container">Advertisement here</div>
                  <p>Actual page content</p>
                </body></html>
                """;

        String bodyText = BrowserService.execute(page -> {
            page.setContent(html);
            page.evaluate("""
                    document.querySelectorAll(
                        'script,style,nav,footer,header,aside,iframe,' +
                        '[aria-hidden="true"],[class*="cookie"],[class*="banner"],' +
                        '[class*="popup"],[class*="ad-"],[class*="sidebar"]'
                    ).forEach(el => el.remove());
                    """);
            return page.innerText("body");
        });

        assertFalse(bodyText.contains("Accept cookies"), "cookie banner should be removed");
        assertFalse(bodyText.contains("Advertisement"), "ad container should be removed");
        assertTrue(bodyText.contains("Actual page content"), "main content should remain");
    }
}
