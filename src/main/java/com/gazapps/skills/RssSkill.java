package com.gazapps.skills;

import com.gazapps.config.AppConfig;
import com.gazapps.logging.LogService;
import com.gazapps.util.RetryUtils;
import com.gazapps.util.UrlValidator;
import com.google.adk.tools.Annotations.Schema;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ADK skill for RSS and Atom feed operations.
 *
 * <p>Three tools:
 * <ul>
 *   <li>{@link #discoverFeed(String)} — detect a feed URL from a site's homepage</li>
 *   <li>{@link #readFeed(String, int)} — fetch and parse feed items</li>
 *   <li>{@link #searchInFeed(String, String)} — filter items by keyword</li>
 * </ul>
 *
 * <p>Uses only JDK built-ins for HTTP and XML parsing (no Playwright required).
 * Protects against XXE attacks via {@code DocumentBuilderFactory} feature flags.
 */
public class RssSkill {

    private static final LogService LOG = LogService.getInstance();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(AppConfig.RSS_FETCH_TIMEOUT_SECONDS))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // ── Tool 1: discoverFeed ──────────────────────────────────────────────────

    @Schema(description = """
            Discover the RSS or Atom feed URL from a website's homepage or any page URL.
            Fetches the page's HTML and searches for a <link rel="alternate"> tag.
            Falls back to common feed paths (/rss, /feed, /atom.xml) if the tag is absent.
            Use this when the user provides a site URL and wants to subscribe to its news.
            Returns the feed URL and its type (rss, atom, or unknown).
            """)
    public static Map<String, String> discoverFeed(
            @Schema(name = "url", description = "The website URL to search for a feed (e.g. https://g1.globo.com)") String url) {

        LOG.section("TOOL CALL: discoverFeed");
        LOG.info("RssSkill", "URL: " + url);
        long start = System.currentTimeMillis();

        try {
            UrlValidator.validate(url);
        } catch (IllegalArgumentException e) {
            LOG.warn("RssSkill", "Blocked unsafe URL: " + e.getMessage());
            return errorMap(url, "URL blocked for security reasons: " + e.getMessage());
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", AppConfig.USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,*/*")
                    .GET()
                    .build();

            String html = RetryUtils.withRetry(
                    "RssSkill.discoverFeed",
                    AppConfig.RETRY_MAX_ATTEMPTS,
                    AppConfig.RETRY_INITIAL_DELAY_MS,
                    () -> {
                        HttpResponse<String> resp = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                            throw new java.io.IOException("HTTP " + resp.statusCode());
                        }
                        return resp.body();
                    },
                    body -> body == null || body.isBlank()
            );

            // Search for <link rel="alternate" ...> tags
            String feedUrl   = "";
            String feedType  = "unknown";
            String htmlLower = html.toLowerCase();

            // Try RSS first
            int idx = htmlLower.indexOf("application/rss+xml");
            if (idx >= 0) {
                feedUrl  = extractHref(html, idx);
                feedType = "rss";
            }
            // Then Atom
            if (feedUrl.isEmpty()) {
                idx = htmlLower.indexOf("application/atom+xml");
                if (idx >= 0) {
                    feedUrl  = extractHref(html, idx);
                    feedType = "atom";
                }
            }

            // Fallback: try common feed paths
            if (feedUrl.isEmpty()) {
                String base = baseUrl(url);
                for (String candidate : List.of("/feed", "/rss", "/rss.xml", "/atom.xml", "/feed.xml")) {
                    String candidateUrl = base + candidate;
                    try {
                        UrlValidator.validate(candidateUrl);
                        HttpRequest probe = HttpRequest.newBuilder()
                                .uri(URI.create(candidateUrl))
                                .header("User-Agent", AppConfig.USER_AGENT)
                                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                                .build();
                        HttpResponse<Void> probe200 = HTTP.send(probe, HttpResponse.BodyHandlers.discarding());
                        if (probe200.statusCode() == 200) {
                            feedUrl  = candidateUrl;
                            feedType = candidate.contains("atom") ? "atom" : "rss";
                            break;
                        }
                    } catch (Exception ignored) {
                        // try next candidate
                    }
                }
            }

            Map<String, String> result = new HashMap<>();
            result.put("status",    feedUrl.isEmpty() ? "not_found" : "success");
            result.put("url",       url);
            result.put("feed_url",  feedUrl);
            result.put("feed_type", feedType);
            if (feedUrl.isEmpty()) {
                result.put("message", "No RSS/Atom feed found for this URL");
            }

            LOG.timing("RssSkill", "discoverFeed", System.currentTimeMillis() - start);
            LOG.toolResult("discoverFeed", result);
            return result;

        } catch (Exception e) {
            LOG.error("RssSkill", "discoverFeed failed for " + url, e);
            return errorMap(url, "Feed discovery failed: " + e.getMessage());
        }
    }

    // ── Tool 2: readFeed ─────────────────────────────────────────────────────

    @Schema(description = """
            Fetch and parse an RSS or Atom feed, returning the most recent items.
            Supports both RSS 2.0 and Atom 1.0 formats.
            Returns structured data: feed title, item count, and a JSON array of items
            each with title, link, pubDate, and description fields.
            Use discoverFeed first if you only have a site URL (not a direct feed URL).
            """)
    public static Map<String, String> readFeed(
            @Schema(name = "feedUrl", description = "The direct URL of the RSS or Atom feed (e.g. https://g1.globo.com/feed/)") String feedUrl,
            @Schema(name = "maxItems", description = "Maximum number of items to return (capped at rss.max.items config, default 20)") int maxItems) {

        LOG.section("TOOL CALL: readFeed");
        LOG.info("RssSkill", "Feed URL: " + feedUrl + ", maxItems: " + maxItems);
        long start = System.currentTimeMillis();

        try {
            UrlValidator.validate(feedUrl);
        } catch (IllegalArgumentException e) {
            LOG.warn("RssSkill", "Blocked unsafe URL: " + e.getMessage());
            return errorMap(feedUrl, "URL blocked for security reasons: " + e.getMessage());
        }

        int effectiveMax = Math.min(maxItems, AppConfig.RSS_MAX_ITEMS);

        try {
            byte[] bytes = fetchBytes(feedUrl);
            List<FeedItem> items = parseItems(bytes, effectiveMax);
            String feedTitle = parseFeedTitle(bytes);

            JsonArray jsonItems = itemsToJson(items);

            Map<String, String> result = new HashMap<>();
            result.put("status",      "success");
            result.put("feed_url",    feedUrl);
            result.put("feed_title",  feedTitle);
            result.put("item_count",  String.valueOf(items.size()));
            result.put("items",       jsonItems.toString());

            LOG.timing("RssSkill", "readFeed", System.currentTimeMillis() - start);
            LOG.toolResult("readFeed", result);
            return result;

        } catch (Exception e) {
            LOG.error("RssSkill", "readFeed failed for " + feedUrl, e);
            return errorMap(feedUrl, "Feed read failed: " + e.getMessage());
        }
    }

    // ── Tool 3: searchInFeed ─────────────────────────────────────────────────

    @Schema(description = """
            Fetch an RSS or Atom feed and return only the items that contain a keyword
            in their title or description (case-insensitive).
            Prefer this over readFeed when looking for something specific within a feed.
            Returns matching items with their titles, links, dates, and descriptions.
            Also used internally by the SchedulerSkill to detect keyword matches.
            """)
    public static Map<String, String> searchInFeed(
            @Schema(name = "feedUrl", description = "The direct URL of the RSS or Atom feed") String feedUrl,
            @Schema(name = "keyword", description = "The keyword to search for in item titles and descriptions (case-insensitive)") String keyword) {

        LOG.section("TOOL CALL: searchInFeed");
        LOG.info("RssSkill", "Feed URL: " + feedUrl + ", keyword: " + keyword);
        long start = System.currentTimeMillis();

        if (keyword == null || keyword.isBlank()) {
            return errorMap(feedUrl, "keyword must not be blank");
        }

        try {
            UrlValidator.validate(feedUrl);
        } catch (IllegalArgumentException e) {
            LOG.warn("RssSkill", "Blocked unsafe URL: " + e.getMessage());
            return errorMap(feedUrl, "URL blocked for security reasons: " + e.getMessage());
        }

        try {
            byte[] bytes = fetchBytes(feedUrl);
            List<FeedItem> allItems = parseItems(bytes, AppConfig.RSS_MAX_ITEMS);

            String kw = keyword.toLowerCase();
            List<FeedItem> matches = new ArrayList<>();
            for (FeedItem item : allItems) {
                if (item.title.toLowerCase().contains(kw)
                        || item.description.toLowerCase().contains(kw)) {
                    matches.add(item);
                }
            }

            JsonArray jsonMatches = itemsToJson(matches);

            Map<String, String> result = new HashMap<>();
            result.put("status",      "success");
            result.put("feed_url",    feedUrl);
            result.put("keyword",     keyword);
            result.put("match_count", String.valueOf(matches.size()));
            result.put("matches",     jsonMatches.toString());

            LOG.timing("RssSkill", "searchInFeed", System.currentTimeMillis() - start);
            LOG.toolResult("searchInFeed", result);
            return result;

        } catch (Exception e) {
            LOG.error("RssSkill", "searchInFeed failed for " + feedUrl, e);
            return errorMap(feedUrl, "Feed search failed: " + e.getMessage());
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /** Simple DTO for a parsed feed item. */
    static class FeedItem {
        String title       = "";
        String link        = "";
        String pubDate     = "";
        String description = "";
    }

    /**
     * Fetch raw bytes from a URL with retry (shared by readFeed and searchInFeed).
     */
    private static byte[] fetchBytes(String feedUrl) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(feedUrl))
                .header("User-Agent", AppConfig.USER_AGENT)
                .header("Accept", "application/rss+xml,application/atom+xml,application/xml,text/xml,*/*")
                .GET()
                .build();

        return RetryUtils.withRetry(
                "RssSkill.fetchBytes",
                AppConfig.RETRY_MAX_ATTEMPTS,
                AppConfig.RETRY_INITIAL_DELAY_MS,
                () -> {
                    HttpResponse<byte[]> resp = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
                    if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                        throw new java.io.IOException("HTTP " + resp.statusCode());
                    }
                    return resp.body();
                },
                bytes -> bytes == null || bytes.length == 0
        );
    }

    /**
     * Parse RSS 2.0 or Atom 1.0 feed bytes into a list of FeedItems.
     * Protected against XXE attacks via DocumentBuilderFactory feature flags.
     */
    static List<FeedItem> parseItems(byte[] bytes, int maxItems) throws Exception {
        Document doc = parseXml(bytes);
        Element root = doc.getDocumentElement();
        String rootTag = root.getTagName().toLowerCase();

        List<FeedItem> items = new ArrayList<>();

        if (rootTag.equals("rss") || rootTag.contains("rss")) {
            // RSS 2.0
            NodeList nodes = doc.getElementsByTagName("item");
            int limit = Math.min(nodes.getLength(), maxItems);
            for (int i = 0; i < limit; i++) {
                Element el = (Element) nodes.item(i);
                FeedItem item = new FeedItem();
                item.title       = textOf(el, "title");
                item.link        = textOf(el, "link");
                item.pubDate     = textOf(el, "pubDate");
                item.description = truncate(stripHtml(textOf(el, "description")));
                items.add(item);
            }
        } else {
            // Atom 1.0 (root = "feed")
            NodeList nodes = doc.getElementsByTagName("entry");
            int limit = Math.min(nodes.getLength(), maxItems);
            for (int i = 0; i < limit; i++) {
                Element el = (Element) nodes.item(i);
                FeedItem item = new FeedItem();
                item.title   = textOf(el, "title");
                item.pubDate = firstNonEmpty(textOf(el, "updated"), textOf(el, "published"));
                // Atom link is an element with href attribute
                NodeList linkEls = el.getElementsByTagName("link");
                if (linkEls.getLength() > 0) {
                    Element linkEl = (Element) linkEls.item(0);
                    item.link = linkEl.getAttribute("href");
                    if (item.link.isEmpty()) item.link = linkEl.getTextContent().trim();
                }
                item.description = truncate(stripHtml(
                        firstNonEmpty(textOf(el, "summary"), textOf(el, "content"))));
                items.add(item);
            }
        }
        return items;
    }

    /** Extract the feed/channel title from the document. */
    private static String parseFeedTitle(byte[] bytes) {
        try {
            Document doc = parseXml(bytes);
            // RSS: <channel><title>
            NodeList channels = doc.getElementsByTagName("channel");
            if (channels.getLength() > 0) {
                String t = textOf((Element) channels.item(0), "title");
                if (!t.isEmpty()) return t;
            }
            // Atom: top-level <title>
            NodeList titles = doc.getElementsByTagName("title");
            if (titles.getLength() > 0) {
                return titles.item(0).getTextContent().trim();
            }
        } catch (Exception ignored) {}
        return "";
    }

    /** Build a secure DocumentBuilder and parse bytes into a DOM Document. */
    private static Document parseXml(byte[] bytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // XXE prevention
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(null); // suppress stderr warnings
        return builder.parse(new ByteArrayInputStream(bytes));
    }

    /** Serialize a list of FeedItems to a Gson JsonArray. */
    private static JsonArray itemsToJson(List<FeedItem> items) {
        JsonArray arr = new JsonArray();
        for (FeedItem item : items) {
            JsonObject obj = new JsonObject();
            obj.addProperty("title",       item.title);
            obj.addProperty("link",        item.link);
            obj.addProperty("pubDate",     item.pubDate);
            obj.addProperty("description", item.description);
            arr.add(obj);
        }
        return arr;
    }

    /** Get the text content of the first child element with the given tag. */
    private static String textOf(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        if (nl.getLength() > 0) return nl.item(0).getTextContent().trim();
        return "";
    }

    /** Return the first non-empty string from the candidates. */
    private static String firstNonEmpty(String... candidates) {
        for (String c : candidates) if (c != null && !c.isBlank()) return c;
        return "";
    }

    /** Strip basic HTML tags from a string. */
    private static String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]+>", " ")
                   .replaceAll("\\s{2,}", " ")
                   .trim();
    }

    /** Truncate description to RSS_MAX_DESCRIPTION_CHARS. */
    private static String truncate(String text) {
        if (text == null) return "";
        int max = AppConfig.RSS_MAX_DESCRIPTION_CHARS;
        if (text.length() > max) return text.substring(0, max) + "...";
        return text;
    }

    /** Extract href="..." from the area around an indexOf hit in the raw HTML. */
    private static String extractHref(String html, int nearIdx) {
        // Search backwards for the start of the <link tag
        int tagStart = html.lastIndexOf('<', nearIdx);
        if (tagStart < 0) return "";
        int tagEnd = html.indexOf('>', nearIdx);
        if (tagEnd < 0) return "";
        String tag = html.substring(tagStart, tagEnd + 1);

        // Find href attribute value
        int hIdx = tag.toLowerCase().indexOf("href=");
        if (hIdx < 0) return "";
        int hStart = hIdx + 5;
        char quote = tag.charAt(hStart);
        if (quote == '"' || quote == '\'') {
            int hEnd = tag.indexOf(quote, hStart + 1);
            if (hEnd > hStart) return tag.substring(hStart + 1, hEnd).trim();
        }
        return "";
    }

    /** Extract the scheme+host base URL from a full URL string. */
    private static String baseUrl(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getScheme() + "://" + uri.getHost()
                    + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        } catch (Exception e) {
            return url;
        }
    }

    /** Build a standard error result map. */
    private static Map<String, String> errorMap(String url, String message) {
        Map<String, String> err = new HashMap<>();
        err.put("status",  "error");
        err.put("url",     url != null ? url : "");
        err.put("message", message);
        return err;
    }
}
