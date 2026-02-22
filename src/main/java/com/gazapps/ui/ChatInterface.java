package com.gazapps.ui;

import com.gazapps.agent.SearchAgent;
import com.gazapps.logging.LogService;
import com.gazapps.util.RetryUtils;

import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Command-line chat interface with ANSI color support and Markdown rendering.
 *
 * <p>
 * Color scheme:
 * <ul>
 * <li>User prompt ("Você:") — bold cyan</li>
 * <li>Assistant label ("Vincenzo:") — bold green</li>
 * <li>Bold Markdown (**text**) — ANSI bold white</li>
 * <li>Bullet points (* item / - item) — cyan bullet •</li>
 * <li>Links ([text](url)) — text + dim url</li>
 * <li>Inline code (`code`) — dim</li>
 * <li>Fenced code blocks (``` ... ```) — dim box</li>
 * <li>Headers (#, ##, ###) — bold / bold-italic</li>
 * <li>Horizontal rules (---) — thin separator</li>
 * </ul>
 *
 * <p>
 * Every user input, agent response, error and timing is logged.
 */
public class ChatInterface {

    // ── ANSI escape codes ─────────────────────────────────────────────────────

    private static final String RESET = "\033[0m";
    private static final String BOLD = "\033[1m";
    private static final String DIM = "\033[2m";
    private static final String ITALIC = "\033[3m";

    private static final String CYAN = "\033[36m";
    private static final String GREEN = "\033[32m";
    private static final String DARK_GREEN = "\033[38;5;28m"; // 256-color dark green for response text
    private static final String YELLOW = "\033[33m";
    private static final String BLUE = "\033[34m";

    // Composed labels
    private static final String USER_LABEL = BOLD + CYAN;
    private static final String AGENT_LABEL = BOLD + GREEN;
    private static final String AGENT_TEXT = DARK_GREEN; // body text of Vincenzo's responses
    private static final String THINKING_LABEL = DIM + GREEN;
    private static final String ERROR_LABEL = BOLD + YELLOW;
    private static final String SECTION_COLOR = DIM + BLUE;

    // ── Markdown regex patterns ───────────────────────────────────────────────

    // **bold** or __bold__
    private static final Pattern BOLD_PAT = Pattern.compile("\\*\\*(.+?)\\*\\*|__(.+?)__");

    // *italic* or _italic_ — single asterisk/underscore (not double)
    private static final Pattern ITALIC_PAT = Pattern.compile("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)"
            + "|(?<!_)_(?!_)(.+?)(?<!_)_(?!_)");

    // [text](url)
    private static final Pattern LINK_PAT = Pattern.compile("\\[([^\\]]+)]\\(([^)]+)\\)");

    // `inline code`
    private static final Pattern CODE_PAT = Pattern.compile("`([^`]+)`");

    // Bullet: line starting with * or - followed by space
    private static final Pattern BULLET_PAT = Pattern.compile("^(\\s*)[*\\-]\\s+(.+)$");

    // Numbered list: "1. text"
    private static final Pattern NUMBERED_PAT = Pattern.compile("^(\\s*)(\\d+\\.\\s+)(.+)$");

    // Horizontal rule: ---, ***, ___ alone on a line
    private static final Pattern HR_PAT = Pattern.compile("^[-*_]{3,}\\s*$");

    // Headers
    private static final Pattern H3_PAT = Pattern.compile("^###\\s+(.+)$");
    private static final Pattern H2_PAT = Pattern.compile("^##\\s+(.+)$");
    private static final Pattern H1_PAT = Pattern.compile("^#\\s+(.+)$");

    // ── Fields ────────────────────────────────────────────────────────────────

    private static final LogService LOG = LogService.getInstance();

    private final Scanner scanner;
    private final SearchAgent agent;
    private final AtomicInteger turnCounter = new AtomicInteger(0);
    private boolean running = true;

    public ChatInterface(SearchAgent agent) {
        this.scanner = new Scanner(System.in);
        this.agent = agent;
        RetryUtils.setRetryListener(msg -> {
            System.out.print("\033[2K\r"
                    + THINKING_LABEL + "Vincenzo: " + RESET
                    + DIM + "[" + msg + "]" + RESET);
            System.out.flush();
        });
        LOG.info("ChatInterface", "ChatInterface instantiated");
    }

    // ── Public entry point ────────────────────────────────────────────────────

    public void startChat() {
        showWelcome();

        while (running) {
            String input = getUserInput();
            if (input.isEmpty())
                continue;

            if (isExitCommand(input)) {
                running = false;
                LOG.info("ChatInterface", "User requested exit");
                System.out.println(DIM + "See you later!" + RESET);
                continue;
            }

            processUserQuery(input);
        }

        cleanup();
    }

    // ── Welcome ───────────────────────────────────────────────────────────────

    private void showWelcome() {
        LOG.section("CHAT SESSION START");
        String sep = SECTION_COLOR + "─".repeat(60) + RESET;
        System.out.println(sep);
        System.out.println(BOLD + "  Vincenzo — Internet Assistant" + RESET);
        System.out.println(DIM + "  Google ADK + Playwright  │  Log: "
                + LOG.getLogFile().getFileName() + RESET);
        System.out.println(sep);
        LOG.info("ChatInterface", "AI service initialized");
        System.out.println(GREEN + "✓ Ready to chat." + RESET);
        System.out.println();
        System.out.println(DIM + "Commands: 'sair', 'exit', 'quit' or 'bye' to exit." + RESET);
        System.out.println(sep);
        System.out.println();
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    private String getUserInput() {
        System.out.print(USER_LABEL + "Você: " + RESET);
        String input = scanner.nextLine().trim();

        if (input.isEmpty())
            return "";

        if (input.length() > 1000) {
            LOG.warn("ChatInterface", "Input too long (" + input.length() + " chars), rejected");
            System.out.println(ERROR_LABEL + "Vincenzo: " + RESET
                    + "Please keep the message below 1000 characters.\n");
            return "";
        }

        // Strip non-printable control chars (keep \r \n \t)
        input = input.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");
        LOG.userMessage(input);
        return input;
    }

    private boolean isExitCommand(String input) {
        String lower = input.toLowerCase();
        return lower.equals("exit") || lower.equals("quit")
                || lower.equals("bye") || lower.equals("sair");
    }

    // ── Query processing ──────────────────────────────────────────────────────

    private void processUserQuery(String input) {
        int turn = turnCounter.incrementAndGet();
        LOG.divider("TURN #" + turn);

        // Show "thinking" indicator on its own line
        System.out.println(THINKING_LABEL + "Vincenzo: " + RESET
                + DIM + "[thinking...]" + RESET);

        long start = System.currentTimeMillis();
        try {
            String response = agent.processQuery(input);
            long elapsed = System.currentTimeMillis() - start;

            LOG.agentResponse(response);
            LOG.timing("ChatInterface", "Turn #" + turn + " end-to-end", elapsed);

            // Erase the "thinking" line, then print the real response
            System.out.print("\033[1A\033[2K\r");
            System.out.println(AGENT_LABEL + "Vincenzo:" + RESET);
            System.out.println(AGENT_TEXT + renderMarkdown(response) + RESET);
            System.out.println();

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            LOG.error("ChatInterface", "Error on turn #" + turn + " after " + elapsed + " ms", e);

            System.out.print("\033[1A\033[2K\r");
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

            System.out.print(ERROR_LABEL + "Vincenzo: " + RESET);
            if (msg.contains("timeout") || msg.contains("timed out")) {
                System.out.println("It took too long. Try something simpler.\n");
            } else if (msg.contains("rate limit") || msg.contains("quota")) {
                System.out.println("Too many requests — wait a moment.\n");
            } else if (msg.contains("unavailable") || msg.contains("connection")) {
                System.out.println("Connection problem. Check your network.\n");
            } else {
                System.out.println("Something went wrong. Try rephrasing the question.");
                System.out.println(DIM + "  (Debug: " + e.getMessage() + ")" + RESET + "\n");
            }
        }
    }

    // ── Markdown renderer ─────────────────────────────────────────────────────

    /**
     * Converts a Markdown-flavoured LLM response to ANSI-formatted terminal output.
     * Line-by-line processing handles: headers, bullet lists, numbered lists,
     * horizontal rules, fenced code blocks. Inline formatting (bold, italic,
     * code, links) is applied on each line via {@link #inlineFormat(String)}.
     */
    static String renderMarkdown(String text) {
        if (text == null || text.isBlank())
            return "";

        StringBuilder out = new StringBuilder();
        String[] lines = text.split("\n", -1);
        boolean inCodeBlock = false;

        for (String raw : lines) {

            // ── Fenced code blocks ─────────────────────────────────────────────
            if (raw.trim().startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                if (inCodeBlock) {
                    String lang = raw.trim().replaceFirst("^```", "").trim();
                    String label = lang.isEmpty() ? "código" : lang;
                    out.append(DIM).append("  ┌─ ").append(label)
                            .append(" ").append("─".repeat(Math.max(1, 30 - label.length())))
                            .append(RESET).append("\n");
                } else {
                    out.append(DIM).append("  └").append("─".repeat(33))
                            .append(RESET).append("\n");
                }
                continue;
            }

            if (inCodeBlock) {
                out.append(DIM).append("  │ ").append(raw).append(RESET).append("\n");
                continue;
            }

            // ── Horizontal rule ────────────────────────────────────────────────
            if (HR_PAT.matcher(raw.trim()).matches()) {
                out.append(DIM).append("─".repeat(56)).append(RESET).append("\n");
                continue;
            }

            // ── Headers ────────────────────────────────────────────────────────
            Matcher h1 = H1_PAT.matcher(raw.trim());
            if (h1.matches()) {
                String title = h1.group(1);
                out.append("\n")
                        .append(BOLD).append(title.toUpperCase()).append(RESET).append("\n")
                        .append(DIM).append("─".repeat(Math.min(title.length() + 2, 56)))
                        .append(RESET).append("\n");
                continue;
            }
            Matcher h2 = H2_PAT.matcher(raw.trim());
            if (h2.matches()) {
                out.append("\n").append(BOLD).append(h2.group(1)).append(RESET).append("\n");
                continue;
            }
            Matcher h3 = H3_PAT.matcher(raw.trim());
            if (h3.matches()) {
                out.append(BOLD).append(ITALIC).append(h3.group(1)).append(RESET).append("\n");
                continue;
            }

            // ── Bullet list ────────────────────────────────────────────────────
            Matcher bullet = BULLET_PAT.matcher(raw);
            if (bullet.matches()) {
                String indent = bullet.group(1);
                String content = inlineFormat(bullet.group(2));
                out.append(indent).append(CYAN).append("  •").append(RESET)
                        .append(" ").append(content).append("\n");
                continue;
            }

            // ── Numbered list ──────────────────────────────────────────────────
            Matcher numbered = NUMBERED_PAT.matcher(raw);
            if (numbered.matches()) {
                String indent = numbered.group(1);
                String number = numbered.group(2);
                String content = inlineFormat(numbered.group(3));
                out.append(indent).append(CYAN).append(number).append(RESET)
                        .append(content).append("\n");
                continue;
            }

            // ── Normal line ────────────────────────────────────────────────────
            out.append(inlineFormat(raw)).append("\n");
        }

        return out.toString().stripTrailing();
    }

    /**
     * Applies inline Markdown formatting to a single line of text.
     * Order: code → bold → italic → links → stray-asterisk cleanup.
     */
    private static String inlineFormat(String line) {

        // 1. Inline code `...` — protect first so inner content isn't reformatted
        line = CODE_PAT.matcher(line).replaceAll(
                m -> DIM + m.group(1).replace("$", "\\$") + RESET);

        // 2. Bold **text** or __text__
        line = BOLD_PAT.matcher(line).replaceAll(m -> {
            String inner = m.group(1) != null ? m.group(1) : m.group(2);
            return BOLD + inner.replace("$", "\\$") + RESET;
        });

        // 3. Italic *text* or _text_
        line = ITALIC_PAT.matcher(line).replaceAll(m -> {
            String inner = m.group(1) != null ? m.group(1) : m.group(2);
            return ITALIC + inner.replace("$", "\\$") + RESET;
        });

        // 4. Links [text](url) → text (dim url)
        line = LINK_PAT.matcher(line).replaceAll(m -> m.group(1).replace("$", "\\$")
                + " " + DIM + "(" + m.group(2).replace("$", "\\$") + ")" + RESET);

        // 5. Remove any stray lone asterisks left over (e.g. unclosed Markdown)
        line = line.replaceAll("(?<!\\*)\\*(?!\\*)", "");

        return line;
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    private void cleanup() {
        LOG.info("ChatInterface", "Total turns: " + turnCounter.get());
        LOG.sessionEnd();
        scanner.close();
        System.out.println(DIM + "\nLog salvo em: "
                + LOG.getLogFile().toAbsolutePath() + RESET);
    }
}
