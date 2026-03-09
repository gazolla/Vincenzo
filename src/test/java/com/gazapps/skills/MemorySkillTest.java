package com.gazapps.skills;

import com.gazapps.config.AppConfig;
import com.gazapps.services.MemoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MemorySkill}.
 *
 * <p>Each test starts with a fresh {@link MemoryService} singleton backed by a
 * temporary file, then invokes the skill's public static methods and asserts
 * the returned {@code Map<String, String>} values.
 */
class MemorySkillTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        overrideFilePath(tempDir.resolve("memory-skill-test.json").toString());
        resetSingleton();
    }

    @AfterEach
    void tearDown() throws Exception {
        resetSingleton();
    }

    // ── saveMemory ────────────────────────────────────────────────────────────

    @Test
    void saveMemory_blankContent_returnsError() {
        Map<String, String> result = MemorySkill.saveMemory("   ", "tag", "note");
        assertEquals("error", result.get("status"));
        assertNotNull(result.get("message"));
    }

    @Test
    void saveMemory_nullContent_returnsError() {
        Map<String, String> result = MemorySkill.saveMemory(null, "tag", "note");
        assertEquals("error", result.get("status"));
    }

    @Test
    void saveMemory_validContent_returnsSuccess() {
        Map<String, String> result = MemorySkill.saveMemory("Some content", "tag1,tag2", "note");
        assertEquals("success", result.get("status"));
        assertNotNull(result.get("id"));
        assertEquals("Some content", result.get("content"));
    }

    @Test
    void saveMemory_idStartsWithMem() {
        Map<String, String> result = MemorySkill.saveMemory("Fact", "tag", "note");
        assertTrue(result.get("id").startsWith("mem-"));
    }

    @Test
    void saveMemory_nullCategory_defaultsToNote() {
        Map<String, String> result = MemorySkill.saveMemory("Content", "tag", null);
        assertEquals("success", result.get("status"));
        assertEquals("note", result.get("category"));
    }

    @Test
    void saveMemory_tagsCommaSeparated_parsedToLowercase() {
        Map<String, String> result = MemorySkill.saveMemory("Content", "Preference, Python", "preference");
        assertEquals("success", result.get("status"));
        // tags field in result is the List.toString() representation
        String tags = result.get("tags");
        assertTrue(tags.contains("preference"), "tags should contain 'preference' in lowercase");
        assertTrue(tags.contains("python"), "tags should contain 'python' in lowercase");
    }

    @Test
    void saveMemory_nullTags_succeeds() {
        Map<String, String> result = MemorySkill.saveMemory("Content", null, "note");
        assertEquals("success", result.get("status"));
    }

    @Test
    void saveMemory_maxLimitReached_returnsError() throws Exception {
        Field propsField = AppConfig.class.getDeclaredField("props");
        propsField.setAccessible(true);
        java.util.Properties props = (java.util.Properties) propsField.get(AppConfig.getInstance());
        String original = props.getProperty("memory.max.items", "500");
        props.setProperty("memory.max.items", "1");
        resetSingleton();

        try {
            MemorySkill.saveMemory("First entry", "tag", "note");
            Map<String, String> result = MemorySkill.saveMemory("Second — over limit", "tag", "note");
            assertEquals("error", result.get("status"));
            assertTrue(result.get("message").contains("limit"));
        } finally {
            props.setProperty("memory.max.items", original);
        }
    }

    // ── retrieveMemory ────────────────────────────────────────────────────────

    @Test
    void retrieveMemory_noEntries_returnsZeroCount() {
        Map<String, String> result = MemorySkill.retrieveMemory("anything", null);
        assertEquals("success", result.get("status"));
        assertEquals("0", result.get("match_count"));
    }

    @Test
    void retrieveMemory_byQuery_findsMatch() {
        MemorySkill.saveMemory("Python is great", "preference", "preference");
        Map<String, String> result = MemorySkill.retrieveMemory("python", null);
        assertEquals("success", result.get("status"));
        assertEquals("1", result.get("match_count"));
    }

    @Test
    void retrieveMemory_byTag_findsMatch() {
        MemorySkill.saveMemory("Dark mode preference", "preference,ui", "preference");
        Map<String, String> result = MemorySkill.retrieveMemory(null, "preference");
        assertEquals("1", result.get("match_count"));
    }

    @Test
    void retrieveMemory_noMatch_returnsZeroCount() {
        MemorySkill.saveMemory("Something about Java", "note", "note");
        Map<String, String> result = MemorySkill.retrieveMemory("nonexistent-xyz-abc", null);
        assertEquals("0", result.get("match_count"));
    }

    @Test
    void retrieveMemory_blankQueryAndTags_returnsAll() {
        MemorySkill.saveMemory("Entry A", "a", "note");
        MemorySkill.saveMemory("Entry B", "b", "note");
        Map<String, String> result = MemorySkill.retrieveMemory("", "");
        assertEquals("2", result.get("match_count"));
    }

    @Test
    void retrieveMemory_hasMemoriesKey() {
        Map<String, String> result = MemorySkill.retrieveMemory("", "");
        assertTrue(result.containsKey("memories"), "result must contain 'memories' key");
    }

    // ── listMemories ──────────────────────────────────────────────────────────

    @Test
    void listMemories_noEntries_returnsZeroCount() {
        Map<String, String> result = MemorySkill.listMemories(null);
        assertEquals("success", result.get("status"));
        assertEquals("0", result.get("entry_count"));
    }

    @Test
    void listMemories_noTag_returnsAll() {
        MemorySkill.saveMemory("A", "x", "note");
        MemorySkill.saveMemory("B", "y", "note");
        Map<String, String> result = MemorySkill.listMemories(null);
        assertEquals("2", result.get("entry_count"));
    }

    @Test
    void listMemories_withTag_filtersCorrectly() {
        MemorySkill.saveMemory("Preference entry", "preference", "preference");
        MemorySkill.saveMemory("Task entry", "task", "task");
        Map<String, String> result = MemorySkill.listMemories("preference");
        assertEquals("1", result.get("entry_count"));
    }

    @Test
    void listMemories_hasMemoriesKey() {
        Map<String, String> result = MemorySkill.listMemories(null);
        assertTrue(result.containsKey("memories"), "result must contain 'memories' key");
    }

    // ── deleteMemory ──────────────────────────────────────────────────────────

    @Test
    void deleteMemory_blankId_returnsError() {
        Map<String, String> result = MemorySkill.deleteMemory("   ");
        assertEquals("error", result.get("status"));
    }

    @Test
    void deleteMemory_nullId_returnsError() {
        Map<String, String> result = MemorySkill.deleteMemory(null);
        assertEquals("error", result.get("status"));
    }

    @Test
    void deleteMemory_unknownId_returnsError() {
        Map<String, String> result = MemorySkill.deleteMemory("mem-doesnotexist");
        assertEquals("error", result.get("status"));
        assertTrue(result.get("message").contains("not found"));
    }

    @Test
    void deleteMemory_existingId_returnsSuccess() {
        Map<String, String> saved = MemorySkill.saveMemory("To be deleted", "tag", "note");
        String id = saved.get("id");

        Map<String, String> result = MemorySkill.deleteMemory(id);
        assertEquals("success", result.get("status"));
        assertEquals(id, result.get("id"));
    }

    @Test
    void deleteMemory_existingId_entryRemovedFromList() {
        Map<String, String> saved = MemorySkill.saveMemory("Gone", "tag", "note");
        String id = saved.get("id");
        MemorySkill.deleteMemory(id);

        Map<String, String> list = MemorySkill.listMemories(null);
        assertEquals("0", list.get("entry_count"));
    }

    // ── updateMemory ──────────────────────────────────────────────────────────

    @Test
    void updateMemory_blankId_returnsError() {
        Map<String, String> result = MemorySkill.updateMemory("   ", "new content", null);
        assertEquals("error", result.get("status"));
    }

    @Test
    void updateMemory_nullId_returnsError() {
        Map<String, String> result = MemorySkill.updateMemory(null, "new content", null);
        assertEquals("error", result.get("status"));
    }

    @Test
    void updateMemory_unknownId_returnsError() {
        Map<String, String> result = MemorySkill.updateMemory("mem-unknown", "text", null);
        assertEquals("error", result.get("status"));
        assertTrue(result.get("message").contains("not found"));
    }

    @Test
    void updateMemory_validId_contentUpdated() {
        Map<String, String> saved = MemorySkill.saveMemory("Original", "tag", "note");
        String id = saved.get("id");

        Map<String, String> result = MemorySkill.updateMemory(id, "Updated content", null);
        assertEquals("success", result.get("status"));
        assertEquals("Updated content", result.get("content"));
    }

    @Test
    void updateMemory_blankContent_keepsOriginalContent() {
        Map<String, String> saved = MemorySkill.saveMemory("Keep this", "tag", "note");
        String id = saved.get("id");

        Map<String, String> result = MemorySkill.updateMemory(id, "   ", "new-tag");
        assertEquals("success", result.get("status"));
        assertEquals("Keep this", result.get("content"));
    }

    @Test
    void updateMemory_blankTags_keepsOriginalTags() {
        Map<String, String> saved = MemorySkill.saveMemory("Content", "original-tag", "note");
        String id = saved.get("id");

        Map<String, String> result = MemorySkill.updateMemory(id, "New content", "   ");
        assertEquals("success", result.get("status"));
        assertTrue(result.get("tags").contains("original-tag"));
    }

    @Test
    void updateMemory_returnsSuccessWithUpdatedFields() {
        Map<String, String> saved = MemorySkill.saveMemory("Content", "old", "note");
        String id = saved.get("id");

        Map<String, String> result = MemorySkill.updateMemory(id, "New content", "new-tag");
        assertEquals("success", result.get("status"));
        assertEquals(id, result.get("id"));
        assertNotNull(result.get("message"));
    }

    // ── parseTags helper ──────────────────────────────────────────────────────

    @Test
    void parseTags_nullInput_returnsEmptyList() {
        List<String> tags = MemorySkill.parseTags(null);
        assertNotNull(tags);
        assertTrue(tags.isEmpty());
    }

    @Test
    void parseTags_commaSeparated_trimmedAndLowercased() {
        List<String> tags = MemorySkill.parseTags("Preference, Python , WORK");
        assertEquals(3, tags.size());
        assertEquals("preference", tags.get(0));
        assertEquals("python", tags.get(1));
        assertEquals("work", tags.get(2));
    }

    @Test
    void parseTags_emptySegments_filtered() {
        List<String> tags = MemorySkill.parseTags(",,,tag,,");
        assertEquals(1, tags.size());
        assertEquals("tag", tags.get(0));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void resetSingleton() throws Exception {
        Field field = MemoryService.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, null);
    }

    private static void overrideFilePath(String path) throws Exception {
        Field propsField = AppConfig.class.getDeclaredField("props");
        propsField.setAccessible(true);
        java.util.Properties props = (java.util.Properties) propsField.get(AppConfig.getInstance());
        props.setProperty("memory.storage.file", path);
    }
}
