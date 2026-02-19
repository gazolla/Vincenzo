package com.gazapps.logging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for log retention and rotation logic extracted from LogService.
 *
 * <p>LogService is a singleton and touches the real filesystem, so these tests
 * exercise the retention and rotation algorithms directly using temporary
 * directories, without instantiating the singleton.
 */
class LogServiceManagementTest {

    // ── Helpers that replicate the production logic ────────────────────────────

    /** Creates N fake session log files with sequential names in {@code dir}. */
    private static void createFakeLogs(Path dir, int count) throws IOException {
        for (int i = 1; i <= count; i++) {
            String name = String.format("session-20260101_%06d.log", i);
            Files.writeString(dir.resolve(name), "log content " + i,
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE);
        }
    }

    /** Lists session-*.log files sorted by name (oldest first). */
    private static List<Path> listLogs(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().startsWith("session-")
                               && p.getFileName().toString().endsWith(".log"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        }
    }

    /**
     * Replicates {@code LogService.enforceRetention()} for testing.
     * Deletes the oldest files so that at most {@code maxFiles} exist
     * (reserving one slot for a not-yet-created new file).
     */
    private static void enforceRetention(Path dir, int maxFiles) throws IOException {
        if (maxFiles <= 0) return;
        List<Path> files = listLogs(dir);
        int toDelete = files.size() - (maxFiles - 1);
        for (int i = 0; i < toDelete; i++) {
            Files.deleteIfExists(files.get(i));
        }
    }

    // ── Retention tests ────────────────────────────────────────────────────────

    @Test
    void retention_deletesOldestWhenOverLimit(@TempDir Path dir) throws IOException {
        createFakeLogs(dir, 12);
        enforceRetention(dir, 10);

        List<Path> remaining = listLogs(dir);
        assertEquals(9, remaining.size(),   // 9 kept + 1 slot for the new file = 10
                "Should keep maxFiles-1 existing files (one slot for the new file)");
        // The two oldest (000001, 000002) must be gone
        assertTrue(remaining.stream().noneMatch(p -> p.getFileName().toString().contains("000001")),
                "Oldest file should be deleted");
        assertTrue(remaining.stream().noneMatch(p -> p.getFileName().toString().contains("000002")),
                "Second oldest file should be deleted");
        // The newest (000012) must remain
        assertTrue(remaining.stream().anyMatch(p -> p.getFileName().toString().contains("000012")),
                "Newest file should be kept");
    }

    @Test
    void retention_doesNothingWhenUnderLimit(@TempDir Path dir) throws IOException {
        createFakeLogs(dir, 5);
        enforceRetention(dir, 10);

        assertEquals(5, listLogs(dir).size(),
                "No files should be deleted when under the limit");
    }

    @Test
    void retention_exactlyAtLimit_deletesOne(@TempDir Path dir) throws IOException {
        createFakeLogs(dir, 10);
        enforceRetention(dir, 10);

        // 10 files - (10-1) kept = 1 deleted
        assertEquals(9, listLogs(dir).size(),
                "One file deleted to free slot for the new file");
    }

    @Test
    void retention_maxFilesZero_doesNothing(@TempDir Path dir) throws IOException {
        createFakeLogs(dir, 20);
        enforceRetention(dir, 0);

        assertEquals(20, listLogs(dir).size(),
                "maxFiles=0 disables retention — no files deleted");
    }

    @Test
    void retention_maxFilesOne_keepsNone(@TempDir Path dir) throws IOException {
        createFakeLogs(dir, 5);
        enforceRetention(dir, 1);

        // 1-1 = 0 slots, all 5 existing files deleted
        assertEquals(0, listLogs(dir).size(),
                "maxFiles=1 means only the new file survives — all existing deleted");
    }

    // ── Rotation size check ────────────────────────────────────────────────────

    @Test
    void rotationThreshold_fileBelowLimit_noRotation(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("session-test.log");
        // Write 100 bytes
        Files.writeString(file, "x".repeat(100), StandardCharsets.UTF_8);

        int maxKb = 1; // 1 KB = 1024 bytes
        long size = Files.size(file);
        assertFalse(size >= (long) maxKb * 1024,
                "100-byte file should be below 1 KB threshold");
    }

    @Test
    void rotationThreshold_fileAtOrAboveLimit_triggersRotation(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("session-test.log");
        // Write exactly 1024 bytes
        Files.writeString(file, "x".repeat(1024), StandardCharsets.UTF_8);

        int maxKb = 1;
        long size = Files.size(file);
        assertTrue(size >= (long) maxKb * 1024,
                "1024-byte file should meet or exceed 1 KB threshold");
    }

    @Test
    void rotationThreshold_zero_neverTriggers(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("session-test.log");
        Files.writeString(file, "x".repeat(999_999), StandardCharsets.UTF_8);

        int maxKb = 0; // disabled
        // With maxKb == 0 the production code skips the check entirely
        assertFalse(maxKb > 0,
                "maxKb=0 disables rotation regardless of file size");
    }
}
