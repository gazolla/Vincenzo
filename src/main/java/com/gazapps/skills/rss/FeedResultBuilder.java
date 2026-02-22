package com.gazapps.skills.rss;

import com.gazapps.util.SkillStatus;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory for the {@code Map<String, String>} result objects returned by
 * RssSkill tools.
 *
 * <p>
 * Centralising map construction here eliminates copy-paste across the three
 * tools.
 */
public final class FeedResultBuilder {

    private FeedResultBuilder() {
    }

    /** Build a standard error map. */
    public static Map<String, String> error(String url, String message) {
        Map<String, String> err = new HashMap<>();
        err.put("status", SkillStatus.ERROR.value());
        err.put("url", url != null ? url : "");
        err.put("message", message);
        return err;
    }

    /** Build the success result for {@code discoverFeed}. */
    public static Map<String, String> discoverSuccess(String url, String feedUrl, String feedType) {
        Map<String, String> result = new HashMap<>();
        result.put("status", feedUrl.isEmpty() ? SkillStatus.NOT_FOUND.value() : SkillStatus.SUCCESS.value());
        result.put("url", url);
        result.put("feed_url", feedUrl);
        result.put("feed_type", feedType);
        if (feedUrl.isEmpty()) {
            result.put("message", "No RSS/Atom feed found for this URL");
        }
        return result;
    }

    /** Build the success result for {@code readFeed}. */
    public static Map<String, String> readSuccess(String feedUrl, String feedTitle,
            List<FeedItem> items) {
        Map<String, String> result = new HashMap<>();
        result.put("status", SkillStatus.SUCCESS.value());
        result.put("feed_url", feedUrl);
        result.put("feed_title", feedTitle);
        result.put("item_count", String.valueOf(items.size()));
        result.put("items", toJsonArray(items).toString());
        return result;
    }

    /** Build the success result for {@code searchInFeed}. */
    public static Map<String, String> searchSuccess(String feedUrl, String keyword,
            List<FeedItem> matches) {
        Map<String, String> result = new HashMap<>();
        result.put("status", SkillStatus.SUCCESS.value());
        result.put("feed_url", feedUrl);
        result.put("keyword", keyword);
        result.put("match_count", String.valueOf(matches.size()));
        result.put("matches", toJsonArray(matches).toString());
        return result;
    }

    // ── private ──

    private static JsonArray toJsonArray(List<FeedItem> items) {
        JsonArray arr = new JsonArray();
        for (FeedItem item : items) {
            JsonObject obj = new JsonObject();
            obj.addProperty("title", item.title);
            obj.addProperty("link", item.link);
            obj.addProperty("pubDate", item.pubDate);
            obj.addProperty("description", item.description);
            arr.add(obj);
        }
        return arr;
    }
}
