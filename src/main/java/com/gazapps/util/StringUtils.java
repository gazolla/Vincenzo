package com.gazapps.util;

/**
 * General-purpose string utilities shared across the application.
 */
public final class StringUtils {

    private StringUtils() {}

    /**
     * Truncates {@code s} to at most {@code max} characters, appending "..." if truncated.
     * Returns the literal string {@code "null"} when the input is null.
     */
    public static String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
