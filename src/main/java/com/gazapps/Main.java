package com.gazapps;

import com.gazapps.agent.SearchAgent;
import com.gazapps.config.AppConfig;
import com.gazapps.logging.LogService;
import com.gazapps.services.NotificationService;
import com.gazapps.services.SchedulerService;
import com.gazapps.ui.ChatInterface;
import com.gazapps.ui.TelegramInterface;

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
        // Apply SLF4J SimpleLogger settings from application.properties BEFORE
        // the first LogService call triggers SLF4J initialization.
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", AppConfig.SLF4J_DEFAULT_LOG_LEVEL);
        System.setProperty("org.slf4j.simpleLogger.showDateTime",    AppConfig.SLF4J_SHOW_DATE_TIME);
        System.setProperty("org.slf4j.simpleLogger.showThreadName",  AppConfig.SLF4J_SHOW_THREAD_NAME);
        System.setProperty("org.slf4j.simpleLogger.showLogName",     AppConfig.SLF4J_SHOW_LOG_NAME);

        // Initialize logger — creates the session log file
        LogService log = LogService.getInstance();
        log.info("Main", "Application starting");
        log.info("Main", "Java version : " + System.getProperty("java.version"));
        log.info("Main", "OS           : " + System.getProperty("os.name")
                + " " + System.getProperty("os.version"));
        log.info("Main", "Working dir  : " + System.getProperty("user.dir"));

        validateEnvironment(log);

        log.info("Main", "GOOGLE_API_KEY is set (length: "
                + System.getenv("GOOGLE_API_KEY").length() + ")");

        SearchAgent agent = new SearchAgent();

        // Initialize SchedulerService singleton — reloads persisted jobs from disk
        SchedulerService.getInstance();
        log.info("Main", "SchedulerService initialized");

        if ("telegram".equalsIgnoreCase(AppConfig.INTERFACE_MODE)) {
            validateTelegramConfig(log);
            TelegramInterface telegram = new TelegramInterface(agent);
            // Inject TelegramBot into NotificationService for proactive message delivery
            NotificationService.getInstance().setBot(telegram.getBot());
            telegram.start();
        } else {
            ChatInterface chat = new ChatInterface(agent);
            chat.startChat();
        }

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

    private static void validateTelegramConfig(LogService log) {
        if (AppConfig.TELEGRAM_BOT_TOKEN.isBlank()) {
            log.error("Main", "TELEGRAM_BOT_TOKEN not set — aborting");
            System.err.println("""
                    ERROR: Telegram bot token is not configured.
                    Set it in application.properties:  telegram.bot.token=<token>
                    Or via environment variable:        export TELEGRAM_BOT_TOKEN="<token>"
                    Get a token from @BotFather on Telegram.
                    """);
            System.exit(1);
        }
        log.info("Main", "TELEGRAM_BOT_TOKEN is set (length: " + AppConfig.TELEGRAM_BOT_TOKEN.length() + ")");
        log.info("Main", "Telegram mode: " + AppConfig.TELEGRAM_MODE);
    }
}
