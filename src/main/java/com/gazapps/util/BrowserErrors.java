package com.gazapps.util;

/**
 * Classifies browser/network exceptions into a short, LLM-friendly error type.
 *
 * <p>Used by all browser-based skills to populate the {@code "error_type"} key
 * in the result map, so the LLM can give the user a more precise error message
 * (e.g. "the site timed out" vs "the site blocked access").</p>
 *
 * <p>Return values:
 * <ul>
 *   <li>{@code "timeout"}  — navigation or response timed out</li>
 *   <li>{@code "network"}  — DNS failure or connection refused</li>
 *   <li>{@code "blocked"}  — HTTP 403/401 or bot-detection wall</li>
 *   <li>{@code "unknown"}  — anything else</li>
 * </ul>
 */
public final class BrowserErrors {

    private BrowserErrors() {}

    /**
     * Classifies the given exception and returns a short error-type string.
     * Never throws; returns {@code "unknown"} for {@code null} input.
     */
    public static String classify(Exception e) {
        if (e == null) return "unknown";
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        String cls = e.getClass().getSimpleName().toLowerCase();

        if (cls.contains("timeout") || msg.contains("timeout") || msg.contains("timed out"))
            return "timeout";

        if (msg.contains("net::err_name_not_resolved") || msg.contains("connection refused")
                || msg.contains("err_connection") || msg.contains("unreachable"))
            return "network";

        if (msg.contains("403") || msg.contains("401") || msg.contains("forbidden")
                || msg.contains("unauthorized") || msg.contains("bot"))
            return "blocked";

        return "unknown";
    }
}
