package com.gazapps.skills;

import com.gazapps.config.AppConfig;
import com.gazapps.logging.LogService;
import com.gazapps.services.BrowserService;
import com.gazapps.util.BrowserErrors;
import com.gazapps.util.RetryUtils;
import com.gazapps.util.SessionState;
import com.gazapps.util.SkillStatus;
import com.gazapps.util.StringUtils;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.ToolContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

import java.util.HashMap;
import java.util.Map;

/**
 * ADK tool that extracts structured data from a web page using CSS selectors.
 * The LLM provides a JSON map of field names to CSS selectors; this skill
 * navigates to the URL, runs the selectors, and returns matched values as JSON.
 */
public class ExtractSkill {

    private static final LogService LOG = LogService.getInstance();

    @Schema(description = """
            Extract structured data from a web page using CSS selectors.
            Use this to extract prices, product names, links, tables, listings, or any
            repeating structured content from a page. The selectors parameter
            must be a JSON object mapping field names to CSS selectors, e.g.:
            {"title": ".product-name", "price": ".price-tag", "rating": ".stars"}
            To extract a URL/href from a link, append "|href" to the selector, e.g.:
            {"title": ".titleline > a", "link": ".titleline > a|href"}
            To extract any HTML attribute, append "|attrName" to the selector, e.g.:
            {"image": "img.cover|src", "data": "div.item|data-id"}
            Returns extracted data as a JSON array of matched items.
            """)
    public static Map<String, String> extractStructuredData(
            @Schema(name = "url", description = "The full URL of the page to extract data from") String url,
            @Schema(name = "selectors", description = "JSON object mapping field names to CSS selectors. For text: {\"title\":\".product-name\",\"price\":\".price\"}. For attributes append |attrName: {\"link\":\".titleline > a|href\",\"img\":\"img.cover|src\"}") String selectors,
            ToolContext toolContext) {

        LOG.section("TOOL CALL: extractStructuredData");
        LOG.info("ExtractSkill", "URL: " + url);
        LOG.info("ExtractSkill", "Selectors: " + StringUtils.truncate(selectors, 300));
        long start = System.currentTimeMillis();

        try {
            return RetryUtils.withRetry(
                    "ExtractSkill.extractStructuredData",
                    AppConfig.RETRY_MAX_ATTEMPTS,
                    AppConfig.RETRY_INITIAL_DELAY_MS,
                    () -> BrowserService.execute(page -> {
                        try {
                            page.navigate(url, new Page.NavigateOptions()
                                    .setTimeout(AppConfig.EXTRACT_NAVIGATE_TIMEOUT_MS));
                            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                            LOG.timing("ExtractSkill", "Navigation", System.currentTimeMillis() - start);

                            // Build and run a JavaScript extraction script using the provided selectors.
                            // Playwright passes the selectorsMap as the arrow-function parameter (sels).
                            // maxItems is a Java constant (not user data), so it is safely interpolated.
                            //
                            // Selector syntax: "cssSelector" for text, "cssSelector|attr" for attributes.
                            // Examples: ".title" → innerText; ".link|href" → href attribute value.
                            String extractScript = """
                                    sels => {
                                        try {
                                            var maxItems = %d;
                                            var fields = Object.keys(sels);
                                            if (fields.length === 0) return JSON.stringify([]);

                                            // Parse each selector into {css, attr} — attr is null for text extraction
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

                                            // Collect NodeLists for each selector
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
                                                        // Attribute extraction: href, src, data-*, etc.
                                                        item[field] = (node.getAttribute(attr) || '').trim();
                                                    } else {
                                                        // Text extraction
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
                                    """
                                    .formatted(AppConfig.EXTRACT_MAX_ITEMS);

                            // Pass only the selectors Map as arg — Playwright serializes Maps safely
                            java.util.Map<String, String> selectorsMap = FormSkill.parseJsonFields(selectors);
                            Object raw = page.evaluate(extractScript, selectorsMap);
                            String data = raw != null ? raw.toString() : "[]";
                            LOG.debug("ExtractSkill", "Extracted data: " + StringUtils.truncate(data, 500));

                            // Count items in result
                            int itemCount = 0;
                            if (data.startsWith("[")) {
                                for (int i = 0; i < data.length(); i++) {
                                    if (data.charAt(i) == '{')
                                        itemCount++;
                                }
                            }
                            LOG.info("ExtractSkill", "Items extracted: " + itemCount);

                            Map<String, String> result = new HashMap<>();
                            result.put("status", SkillStatus.SUCCESS.value());
                            result.put("url", url);
                            result.put("data", data);
                            result.put("item_count", String.valueOf(itemCount));
                            result.put("selectors_used", selectors);

                            // Save to session.state for cross-tool context within the same turn
                            SessionState.put(toolContext, SessionState.KEY_LAST_EXTRACT_URL, url);
                            SessionState.put(toolContext, SessionState.KEY_LAST_EXTRACT_DATA, data);

                            LOG.timing("ExtractSkill", "Total execution", System.currentTimeMillis() - start);
                            LOG.toolResult("extractStructuredData", result);
                            return result;

                        } catch (Exception e) {
                            LOG.error("ExtractSkill", "Failed for " + url, e);
                            Map<String, String> error = new HashMap<>();
                            error.put("status", SkillStatus.ERROR.value());
                            error.put("url", url);
                            error.put("error_type", BrowserErrors.classify(e));
                            error.put("message", "Failed to extract structured data: " + e.getMessage());
                            return error;
                        }
                    }),
                    result -> SkillStatus.ERROR.value().equals(result.get("status")));
        } catch (Exception e) {
            LOG.error("ExtractSkill", "Failed for " + url + " after retries", e);
            Map<String, String> error = new HashMap<>();
            error.put("status", SkillStatus.ERROR.value());
            error.put("url", url);
            error.put("error_type", BrowserErrors.classify(e));
            error.put("message", "Failed to extract structured data: " + e.getMessage());
            return error;
        }
    }
}
