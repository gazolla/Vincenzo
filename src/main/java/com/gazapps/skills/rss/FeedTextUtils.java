package com.gazapps.skills.rss;

import com.gazapps.config.AppConfig;

import java.net.URI;

/**
 * Pure text / URL utility methods for RSS feed processing.
 *
 * <p>
 * All methods are stateless and have no I/O side effects,
 * making them straightforward to unit-test in isolation.
 */
public final class FeedTextUtils {

    private FeedTextUtils() {
    }

    /** Strip basic HTML tags from a string and collapse whitespace. */
    public static String stripHtml(String html) {
        if (html == null)
            return "";
        return html.replaceAll("<[^>]+>", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    /**
     * Truncate text to {@code AppConfig.getInstance().RSS_MAX_DESCRIPTION_CHARS}, appending "…"
     * if cut.
     */
    public static String truncate(String text) {
        if (text == null)
            return "";
        int max = AppConfig.getInstance().RSS_MAX_DESCRIPTION_CHARS;
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }

    /**
     * Extract {@code href="..."} from the HTML tag area around an indexOf hit.
     *
     * @param html    full HTML string
     * @param nearIdx index near the hit (e.g. position of "application/rss+xml")
     * @return the href value, or an empty string if not found
     */
    public static String extractHref(String html, int nearIdx) {
        int tagStart = html.lastIndexOf('<', nearIdx);
        if (tagStart < 0)
            return "";
        int tagEnd = html.indexOf('>', nearIdx);
        if (tagEnd < 0)
            return "";
        String tag = html.substring(tagStart, tagEnd + 1);

        int hIdx = tag.toLowerCase().indexOf("href=");
        if (hIdx < 0)
            return "";
        int hStart = hIdx + 5;
        char quote = tag.charAt(hStart);
        if (quote == '"' || quote == '\'') {
            int hEnd = tag.indexOf(quote, hStart + 1);
            if (hEnd > hStart)
                return tag.substring(hStart + 1, hEnd).trim();
        }
        return "";
    }

    /** Extract the scheme+host (+ port if non-default) from a full URL string. */
    public static String baseUrl(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getScheme() + "://" + uri.getHost()
                    + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        } catch (Exception e) {
            return url;
        }
    }
}
