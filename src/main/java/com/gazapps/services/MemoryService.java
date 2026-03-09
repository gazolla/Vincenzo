package com.gazapps.services;

import com.gazapps.config.AppConfig;
import com.gazapps.logging.LogService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Singleton service that stores and retrieves persistent memory entries.
 *
 * <p>Entries are kept in a {@link ConcurrentHashMap} and persisted to
 * {@code work/memory-store.json} (configurable via {@code memory.storage.file})
 * using Gson. The file is written on every mutating operation.</p>
 *
 * <p>Search supports case-insensitive substring matching on content and
 * exact matching on any of the provided tags (OR within the tag list, AND
 * between query and tag filters when both are supplied).</p>
 */
public class MemoryService {

    private static final LogService LOG = LogService.getInstance();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static volatile MemoryService instance;

    public static MemoryService getInstance() {
        if (instance == null) {
            synchronized (MemoryService.class) {
                if (instance == null) {
                    instance = new MemoryService();
                }
            }
        }
        return instance;
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private final ConcurrentHashMap<String, MemoryEntry> entries = new ConcurrentHashMap<>();

    // ── MemoryEntry ───────────────────────────────────────────────────────────

    /**
     * Immutable snapshot of a single memory entry.
     * Gson 2.10+ serializes Java records correctly via field reflection.
     */
    public record MemoryEntry(
            String id,          // "mem-" + UUID 8-char prefix  e.g. "mem-a1b2c3d4"
            String content,     // free-text body
            List<String> tags,  // lowercase tags e.g. ["preference", "python"]
            String category,    // "preference" | "note" | "research" | "task"
            long createdAt,     // epoch millis
            long updatedAt      // epoch millis; equals createdAt on creation
    ) {}

    // ── Constructor ───────────────────────────────────────────────────────────

    private MemoryService() {
        LOG.info("MemoryService", "Initializing...");
        ensureWorkDir();
        loadEntries();
        LOG.info("MemoryService", "Ready — " + entries.size() + " entr(y/ies) loaded");
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Save a new memory entry.
     *
     * @return an {@link Optional} containing the new id (e.g. {@code "mem-a1b2c3d4"}),
     *         or {@link Optional#empty()} if the {@code memory.max.items} limit has been reached
     */
    public Optional<String> save(String content, List<String> tags, String category) {
        int maxItems = AppConfig.getInstance().memoryMaxItems();
        if (entries.size() >= maxItems) {
            LOG.warn("MemoryService", "Max items limit reached (" + maxItems + ")");
            return Optional.empty();
        }
        long now = System.currentTimeMillis();
        String id = "mem-" + UUID.randomUUID().toString().substring(0, 8);
        List<String> safeTags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
        String safeCategory = (category != null && !category.isBlank()) ? category.trim() : "note";
        MemoryEntry entry = new MemoryEntry(id, content, safeTags, safeCategory, now, now);
        entries.put(id, entry);
        saveData();
        LOG.info("MemoryService", "Saved " + id + " tags=" + safeTags + " category=" + safeCategory);
        return Optional.of(id);
    }

    /**
     * Search entries by text substring (case-insensitive) and/or tag list.
     * Both filters are optional; omitting both returns all entries.
     * When both are supplied, both must match (AND semantics).
     * Within the tag list, an entry matches if it contains ANY of the provided tags (OR semantics).
     *
     * @return list of matching entries, sorted newest-first
     */
    public List<MemoryEntry> search(String query, List<String> tags) {
        String q = (query != null) ? query.toLowerCase(Locale.ROOT) : null;
        boolean hasQuery = q != null && !q.isBlank();
        boolean hasTags  = tags != null && !tags.isEmpty();

        return entries.values().stream()
                .filter(e -> {
                    boolean matchesQuery = !hasQuery
                            || e.content().toLowerCase(Locale.ROOT).contains(q);
                    boolean matchesTags  = !hasTags
                            || tags.stream().anyMatch(t -> e.tags().contains(t));
                    return matchesQuery && matchesTags;
                })
                .sorted(Comparator.comparingLong(MemoryEntry::updatedAt).reversed())
                .collect(Collectors.toList());
    }

    /**
     * List all entries, optionally filtered by a single tag (exact match).
     *
     * @param tag single tag to filter by, or {@code null}/blank to return all
     * @return entries sorted newest-first
     */
    public List<MemoryEntry> list(String tag) {
        boolean filterByTag = tag != null && !tag.isBlank();
        return entries.values().stream()
                .filter(e -> !filterByTag || e.tags().contains(tag))
                .sorted(Comparator.comparingLong(MemoryEntry::updatedAt).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Delete an entry by id.
     *
     * @return {@code true} if found and removed; {@code false} if not found
     */
    public boolean delete(String id) {
        MemoryEntry removed = entries.remove(id);
        if (removed == null) {
            LOG.warn("MemoryService", "Delete requested for unknown id: " + id);
            return false;
        }
        saveData();
        LOG.info("MemoryService", "Deleted " + id);
        return true;
    }

    /**
     * Update content and/or tags of an existing entry.
     * Pass {@code null} for any field to keep the existing value.
     *
     * @return an {@link Optional} containing the updated entry,
     *         or {@link Optional#empty()} if the id was not found
     */
    public Optional<MemoryEntry> update(String id, String newContent, List<String> newTags) {
        MemoryEntry existing = entries.get(id);
        if (existing == null) {
            LOG.warn("MemoryService", "Update requested for unknown id: " + id);
            return Optional.empty();
        }
        String content = (newContent != null && !newContent.isBlank())
                ? newContent : existing.content();
        List<String> tags = (newTags != null)
                ? new ArrayList<>(newTags) : existing.tags();
        long now = System.currentTimeMillis();
        MemoryEntry updated = new MemoryEntry(
                id, content, tags, existing.category(), existing.createdAt(), now);
        entries.put(id, updated);
        saveData();
        LOG.info("MemoryService", "Updated " + id);
        return Optional.of(updated);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void ensureWorkDir() {
        try {
            Files.createDirectories(Paths.get("work"));
        } catch (IOException e) {
            LOG.warn("MemoryService", "Could not create work directory: " + e.getMessage());
        }
    }

    /** Returns the storage file path — reads dynamically so tests can override via PROPS. */
    private static Path dataFilePath() {
        return Paths.get(AppConfig.getInstance().memoryStorageFile());
    }

    private void loadEntries() {
        Path path = dataFilePath();
        if (!Files.exists(path)) {
            LOG.info("MemoryService", "No memory file found at " + path + " — starting empty");
            return;
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            Type listType = new TypeToken<List<MemoryEntry>>() {}.getType();
            List<MemoryEntry> loaded = GSON.fromJson(json, listType);
            if (loaded == null) return;
            for (MemoryEntry entry : loaded) {
                entries.put(entry.id(), entry);
            }
            LOG.info("MemoryService", "Loaded " + loaded.size() + " entr(y/ies)");
        } catch (Exception e) {
            LOG.error("MemoryService", "Failed to load entries from " + path, e);
        }
    }

    private synchronized void saveData() {
        Path path = dataFilePath();
        try {
            String json = GSON.toJson(new ArrayList<>(entries.values()));
            Files.writeString(path, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            LOG.debug("MemoryService", "Persisted " + entries.size() + " entr(y/ies) to " + path);
        } catch (IOException e) {
            LOG.error("MemoryService", "Failed to save to " + path, e);
        }
    }
}
