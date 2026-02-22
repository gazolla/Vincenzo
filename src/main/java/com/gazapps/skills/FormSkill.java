package com.gazapps.skills;

import com.gazapps.config.AppConfig;
import com.gazapps.logging.LogService;
import com.gazapps.services.BrowserService;
import com.gazapps.util.BrowserErrors;
import com.gazapps.util.SkillStatus;
import com.gazapps.util.StringUtils;
import com.gazapps.util.UrlValidator;
import com.google.adk.tools.Annotations.Schema;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
            Example fields: {"#origin": "Chicago", "#destination": "New York", "#date": "03/20/2026"}
            Example submitSelector: "button[type=submit]" or "#search-btn"
            Returns the result page content after form submission.
            """)
    public static Map<String, String> fillFormAndSubmit(
            @Schema(name = "url", description = "The full URL of the page containing the form") String url,
            @Schema(name = "fields", description = "JSON object mapping CSS selectors to values, e.g. {\"#name\":\"Alice\",\"#city\":\"NY\"}") String fields,
            @Schema(name = "submitSelector", description = "CSS selector of the submit button, e.g. 'button[type=submit]' or '#search-btn'") String submitSelector) {

        LOG.section("TOOL CALL: fillFormAndSubmit");
        LOG.info("FormSkill", "URL: " + url);
        LOG.info("FormSkill", "Fields: " + StringUtils.truncate(fields, 300));
        LOG.info("FormSkill", "Submit selector: " + submitSelector);
        long start = System.currentTimeMillis();

        // Fix 1 — SSRF: validate URL before any navigation
        try {
            UrlValidator.validate(url);
        } catch (IllegalArgumentException e) {
            LOG.warn("FormSkill", "Blocked unsafe URL: " + e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("status", SkillStatus.ERROR.value());
            error.put("url", url);
            error.put("message", "URL blocked for security reasons: " + e.getMessage());
            return error;
        }

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

                // Fix 3 — parse fill result with Gson instead of fragile string indexing
                String fieldsFilled = "0";
                if (fillResult != null) {
                    try {
                        JsonObject fr = JsonParser.parseString(fillResult.toString()).getAsJsonObject();
                        fieldsFilled = String.valueOf(fr.get("filled").getAsInt());
                        JsonArray fillErrors = fr.getAsJsonArray("errors");
                        if (fillErrors != null && fillErrors.size() > 0) {
                            LOG.warn("FormSkill", "Fill errors: " + fillErrors);
                        }
                    } catch (Exception parseEx) {
                        LOG.warn("FormSkill", "Could not parse fill result: " + fillResult);
                    }
                }

                Map<String, String> result = new HashMap<>();
                result.put("status", SkillStatus.SUCCESS.value());
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
                error.put("status", SkillStatus.ERROR.value());
                error.put("url", url);
                error.put("error_type", BrowserErrors.classify(e));
                error.put("message", "Form fill/submit failed: " + e.getMessage());
                return error;
            }
        });
    }

    /**
     * Fix 2 — Parses a JSON object string like {"#sel": "value", ...} into a Map.
     * Uses Gson (available as a transitive dependency of google-adk) for correct handling
     * of commas inside values, numeric values, and other edge cases.
     */
    static java.util.Map<String, String> parseJsonFields(String json) {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        if (json == null || json.isBlank()) return map;
        try {
            JsonObject obj = JsonParser.parseString(json.trim()).getAsJsonObject();
            for (var entry : obj.entrySet()) {
                map.put(entry.getKey(), entry.getValue().getAsString());
            }
        } catch (Exception e) {
            LOG.warn("FormSkill", "Failed to parse fields JSON: " + e.getMessage());
        }
        return map;
    }
}
