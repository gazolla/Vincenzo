package com.gazapps.ui;

import com.gazapps.agent.SearchAgent;
import com.gazapps.logging.LogService;

import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Command-line chat interface.
 * Every user input, agent response, error and timing is logged.
 */
public class ChatInterface {

    private static final LogService LOG = LogService.getInstance();

    private final Scanner      scanner;
    private final SearchAgent  agent;
    private final AtomicInteger turnCounter = new AtomicInteger(0);
    private boolean running = true;

    public ChatInterface(SearchAgent agent) {
        this.scanner = new Scanner(System.in);
        this.agent   = agent;
        LOG.info("ChatInterface", "ChatInterface instantiated");
    }

    public void startChat() {
        showWelcome();

        while (running) {
            String input = getUserInput();
            if (input.isEmpty()) continue;

            if (isExitCommand(input)) {
                running = false;
                LOG.info("ChatInterface", "User requested exit");
                System.out.println("Goodbye!");
                continue;
            }

            processUserQuery(input);
        }

        cleanup();
    }

    // ── Welcome ───────────────────────────────────────────────────────────────

    private void showWelcome() {
        LOG.section("CHAT SESSION START");
        System.out.println("=".repeat(60));
        System.out.println("  AI Internet Search Assistant");
        System.out.println("  Powered by Google ADK + Playwright");
        System.out.println("  Log: " + LOG.getLogFile().toAbsolutePath());
        System.out.println("=".repeat(60));
        LOG.info("ChatInterface", "AI service initialized");
        System.out.println("AI service is ready.");

        System.out.println();
        System.out.println("Commands: 'exit', 'quit' or 'bye' to stop.");
        System.out.println("Ask anything — I can search the internet for current information!");
        System.out.println("-".repeat(60));
        System.out.println();
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    private String getUserInput() {
        System.out.print("You: ");
        String input = scanner.nextLine().trim();

        if (input.isEmpty()) return "";

        if (input.length() > 1000) {
            LOG.warn("ChatInterface", "Input too long (" + input.length() + " chars), rejected");
            System.out.println("Assistant: Please keep your message under 1000 characters.\n");
            return "";
        }

        // Strip non-printable control chars (keep \r \n \t)
        input = input.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");
        LOG.userMessage(input);
        return input;
    }

    private boolean isExitCommand(String input) {
        String lower = input.toLowerCase();
        return lower.equals("exit") || lower.equals("quit") || lower.equals("bye");
    }

    // ── Query processing ──────────────────────────────────────────────────────

    private void processUserQuery(String input) {
        int turn = turnCounter.incrementAndGet();
        LOG.divider("TURN #" + turn);

        System.out.println("Assistant: [thinking...]\r\033[A");

        long start = System.currentTimeMillis();
        try {
            String response = agent.processQuery(input);
            long elapsed = System.currentTimeMillis() - start;

            LOG.agentResponse(response);
            LOG.timing("ChatInterface", "Turn #" + turn + " end-to-end", elapsed);

            System.out.print("\033[2K\r");
            System.out.println("Assistant: " + response);
            System.out.println();

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            LOG.error("ChatInterface", "Error on turn #" + turn + " after " + elapsed + " ms", e);

            System.out.print("\033[2K\r");
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

            if (msg.contains("timeout") || msg.contains("timed out")) {
                System.out.println("Assistant: That took too long. Please try something simpler.\n");
            } else if (msg.contains("rate limit") || msg.contains("quota")) {
                System.out.println("Assistant: Too many requests — please wait a moment.\n");
            } else if (msg.contains("unavailable") || msg.contains("connection")) {
                System.out.println("Assistant: Connection issue. Please check your network.\n");
            } else {
                System.out.println("Assistant: Something went wrong. Try rephrasing your question.\n");
                System.out.println("  (Debug: " + e.getMessage() + ")\n");
            }
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    private void cleanup() {
        LOG.info("ChatInterface", "Total turns: " + turnCounter.get());
        LOG.sessionEnd();
        scanner.close();
        System.out.println("\nLog saved to: " + LOG.getLogFile().toAbsolutePath());
    }
}
