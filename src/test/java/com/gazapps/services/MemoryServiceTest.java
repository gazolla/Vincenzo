package com.gazapps.services;

import com.gazapps.config.AppConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MemoryService}.
 *
 * <p>Tests use reflection to reset the singleton between test methods,
 * ensuring each test starts with a clean state. A temporary directory is
 * used for the JSON storage file to avoid touching real data.
 */
class MemoryServiceTest {

    @TempDir
    Path tempDir;

    private String originalFileProp;

    @BeforeEach
    void setUp() throws Exception {
        originalFileProp = getFileProp();
        overrideFilePath(tempDir.resolve("test-memory-store.json").toString());
        resetSingleton();
    }

    @AfterEach
    void tearDown() throws Exception {
        resetSingleton();
        overrideFilePath(originalFileProp != null ? originalFileProp : "work/memory-store.json");
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_validContent_returnsId() {
        String id = MemoryService.getInstance().save("Test content", List.of("tag1"), "note");
        assertNotNull(id);
        assertFalse(id.isBlank());
    }

    @Test
    void save_idStartsWithMem() {
        String id = MemoryService.getInstance().save("Some fact", List.of(), "note");
        assertTrue(id.startsWith("mem-"), "id should start with 'mem-'");
    }

    @Test
    void save_entryAppearsInList() {
        MemoryService svc = MemoryService.getInstance();
        svc.save("Prefers Python", List.of("preference", "python"), "preference");

        List<MemoryService.MemoryEntry> all = svc.list(null);
        assertEquals(1, all.size());
        assertEquals("Prefers Python", all.get(0).content());
        assertEquals("preference", all.get(0).category());
        assertTrue(all.get(0).tags().contains("preference"));
        assertTrue(all.get(0).tags().contains("python"));
    }

    @Test
    void save_multipleEntries_allInList() {
        MemoryService svc = MemoryService.getInstance();
        svc.save("Entry one", List.of("a"), "note");
        svc.save("Entry two", List.of("b"), "note");
        svc.save("Entry three", List.of("c"), "note");

        assertEquals(3, svc.list(null).size());
    }

    @Test
    void save_nullTags_treatedAsEmpty() {
        String id = MemoryService.getInstance().save("No tags here", null, "note");
        assertNotNull(id);
        MemoryService.MemoryEntry entry = MemoryService.getInstance().list(null).get(0);
        assertNotNull(entry.tags());
        assertTrue(entry.tags().isEmpty());
    }

    @Test
    void save_nullCategory_defaultsToNote() {
        MemoryService.getInstance().save("Content only", List.of(), null);
        MemoryService.MemoryEntry entry = MemoryService.getInstance().list(null).get(0);
        assertEquals("note", entry.category());
    }

    @Test
    void save_maxItemsReached_returnsNull() throws Exception {
        // Override limit to 2 for this test
        Field propsField = AppConfig.class.getDeclaredField("PROPS");
        propsField.setAccessible(true);
        java.util.Properties props = (java.util.Properties) propsField.get(null);
        String original = props.getProperty("memory.max.items", "500");
        props.setProperty("memory.max.items", "2");

        // Reload singleton so it picks up the new MEMORY_MAX_ITEMS at call time
        resetSingleton();
        MemoryService svc = MemoryService.getInstance();

        try {
            assertNotNull(svc.save("First", List.of(), "note"));
            assertNotNull(svc.save("Second", List.of(), "note"));
            assertNull(svc.save("Third — should be rejected", List.of(), "note"),
                    "save() should return null when max items is reached");
        } finally {
            props.setProperty("memory.max.items", original);
        }
    }

    // ── search ────────────────────────────────────────────────────────────────

    @Test
    void search_bySubstring_caseInsensitive_findsMatch() {
        MemoryService svc = MemoryService.getInstance();
        svc.save("Prefers Python over Java", List.of("preference"), "preference");

        List<MemoryService.MemoryEntry> results = svc.search("PYTHON", null);
        assertEquals(1, results.size());
        assertTrue(results.get(0).content().contains("Python"));
    }

    @Test
    void search_byTag_findsMatch() {
        MemoryService svc = MemoryService.getInstance();
        svc.save("Dark mode preference", List.of("preference", "ui"), "preference");
        svc.save("Project deadline March 15", List.of("task", "work"), "task");

        List<MemoryService.MemoryEntry> results = svc.search(null, List.of("preference"));
        assertEquals(1, results.size());
        assertEquals("Dark mode preference", results.get(0).content());
    }

    @Test
    void search_byQueryAndTag_bothMustMatch() {
        MemoryService svc = MemoryService.getInstance();
        svc.save("Python is my favorite language", List.of("preference"), "preference");
        svc.save("Python script for work", List.of("task"), "task");

        // query=python AND tag=preference → only the first
        List<MemoryService.MemoryEntry> results = svc.search("python", List.of("preference"));
        assertEquals(1, results.size());
        assertEquals("Python is my favorite language", results.get(0).content());
    }

    @Test
    void search_noMatch_returnsEmptyList() {
        MemoryService svc = MemoryService.getInstance();
        svc.save("Something about Java", List.of("note"), "note");

        assertTrue(svc.search("nonexistent-xyz", null).isEmpty());
    }

    @Test
    void search_nullQueryAndNullTags_returnsAll() {
        MemoryService svc = MemoryService.getInstance();
        svc.save("Entry A", List.of("a"), "note");
        svc.save("Entry B", List.of("b"), "note");

        assertEquals(2, svc.search(null, null).size());
    }

    @Test
    void search_tagMatchesAnyProvided() {
        MemoryService svc = MemoryService.getInstance();
        svc.save("Work project context", List.of("work", "project"), "task");

        // Entry has tag "work"; querying for ["work", "python"] should still match
        List<MemoryService.MemoryEntry> results = svc.search(null, List.of("work", "python"));
        assertEquals(1, results.size());
    }

    @Test
    void search_sortedNewestFirst() throws InterruptedException {
        MemoryService svc = MemoryService.getInstance();
        svc.save("Older entry", List.of(), "note");
        Thread.sleep(5); // ensure different timestamps
        svc.save("Newer entry", List.of(), "note");

        List<MemoryService.MemoryEntry> results = svc.search(null, null);
        assertEquals("Newer entry", results.get(0).content());
    }

    // ── list ──────────────────────────────────────────────────────────────────

    @Test
    void list_noTag_returnsAll() {
        MemoryService svc = MemoryService.getInstance();
        svc.save("A", List.of("x"), "note");
        svc.save("B", List.of("y"), "note");

        assertEquals(2, svc.list(null).size());
    }

    @Test
    void list_withTag_filtersCorrectly() {
        MemoryService svc = MemoryService.getInstance();
        svc.save("Preference entry", List.of("preference"), "preference");
        svc.save("Task entry", List.of("task"), "task");

        List<MemoryService.MemoryEntry> filtered = svc.list("preference");
        assertEquals(1, filtered.size());
        assertEquals("Preference entry", filtered.get(0).content());
    }

    @Test
    void list_freshStart_returnsEmpty() {
        assertTrue(MemoryService.getInstance().list(null).isEmpty());
    }

    @Test
    void list_blankTag_returnsAll() {
        MemoryService svc = MemoryService.getInstance();
        svc.save("A", List.of("x"), "note");
        svc.save("B", List.of("y"), "note");

        assertEquals(2, svc.list("").size());
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_existingId_returnsTrue() {
        MemoryService svc = MemoryService.getInstance();
        String id = svc.save("To be deleted", List.of(), "note");
        assertTrue(svc.delete(id));
    }

    @Test
    void delete_unknownId_returnsFalse() {
        assertFalse(MemoryService.getInstance().delete("mem-nonexistent"));
    }

    @Test
    void delete_afterDelete_notInList() {
        MemoryService svc = MemoryService.getInstance();
        String id = svc.save("Gone", List.of(), "note");
        svc.delete(id);

        assertTrue(svc.list(null).isEmpty());
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_existingId_contentChanged() {
        MemoryService svc = MemoryService.getInstance();
        String id = svc.save("Original content", List.of("tag"), "note");

        MemoryService.MemoryEntry updated = svc.update(id, "Updated content", null);
        assertNotNull(updated);
        assertEquals("Updated content", updated.content());
    }

    @Test
    void update_existingId_tagsChanged() {
        MemoryService svc = MemoryService.getInstance();
        String id = svc.save("Content", List.of("old-tag"), "note");

        MemoryService.MemoryEntry updated = svc.update(id, null, List.of("new-tag"));
        assertNotNull(updated);
        assertTrue(updated.tags().contains("new-tag"));
        assertFalse(updated.tags().contains("old-tag"));
    }

    @Test
    void update_nullContent_keepsExistingContent() {
        MemoryService svc = MemoryService.getInstance();
        String id = svc.save("Keep this content", List.of(), "note");

        MemoryService.MemoryEntry updated = svc.update(id, null, List.of("new-tag"));
        assertEquals("Keep this content", updated.content());
    }

    @Test
    void update_nullTags_keepsExistingTags() {
        MemoryService svc = MemoryService.getInstance();
        String id = svc.save("Content", List.of("keep-tag"), "note");

        MemoryService.MemoryEntry updated = svc.update(id, "New content", null);
        assertTrue(updated.tags().contains("keep-tag"));
    }

    @Test
    void update_unknownId_returnsNull() {
        assertNull(MemoryService.getInstance().update("mem-unknown", "text", null));
    }

    @Test
    void update_updatedAtIsRefreshed() throws InterruptedException {
        MemoryService svc = MemoryService.getInstance();
        String id = svc.save("Original", List.of(), "note");
        MemoryService.MemoryEntry original = svc.list(null).get(0);

        Thread.sleep(5);
        MemoryService.MemoryEntry updated = svc.update(id, "Changed", null);

        assertTrue(updated.updatedAt() >= original.updatedAt(),
                "updatedAt should be refreshed after update");
        assertEquals(original.createdAt(), updated.createdAt(),
                "createdAt should not change");
    }

    // ── persistence ───────────────────────────────────────────────────────────

    @Test
    void saveAndLoad_roundTrip_preservesAllFields() throws Exception {
        Path storageFile = tempDir.resolve("roundtrip-memory.json");
        overrideFilePath(storageFile.toString());
        resetSingleton();

        MemoryService svc = MemoryService.getInstance();
        String id = svc.save("Persistent content", List.of("persist", "test"), "research");
        MemoryService.MemoryEntry original = svc.list(null).get(0);

        assertTrue(Files.exists(storageFile), "JSON file should be created after save");

        // Reset and reload
        resetSingleton();
        overrideFilePath(storageFile.toString());
        MemoryService loaded = MemoryService.getInstance();

        List<MemoryService.MemoryEntry> entries = loaded.list(null);
        assertEquals(1, entries.size());
        MemoryService.MemoryEntry reloaded = entries.get(0);

        assertEquals(original.id(),        reloaded.id());
        assertEquals(original.content(),   reloaded.content());
        assertEquals(original.category(),  reloaded.category());
        assertEquals(original.createdAt(), reloaded.createdAt());
        assertEquals(original.updatedAt(), reloaded.updatedAt());
        assertEquals(original.tags(),      reloaded.tags());
    }

    @Test
    void saveAndLoad_missingFile_startsEmpty() throws Exception {
        Path nonExistent = tempDir.resolve("does-not-exist.json");
        overrideFilePath(nonExistent.toString());
        resetSingleton();

        assertTrue(MemoryService.getInstance().list(null).isEmpty());
    }

    @Test
    void saveAndLoad_afterDelete_entryAbsent() throws Exception {
        Path storageFile = tempDir.resolve("delete-persist.json");
        overrideFilePath(storageFile.toString());
        resetSingleton();

        MemoryService svc = MemoryService.getInstance();
        String id = svc.save("Will be deleted", List.of(), "note");
        svc.delete(id);

        // Reset and reload
        resetSingleton();
        overrideFilePath(storageFile.toString());

        assertTrue(MemoryService.getInstance().list(null).isEmpty(),
                "Deleted entry should not appear after reload");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Reset the MemoryService singleton via reflection.
     * MemoryService has no thread pool, so a simple null-set suffices.
     */
    private static void resetSingleton() throws Exception {
        Field field = MemoryService.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, null);
    }

    private static void overrideFilePath(String path) throws Exception {
        Field propsField = AppConfig.class.getDeclaredField("PROPS");
        propsField.setAccessible(true);
        java.util.Properties props = (java.util.Properties) propsField.get(null);
        props.setProperty("memory.storage.file", path);
    }

    private static String getFileProp() throws Exception {
        Field propsField = AppConfig.class.getDeclaredField("PROPS");
        propsField.setAccessible(true);
        java.util.Properties props = (java.util.Properties) propsField.get(null);
        return props.getProperty("memory.storage.file", "work/memory-store.json");
    }
}
