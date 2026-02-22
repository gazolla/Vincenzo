package com.gazapps.util;

/**
 * Canonical status values for the {@code "status"} key in skill result maps.
 *
 * <p>Skills use this enum internally to avoid hardcoded string literals.
 * The string written into the map remains unchanged ("success", "error",
 * "not_found"), preserving full ADK and test compatibility.</p>
 */
public enum SkillStatus {

    SUCCESS("success"),
    ERROR("error"),
    NOT_FOUND("not_found");

    private final String value;

    SkillStatus(String value) {
        this.value = value;
    }

    /** Returns the wire-format string (e.g. {@code "success"}). */
    public String value() {
        return value;
    }
}
