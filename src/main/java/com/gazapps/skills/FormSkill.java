package com.gazapps.skills;

import com.gazapps.config.AppConfig;
import com.gazapps.logging.LogService;
import com.gazapps.services.BrowserService;
import com.gazapps.util.StringUtils;
import com.google.adk.tools.Annotations.Schema;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

import java.util.HashMap;
import java.util.Map;

/**
 * ADK tool that fills and submits HTML forms via Playwright.
 * Useful for sites that require form interaction and have no public API
 * (e.g., bus ticket search, government portals, CEP lookup).
 */
public class FormSkill {

    private static final LogService LOG = LogService.getInstance();

    @Schema(description = """
            Navigate to a URL, fill HTML form fields, and submit the form.
            Use this for sites that require form interaction and have no public API,
            such as bus/flight ticket search, government portals, or CEP lookup.
            The fields parameter is a JSON object mapping CSS selectors to values.
            The submitSelector is the CSS selector for the submit button.
            Example fields: {"#origin": "Brasilia", "#destination": "São Paulo", "#date": "20/03/2026"}
            Example submitSelector: "button[type=submit]" or "#search-btn"
            Returns the result page content after form submission.
            """)
    public static Map<String, String> fillFormAndSubmit(
            @Schema(name = "url", description = "The full URL of the page containing the form") String url,
            @Schema(name = "fields", description = "JSON object mapping CSS selectors to values, e.g. {\"#name\":\"João\",\"#city\":\"SP\"}") String fields,
            @Schema(name = "submitSelector", description = "CSS selector of the submit button, e.g. 'button[type=submit]' or '#search-btn'") String submitSelector) {

        LOG.section("TOOL CALL: fillFormAndSubmit");
        LOG.info("FormSkill", "URL: " + url);
        LOG.info("FormSkill", "Fields: " + StringUtils.truncate(fields, 300));
        LOG.info("FormSkill", "Submit selector: " + submitSelector);
        long start = System.currentTimeMillis();

        return BrowserService.execute(page -> {
            try {
                page.navigate(url, new Page.NavigateOptions()
                        .setTimeout(AppConfig.FORM_NAVIGATE_TIMEOUT_MS));
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                LOG.timing("FormSkill", "Navigation", System.currentTimeMillis() - start);

                // Fill each form field using a JavaScript-driven approach to handle
                // both input fields and select elements uniformly.
                // The fields JSON is passed as a safe Playwright argument — never interpolated.
                String fillScript = """
                        (function(fieldsObj) {
                            try {
                                var filled = 0;
                                var errors = [];
                                Object.keys(fieldsObj).forEach(function(selector) {
                                    var el = document.querySelector(selector);
                                    if (!el) {
                                        errors.push('Not found: ' + selector);
                                        return;
                                    }
                                    var val = fieldsObj[selector];
                                    if (el.tagName === 'SELECT') {
                                        var opts = Array.from(el.options);
                                        var match = opts.find(function(o) {
                                            return o.value === val || o.text.toLowerCase().includes(val.toLowerCase());
                                        });
                                        if (match) { el.value = match.value; filled++; }
                                        else errors.push('Option not found for: ' + selector + ' = ' + val);
                                    } else {
                                        el.focus();
                                        el.value = val;
                                        el.dispatchEvent(new Event('input', {bubbles: true}));
                                        el.dispatchEvent(new Event('change', {bubbles: true}));
                                        filled++;
                                    }
                                });
                                return JSON.stringify({filled: filled, errors: errors});
                            } catch(e) {
                                return JSON.stringify({filled: 0, errors: [e.message]});
                            }
                        })(arg)
                        """;

                // Parse fields JSON into a Java Map and pass it as a safe Playwright arg
                java.util.Map<String, String> fieldsMap = parseJsonFields(fields);
                Object fillResult = page.evaluate(fillScript, fieldsMap);
                LOG.info("FormSkill", "Fill result: " + fillResult);

                // Submit the form
                page.click(submitSelector);
                LOG.info("FormSkill", "Clicked submit: " + submitSelector);

                // Wait for result page to load
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                page.waitForTimeout(AppConfig.FORM_AFTER_SUBMIT_WAIT_MS);
                LOG.timing("FormSkill", "Post-submit wait", System.currentTimeMillis() - start);

                String responseUrl = page.url();
                LOG.info("FormSkill", "Response URL: " + responseUrl);

                // Remove noise and extract result text
                page.evaluate("""
                        document.querySelectorAll(
                            'script,style,nav,footer,header,aside,iframe,' +
                            '[aria-hidden="true"],[class*="cookie"],[class*="banner"],[class*="popup"]'
                        ).forEach(el => el.remove());
                        """);

                String resultText = page.innerText("body");
                int rawLen = resultText.length();
                LOG.info("FormSkill", "Result page text length: " + rawLen + " chars");

                if (resultText.length() > AppConfig.FORM_RESULT_MAX_CHARS) {
                    resultText = resultText.substring(0, AppConfig.FORM_RESULT_MAX_CHARS)
                            + "... [content truncated]";
                }

                // Extract fields_filled count from JS result
                String fieldsFilled = "0";
                if (fillResult != null) {
                    String fr = fillResult.toString();
                    int idx = fr.indexOf("\"filled\":");
                    if (idx >= 0) {
                        int end = fr.indexOf(',', idx);
                        if (end < 0) end = fr.indexOf('}', idx);
                        fieldsFilled = fr.substring(idx + 9, end).trim();
                    }
                }

                Map<String, String> result = new HashMap<>();
                result.put("status", "success");
                result.put("url", url);
                result.put("response_url", responseUrl);
                result.put("result_content", resultText);
                result.put("fields_filled", fieldsFilled);

                LOG.timing("FormSkill", "Total execution", System.currentTimeMillis() - start);
                LOG.toolResult("fillFormAndSubmit", result);
                return result;

            } catch (Exception e) {
                LOG.error("FormSkill", "Failed for " + url, e);
                Map<String, String> error = new HashMap<>();
                error.put("status", "error");
                error.put("url", url);
                error.put("message", "Form fill/submit failed: " + e.getMessage());
                return error;
            }
        });
    }

    /**
     * Parses a simple JSON object string like {"#sel": "value", ...} into a Map.
     * Uses basic string parsing — sufficient for the flat key/value structure the LLM produces.
     */
    static java.util.Map<String, String> parseJsonFields(String json) {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        if (json == null || json.isBlank()) return map;
        // Strip outer braces
        String inner = json.trim();
        if (inner.startsWith("{")) inner = inner.substring(1);
        if (inner.endsWith("}")) inner = inner.substring(0, inner.length() - 1);
        // Split by commas that are outside quotes (simple heuristic: split on "," followed by optional space and ")
        String[] pairs = inner.split(",(?=\\s*\")");
        for (String pair : pairs) {
            int colon = pair.indexOf(':');
            if (colon < 0) continue;
            String key = pair.substring(0, colon).trim().replaceAll("^\"|\"$", "");
            String val = pair.substring(colon + 1).trim().replaceAll("^\"|\"$", "");
            map.put(key, val);
        }
        return map;
    }
}
