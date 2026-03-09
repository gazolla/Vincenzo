package com.gazapps.logging;

import com.gazapps.config.AppConfig;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Centralized logging service.
 *
 * - Writes structured logs to  logs/session-<timestamp>.log
 * - Each log line: [TIMESTAMP] [LEVEL] [COMPONENT] message
 * - Sections delimited by banners for easy reading
 * - Thread-safe (AtomicLong counter + synchronized file write)
 * - Log rotation: opens a new file when the current one exceeds {@code log.max.size.kb}
 * - Log retention: deletes oldest files when the count exceeds {@code log.max.files}
 */
public class LogService {

    // ── Levels ────────────────────────────────────────────────────────────────
    public enum Level { DEBUG, INFO, WARN, ERROR }

    // ── Singleton ─────────────────────────────────────────────────────────────
    private static volatile LogService instance;

    public static LogService getInstance() {
        if (instance == null) {
            synchronized (LogService.class) {
                if (instance == null) instance = new LogService();
            }
        }
        return instance;
    }

    // ── State ─────────────────────────────────────────────────────────────────
    private Path logFile;
    private final Path logsDir;
    private final DateTimeFormatter ts     = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private final DateTimeFormatter fileTs = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private final AtomicLong eventCounter  = new AtomicLong(0);
    private Level minLevel = Level.DEBUG;

    // ── Constructor ───────────────────────────────────────────────────────────
    private LogService() {
        logsDir = Paths.get("logs");
        try {
            Files.createDirectories(logsDir);
        } catch (IOException e) {
            System.err.println("[LogService] Could not create logs dir: " + e.getMessage());
        }
        logFile = newLogFile();
        enforceRetention();
        writeRaw(banner("SESSION START  " + LocalDateTime.now().format(ts)));
        writeRaw(line(Level.INFO, "LogService", "Log file: " + logFile.toAbsolutePath()));
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void setMinLevel(Level level) { this.minLevel = level; }

    public Path getLogFile() { return logFile; }

    // Generic log
    public void log(Level level, String component, String message) {
        if (level.ordinal() < minLevel.ordinal()) return;
        String entry = line(level, component, message);
        writeRaw(entry);
    }

    public void debug(String component, String message) { log(Level.DEBUG, component, message); }
    public void info (String component, String message) { log(Level.INFO,  component, message); }
    public void warn (String component, String message) { log(Level.WARN,  component, message); }
    public void error(String component, String message) { log(Level.ERROR, component, message); }

    // Log an exception with full stack trace
    public void error(String component, String message, Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        log(Level.ERROR, component, message + "\n" + sw);
    }

    // Log a section banner (═══ TITLE ═══)
    public void section(String title) {
        writeRaw(banner(title));
    }

    // Log a sub-section divider (─── title ───)
    public void divider(String title) {
        writeRaw(dividerLine(title));
    }

    // Log a Map<String,String> tool result with truncation
    public void toolResult(String toolName, Map<String, String> result) {
        long n = eventCounter.incrementAndGet();
        StringBuilder sb = new StringBuilder();
        sb.append(line(Level.INFO, toolName, "── Tool result #" + n + " ──"));
        for (Map.Entry<String, String> e : result.entrySet()) {
            String val = e.getValue();
            if (val != null && val.length() > 1000) {
                val = val.substring(0, 1000) + "... [+" + (e.getValue().length() - 1000) + " chars truncated]";
            }
            // indent each value line
            String indented = val == null ? "null" : val.replace("\n", "\n            ");
            sb.append(String.format("            %-14s: %s%n", e.getKey(), indented));
        }
        writeRaw(sb.toString());
    }

    // Log a timing measurement
    public void timing(String component, String operation, long elapsedMs) {
        log(Level.INFO, component, String.format("⏱  %s completed in %,d ms", operation, elapsedMs));
    }

    // Log the user message
    public void userMessage(String text) {
        divider("USER INPUT");
        log(Level.INFO, "ChatInterface", "User  ▶  " + sanitize(text));
    }

    // Log the agent response
    public void agentResponse(String text) {
        divider("AGENT RESPONSE");
        log(Level.INFO, "SearchAgent", "Agent ◀  " + sanitize(text));
    }

    // Log an ADK event
    public void adkEvent(String eventType, boolean isFinal, String content) {
        String flag = isFinal ? "[FINAL]" : "[intermediate]";
        String preview = content != null && content.length() > 300
                ? content.substring(0, 300) + "..."
                : content;
        log(Level.DEBUG, "ADK-Event", flag + " type=" + eventType + "  content=" + preview);
    }

    // Mark session end
    public void sessionEnd() {
        writeRaw(banner("SESSION END  " + LocalDateTime.now().format(ts)));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Path newLogFile() {
        String fileName = "session-" + LocalDateTime.now().format(fileTs) + ".log";
        return logsDir.resolve(fileName);
    }

    /**
     * Rotates the current log file: writes a continuation marker to the current file,
     * then switches to a new file and applies retention. Called inside a synchronized block.
     */
    private void rotate() {
        writeRaw(banner("LOG ROTATED — continued in next file"));
        logFile = newLogFile();
        enforceRetention();
        writeRaw(banner("SESSION CONTINUED  " + LocalDateTime.now().format(ts)));
        writeRaw(line(Level.INFO, "LogService", "Log file: " + logFile.toAbsolutePath()));
    }

    /**
     * Deletes the oldest session log files so that at most {@code LOG_MAX_FILES}
     * files remain in the logs directory (counting the file about to be created).
     * Silently skips files that cannot be deleted.
     */
    private void enforceRetention() {
        int maxFiles = AppConfig.getInstance().LOG_MAX_FILES;
        if (maxFiles <= 0) return;
        try (Stream<Path> stream = Files.list(logsDir)) {
            List<Path> files = stream
                    .filter(p -> p.getFileName().toString().startsWith("session-")
                              && p.getFileName().toString().endsWith(".log"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();

            // +1 because logFile was just assigned but not yet written (counts as one slot)
            int toDelete = files.size() - (maxFiles - 1);
            for (int i = 0; i < toDelete; i++) {
                Path old = files.get(i);
                try {
                    Files.deleteIfExists(old);
                    System.err.println("[LogService] Deleted old log: " + old.getFileName());
                } catch (IOException e) {
                    System.err.println("[LogService] Could not delete " + old.getFileName()
                            + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[LogService] Could not list logs dir: " + e.getMessage());
        }
    }

    private String line(Level level, String component, String message) {
        String now = LocalDateTime.now().format(ts);
        String lvl = String.format("%-5s", level.name());
        String comp = String.format("%-18s", component);
        return String.format("[%s] [%s] [%s] %s%n", now, lvl, comp, message);
    }

    private String banner(String title) {
        String bar = "═".repeat(70);
        return "\n" + bar + "\n  " + title + "\n" + bar + "\n";
    }

    private String dividerLine(String title) {
        int pad = Math.max(0, 66 - title.length());
        String right = "─".repeat(pad / 2);
        String left  = "─".repeat(pad - pad / 2);
        return "\n  " + left + " " + title + " " + right + "\n";
    }

    private String sanitize(String text) {
        if (text == null) return "null";
        // Replace non-printable control chars (keep \n, \t)
        return text.replaceAll("[\\p{Cntrl}&&[^\n\t]]", "?");
    }

    private synchronized void writeRaw(String text) {
        try {
            // Check size limit before writing (0 = disabled)
            int maxKb = AppConfig.getInstance().LOG_MAX_SIZE_KB;
            if (maxKb > 0 && Files.exists(logFile)) {
                long currentBytes = Files.size(logFile);
                if (currentBytes >= (long) maxKb * 1024) {
                    rotate();
                }
            }
            Files.writeString(logFile, text,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[LogService] Write error: " + e.getMessage());
        }
    }
}
