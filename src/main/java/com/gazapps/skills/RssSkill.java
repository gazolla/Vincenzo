package com.gazapps.skills;

import com.gazapps.config.AppConfig;
import com.gazapps.logging.LogService;
import com.gazapps.skills.rss.FeedHttpClient;
import com.gazapps.skills.rss.FeedItem;
import com.gazapps.skills.rss.FeedParser;
import com.gazapps.skills.rss.FeedResultBuilder;
import com.gazapps.skills.rss.FeedTextUtils;
import com.gazapps.util.UrlValidator;
import com.google.adk.tools.Annotations.Schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ADK skill for RSS and Atom feed operations.
 *
 * <p>
 * Three tools:
 * <ul>
 * <li>{@link #discoverFeed(String)} — detect a feed URL from a site's
 * homepage</li>
 * <li>{@link #readFeed(String, int)} — fetch and parse feed items</li>
 * <li>{@link #searchInFeed(String, String)} — filter items by keyword</li>
 * </ul>
 *
 * <p>
 * This class is a thin orchestrator. HTTP, parsing, text utilities and result
 * construction are each delegated to a focused component in the {@code rss}
 * sub-package.
 */
public class RssSkill {

    private static final LogService LOG = LogService.getInstance();

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
            return FeedResultBuilder.error(url, "URL blocked for security reasons: " + e.getMessage());
        }

        try {
            String html = FeedHttpClient.fetchHtml(url);
            String htmlLower = html.toLowerCase();
            String feedUrl = "";
            String feedType = "unknown";

            // Try RSS then Atom link tags
            int idx = htmlLower.indexOf("application/rss+xml");
            if (idx >= 0) {
                feedUrl = FeedTextUtils.extractHref(html, idx);
                feedType = "rss";
            }
            if (feedUrl.isEmpty()) {
                idx = htmlLower.indexOf("application/atom+xml");
                if (idx >= 0) {
                    feedUrl = FeedTextUtils.extractHref(html, idx);
                    feedType = "atom";
                }
            }

            // Fallback: probe common feed paths
            if (feedUrl.isEmpty()) {
                String base = FeedTextUtils.baseUrl(url);
                for (String path : List.of("/feed", "/rss", "/rss.xml", "/atom.xml", "/feed.xml")) {
                    String candidate = base + path;
                    try {
                        UrlValidator.validate(candidate);
                        if (FeedHttpClient.probe(candidate)) {
                            feedUrl = candidate;
                            feedType = path.contains("atom") ? "atom" : "rss";
                            break;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            Map<String, String> result = FeedResultBuilder.discoverSuccess(url, feedUrl, feedType);
            LOG.timing("RssSkill", "discoverFeed", System.currentTimeMillis() - start);
            LOG.toolResult("discoverFeed", result);
            return result;

        } catch (Exception e) {
            LOG.error("RssSkill", "discoverFeed failed for " + url, e);
            return FeedResultBuilder.error(url, "Feed discovery failed: " + e.getMessage());
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
            return FeedResultBuilder.error(feedUrl, "URL blocked for security reasons: " + e.getMessage());
        }

        try {
            int effectiveMax = Math.min(maxItems, AppConfig.RSS_MAX_ITEMS);
            byte[] bytes = FeedHttpClient.fetchBytes(feedUrl);
            List<FeedItem> items = FeedParser.parseItems(bytes, effectiveMax);
            String feedTitle = FeedParser.parseFeedTitle(bytes);

            Map<String, String> result = FeedResultBuilder.readSuccess(feedUrl, feedTitle, items);
            LOG.timing("RssSkill", "readFeed", System.currentTimeMillis() - start);
            LOG.toolResult("readFeed", result);
            return result;

        } catch (Exception e) {
            LOG.error("RssSkill", "readFeed failed for " + feedUrl, e);
            return FeedResultBuilder.error(feedUrl, "Feed read failed: " + e.getMessage());
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
            return FeedResultBuilder.error(feedUrl, "keyword must not be blank");
        }

        try {
            UrlValidator.validate(feedUrl);
        } catch (IllegalArgumentException e) {
            LOG.warn("RssSkill", "Blocked unsafe URL: " + e.getMessage());
            return FeedResultBuilder.error(feedUrl, "URL blocked for security reasons: " + e.getMessage());
        }

        try {
            byte[] bytes = FeedHttpClient.fetchBytes(feedUrl);
            List<FeedItem> all = FeedParser.parseItems(bytes, AppConfig.RSS_MAX_ITEMS);

            String kw = keyword.toLowerCase();
            List<FeedItem> matches = new ArrayList<>();
            for (FeedItem item : all) {
                if (item.title.toLowerCase().contains(kw)
                        || item.description.toLowerCase().contains(kw)) {
                    matches.add(item);
                }
            }

            Map<String, String> result = FeedResultBuilder.searchSuccess(feedUrl, keyword, matches);
            LOG.timing("RssSkill", "searchInFeed", System.currentTimeMillis() - start);
            LOG.toolResult("searchInFeed", result);
            return result;

        } catch (Exception e) {
            LOG.error("RssSkill", "searchInFeed failed for " + feedUrl, e);
            return FeedResultBuilder.error(feedUrl, "Feed search failed: " + e.getMessage());
        }
    }

    // ── kept for test compatibility (package-private) ─────────────────────────

    /**
     * Delegates to {@link FeedParser#parseItems} — preserved so that
     * {@code RssSkillTest} (same package) can continue to call this directly.
     */
    static List<FeedItem> parseItems(byte[] bytes, int maxItems) throws Exception {
        return FeedParser.parseItems(bytes, maxItems);
    }
}
