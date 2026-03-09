package com.gazapps.skills;

import com.gazapps.config.AppConfig;
import com.gazapps.logging.LogService;
import com.gazapps.services.MemoryService;
import com.gazapps.services.MemoryService.MemoryEntry;
import com.gazapps.util.SkillStatus;
import com.google.adk.tools.Annotations.Schema;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * ADK skill for persistent long-term memory across sessions.
 *
 * <p>Five tools:
 * <ul>
 *   <li>{@link #saveMemory} — store a fact, preference, note, research summary, or task context</li>
 *   <li>{@link #retrieveMemory} — search by text substring and/or tags</li>
 *   <li>{@link #listMemories} — list all entries, optionally filtered by a single tag</li>
 *   <li>{@link #deleteMemory} — permanently remove an entry by id</li>
 *   <li>{@link #updateMemory} — update content and/or tags of an existing entry</li>
 * </ul>
 *
 * <p>Tags are passed as a comma-separated string (e.g. {@code "preference,python"}) and
 * normalized to lowercase before being stored or matched.
 * Delegates all persistence to {@link MemoryService}.
 */
public final class MemorySkill {

    private static final LogService LOG = LogService.getInstance();

    // ── Tool 1: saveMemory ────────────────────────────────────────────────────

    @Schema(description = """
            Store a memory entry: a user preference, research finding, reminder, note,
            or project/task context. Memories are persisted to disk and survive restarts.
            Each entry has free-form tags for organization (e.g. 'preference,python').
            Use retrieveMemory() or listMemories() to recall stored entries later.
            Always confirm with the user what to store before calling this.
            """)
    public static Map<String, String> saveMemory(
            @Schema(name = "content",
                    description = "The text to store. Can be a fact, preference, summary, note, or task context. Be concise but complete.")
            String content,

            @Schema(name = "tags",
                    description = "Comma-separated tags for this memory (e.g. 'preference,python' or 'work,project-alpha'). Use lowercase, short words.")
            String tags,

            @Schema(name = "category",
                    description = "One of: 'preference', 'note', 'research', 'task'. Defaults to 'note' if omitted.")
            String category) {

        LOG.section("TOOL CALL: saveMemory");
        LOG.info("MemorySkill", "content_length=" + (content != null ? content.length() : 0)
                + " tags=" + tags + " category=" + category);

        if (content == null || content.isBlank()) {
            return errorMap("content must not be blank");
        }

        List<String> tagList = parseTags(tags);
        String safeCategory = (category != null && !category.isBlank()) ? category.trim() : "note";

        Optional<String> savedId = MemoryService.getInstance().save(content, tagList, safeCategory);
        if (savedId.isEmpty()) {
            return errorMap("Memory limit reached (" + AppConfig.getInstance().memoryMaxItems()
                    + "). Delete some entries first using deleteMemory(id).");
        }
        String id = savedId.get();

        Map<String, String> result = new HashMap<>();
        result.put("status",   SkillStatus.SUCCESS.value());
        result.put("id",       id);
        result.put("content",  content);
        result.put("tags",     tagList.toString());
        result.put("category", safeCategory);
        result.put("message",  "Memory saved with id " + id);

        LOG.toolResult("saveMemory", result);
        return result;
    }

    // ── Tool 2: retrieveMemory ────────────────────────────────────────────────

    @Schema(description = """
            Search stored memories by text substring, tags, or both.
            - query: case-insensitive substring match on the content field
            - tags: exact match on any of the provided comma-separated tags (OR logic within tags)
            - Both filters can be combined (AND logic) or used independently
            - Leave both blank to retrieve all memories (equivalent to listMemories)
            Returns a JSON array of matching entries sorted newest-first.
            Always call this before answering questions about user preferences or past context.
            """)
    public static Map<String, String> retrieveMemory(
            @Schema(name = "query",
                    description = "Text substring to search in memory content (case-insensitive). Leave blank to skip text filter.")
            String query,

            @Schema(name = "tags",
                    description = "Comma-separated tags to filter by (e.g. 'preference,python'). An entry matches if it has ANY of these tags. Leave blank to skip tag filter.")
            String tags) {

        LOG.section("TOOL CALL: retrieveMemory");
        LOG.info("MemorySkill", "query=" + query + " tags=" + tags);

        List<String> tagList = parseTags(tags);
        List<MemoryEntry> matches = MemoryService.getInstance().search(query, tagList);

        Map<String, String> result = new HashMap<>();
        result.put("status",      SkillStatus.SUCCESS.value());
        result.put("query",       query != null ? query : "");
        result.put("tags",        tags != null ? tags : "");
        result.put("match_count", String.valueOf(matches.size()));
        result.put("memories",    entriesToJson(matches).toString());

        LOG.toolResult("retrieveMemory", result);
        return result;
    }

    // ── Tool 3: listMemories ──────────────────────────────────────────────────

    @Schema(description = """
            List all stored memories, optionally filtered by a single tag.
            Returns entries sorted newest-first with their id, content, tags, category,
            and timestamps. Use this to show the user a summary of what has been stored.
            Use tag="" to list everything. Use retrieveMemory() for text search.
            """)
    public static Map<String, String> listMemories(
            @Schema(name = "tag",
                    description = "A single tag to filter by (e.g. 'preference'). Leave blank or omit to list all memories.")
            String tag) {

        LOG.section("TOOL CALL: listMemories");
        LOG.info("MemorySkill", "tag=" + tag);

        List<MemoryEntry> all = MemoryService.getInstance().list(tag);

        Map<String, String> result = new HashMap<>();
        result.put("status",      SkillStatus.SUCCESS.value());
        result.put("tag_filter",  tag != null ? tag : "");
        result.put("entry_count", String.valueOf(all.size()));
        result.put("memories",    entriesToJson(all).toString());

        LOG.toolResult("listMemories", result);
        return result;
    }

    // ── Tool 4: deleteMemory ──────────────────────────────────────────────────

    @Schema(description = """
            Permanently delete a memory entry by its id.
            Use listMemories() or retrieveMemory() first to confirm the correct id.
            This cannot be undone. Always confirm with the user before deleting.
            """)
    public static Map<String, String> deleteMemory(
            @Schema(name = "id",
                    description = "The memory entry id to delete (e.g. 'mem-a1b2c3d4'). Use listMemories() to find it.")
            String id) {

        LOG.section("TOOL CALL: deleteMemory");
        LOG.info("MemorySkill", "id=" + id);

        if (id == null || id.isBlank()) {
            return errorMap("id must not be blank");
        }

        boolean deleted = MemoryService.getInstance().delete(id);

        Map<String, String> result = new HashMap<>();
        if (deleted) {
            result.put("status",  SkillStatus.SUCCESS.value());
            result.put("id",      id);
            result.put("message", "Memory entry deleted");
        } else {
            result.put("status",  SkillStatus.ERROR.value());
            result.put("id",      id);
            result.put("message", "Memory entry not found: " + id
                    + ". Use listMemories() to see existing ids.");
        }

        LOG.toolResult("deleteMemory", result);
        return result;
    }

    // ── Tool 5: updateMemory ──────────────────────────────────────────────────

    @Schema(description = """
            Update the content and/or tags of an existing memory entry.
            Only the fields you provide will be changed; leave a field blank to keep it unchanged.
            Providing tags replaces ALL existing tags for that entry.
            The updatedAt timestamp is refreshed automatically.
            Use listMemories() to find the id before updating.
            """)
    public static Map<String, String> updateMemory(
            @Schema(name = "id",
                    description = "The memory entry id to update (e.g. 'mem-a1b2c3d4'). Use listMemories() to find it.")
            String id,

            @Schema(name = "content",
                    description = "New content text. Leave blank to keep the existing content.")
            String content,

            @Schema(name = "tags",
                    description = "New comma-separated tags replacing ALL existing tags (e.g. 'work,python'). Leave blank to keep existing tags.")
            String tags) {

        LOG.section("TOOL CALL: updateMemory");
        LOG.info("MemorySkill", "id=" + id
                + " has_content=" + (content != null && !content.isBlank())
                + " has_tags=" + (tags != null && !tags.isBlank()));

        if (id == null || id.isBlank()) {
            return errorMap("id must not be blank");
        }

        List<String> tagList = (tags != null && !tags.isBlank()) ? parseTags(tags) : null;
        String newContent    = (content != null && !content.isBlank()) ? content : null;

        Optional<MemoryEntry> updatedOpt = MemoryService.getInstance().update(id, newContent, tagList);
        if (updatedOpt.isEmpty()) {
            return errorMap("Memory entry not found: " + id
                    + ". Use listMemories() to see existing ids.");
        }
        MemoryEntry updated = updatedOpt.get();

        Map<String, String> result = new HashMap<>();
        result.put("status",   SkillStatus.SUCCESS.value());
        result.put("id",       updated.id());
        result.put("content",  updated.content());
        result.put("tags",     updated.tags().toString());
        result.put("category", updated.category());
        result.put("message",  "Memory entry updated");

        LOG.toolResult("updateMemory", result);
        return result;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Parse a comma-separated tag string into a trimmed, lowercase list.
     * Example: {@code "preference, Python , work"} → {@code ["preference", "python", "work"]}
     */
    static List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) return new java.util.ArrayList<>();
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }

    /** Serialize a list of {@link MemoryEntry} to a Gson {@link JsonArray}. */
    private static JsonArray entriesToJson(List<MemoryEntry> entries) {
        JsonArray arr = new JsonArray();
        for (MemoryEntry e : entries) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id",        e.id());
            obj.addProperty("content",   e.content());
            obj.addProperty("category",  e.category());
            obj.addProperty("createdAt", e.createdAt());
            obj.addProperty("updatedAt", e.updatedAt());
            JsonArray tagsArr = new JsonArray();
            for (String t : e.tags()) tagsArr.add(t);
            obj.add("tags", tagsArr);
            arr.add(obj);
        }
        return arr;
    }

    private static Map<String, String> errorMap(String message) {
        Map<String, String> err = new HashMap<>();
        err.put("status",  SkillStatus.ERROR.value());
        err.put("message", message);
        return err;
    }
}
