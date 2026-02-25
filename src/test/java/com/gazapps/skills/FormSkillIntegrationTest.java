package com.gazapps.skills;

import com.gazapps.services.BrowserService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for FormSkill browser interaction.
 *
 * <p>
 * Separate from {@link FormSkillTest} which covers the static
 * {@code parseJsonFields}
 * parsing logic. These tests validate the JavaScript form-filling and click
 * behavior
 * using a real Chromium browser via {@code page.setContent()}.
 *
 * <p>
 * Prerequisite: run
 * {@code mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args=install}
 */
class FormSkillIntegrationTest {

    // ── Browser-based tests ────────────────────────────────────────────────────

    @Test
    @Tag("browser")
    void fillForm_setsTextInputValues() {
        String html = """
                <html><body>
                  <input id="name" type="text" />
                  <input id="city" type="text" />
                </body></html>
                """;

        Map<String, String> fields = Map.of("#name", "Alice", "#city", "New York");

        String nameValue = BrowserService.execute(page -> {
            page.setContent(html);
            // Replicate the fill script from FormSkill
            page.evaluate("""
                    (function(f) {
                        Object.keys(f).forEach(function(sel) {
                            var el = document.querySelector(sel);
                            if (!el) return;
                            var nativeInputValueSetter = Object.getOwnPropertyDescriptor(
                                window.HTMLInputElement.prototype, 'value').set;
                            nativeInputValueSetter.call(el, f[sel]);
                            el.dispatchEvent(new Event('input', { bubbles: true }));
                            el.dispatchEvent(new Event('change', { bubbles: true }));
                        });
                    })(arg)
                    """, fields);
            return (String) page.evaluate("document.querySelector('#name').value");
        });

        assertEquals("Alice", nameValue, "Input #name should contain the filled value");
    }

    @Test
    @Tag("browser")
    void fillForm_buttonClickUpdatesDOM() {
        String html = """
                <html><body>
                  <input id="query" type="text" />
                  <button id="btn"
                    onclick="document.getElementById('result').innerText='submitted:' + document.getElementById('query').value">
                    Search
                  </button>
                  <div id="result"></div>
                </body></html>
                """;

        Map<String, String> fields = Map.of("#query", "test input");

        String resultText = BrowserService.execute(page -> {
            page.setContent(html);
            page.evaluate("""
                    (function(f) {
                        Object.keys(f).forEach(function(sel) {
                            var el = document.querySelector(sel);
                            if (!el) return;
                            var nativeInputValueSetter = Object.getOwnPropertyDescriptor(
                                window.HTMLInputElement.prototype, 'value').set;
                            nativeInputValueSetter.call(el, f[sel]);
                            el.dispatchEvent(new Event('input', { bubbles: true }));
                            el.dispatchEvent(new Event('change', { bubbles: true }));
                        });
                    })(arg)
                    """, fields);
            page.click("#btn");
            return page.innerText("#result");
        });

        assertEquals("submitted:test input", resultText,
                "DOM result should reflect submitted value");
    }

    @Test
    @Tag("browser")
    void fillForm_selectDropdownSetsValue() {
        String html = """
                <html><body>
                  <select id="color">
                    <option value="">--choose--</option>
                    <option value="red">Red</option>
                    <option value="blue">Blue</option>
                  </select>
                </body></html>
                """;

        Map<String, String> fields = Map.of("#color", "blue");

        String selectedValue = BrowserService.execute(page -> {
            page.setContent(html);
            page.evaluate("""
                    (function(f) {
                        Object.keys(f).forEach(function(sel) {
                            var el = document.querySelector(sel);
                            if (!el) return;
                            if (el.tagName === 'SELECT') {
                                el.value = f[sel];
                                el.dispatchEvent(new Event('change', { bubbles: true }));
                            }
                        });
                    })(arg)
                    """, fields);
            return (String) page.evaluate("document.querySelector('#color').value");
        });

        assertEquals("blue", selectedValue, "Select should have 'blue' selected");
    }

    // ── Unit-level tests (no browser) ─────────────────────────────────────────

    @Test
    void fillFormAndSubmit_privateIpUrl_returnsErrorMap() {
        Map<String, String> result = FormSkill.fillFormAndSubmit(
                "http://192.168.0.1/form", "{}", "button", null);

        assertEquals("error", result.get("status"));
        assertNotNull(result.get("message"));
        assertTrue(result.get("message").contains("blocked"),
                "Error message should mention 'blocked'");
    }

    @Test
    void fillFormAndSubmit_loopbackUrl_returnsErrorMap() {
        Map<String, String> result = FormSkill.fillFormAndSubmit(
                "http://127.0.0.1/form", "{}", "button[type=submit]", null);

        assertEquals("error", result.get("status"));
    }
}
