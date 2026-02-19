package com.gazapps;

import com.gazapps.agent.SearchAgent;
import com.gazapps.logging.LogService;
import com.gazapps.ui.ChatInterface;

/**
 * Entry point for the AI Internet Search Assistant.
 *
 * Prerequisites:
 *   export GOOGLE_API_KEY="your-api-key-here"
 *
 * Run:
 *   mvn compile exec:java
 *
 * Logs are written to:  logs/session-<timestamp>.log
 */
public class Main {

    public static void main(String[] args) {
        // Initialize logger first — creates the log file
        LogService log = LogService.getInstance();
        log.info("Main", "Application starting");
        log.info("Main", "Java version : " + System.getProperty("java.version"));
        log.info("Main", "OS           : " + System.getProperty("os.name")
                + " " + System.getProperty("os.version"));
        log.info("Main", "Working dir  : " + System.getProperty("user.dir"));

        validateEnvironment(log);

        log.info("Main", "GOOGLE_API_KEY is set (length: "
                + System.getenv("GOOGLE_API_KEY").length() + ")");

        SearchAgent    agent = new SearchAgent();
        ChatInterface  chat  = new ChatInterface(agent);
        chat.startChat();

        log.info("Main", "Application exiting normally");
        // Force JVM exit to stop lingering OkHttp/gRPC daemon threads
        System.exit(0);
    }

    private static void validateEnvironment(LogService log) {
        String apiKey = System.getenv("GOOGLE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            log.error("Main", "GOOGLE_API_KEY not set — aborting");
            System.err.println("""
                    ERROR: GOOGLE_API_KEY environment variable is not set.
                    Get your key at: https://aistudio.google.com/app/apikey
                    Then run:  export GOOGLE_API_KEY="your-key"
                    """);
            System.exit(1);
        }
    }
}
