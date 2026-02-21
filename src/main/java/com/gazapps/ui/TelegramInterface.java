package com.gazapps.ui;

import com.gazapps.agent.SearchAgent;
import com.gazapps.config.AppConfig;
import com.gazapps.logging.LogService;
import com.gazapps.util.RetryUtils;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.utility.BotUtils;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.GetUpdates;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SetWebhook;
import com.pengrad.telegrambot.request.DeleteWebhook;
import com.pengrad.telegrambot.response.GetUpdatesResponse;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Telegram interface for the AI Internet Search Assistant.
 *
 * <p>Supports two update modes:
 * <ul>
 *   <li><b>polling</b> — calls {@code getUpdates} in a long-poll loop.
 *       No open port or public URL required. Default mode.</li>
 *   <li><b>webhook</b> — starts a {@code com.sun.net.httpserver.HttpServer} on
 *       the configured port and registers the webhook URL with Telegram.</li>
 * </ul>
 *
 * <p>Configure via {@code application.properties}:
 * <pre>
 *   interface.mode=telegram
 *   telegram.bot.token=&lt;token&gt;          # or env TELEGRAM_BOT_TOKEN
 *   telegram.mode=polling | webhook
 *   telegram.webhook.port=8443            # webhook only
 *   telegram.webhook.url=https://...      # webhook only
 * </pre>
 */
public class TelegramInterface {

    private static final LogService LOG = LogService.getInstance();

    /** Telegram max message length (API limit). */
    private static final int TELEGRAM_MAX_LENGTH = 4096;

    private final SearchAgent    agent;
    private final TelegramBot    bot;
    private final AtomicInteger  turnCounter = new AtomicInteger(0);
    private volatile boolean     running     = true;

    public TelegramInterface(SearchAgent agent) {
        this.agent = agent;
        this.bot   = new TelegramBot(AppConfig.TELEGRAM_BOT_TOKEN);

        RetryUtils.setRetryListener(msg ->
                LOG.info("TelegramInterface", "Retry: " + msg));

        LOG.info("TelegramInterface", "TelegramInterface instantiated");
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    /**
     * Starts the Telegram interface. Blocks until the application receives a
     * shutdown signal (SIGINT / SIGTERM) or {@link #stop()} is called.
     */
    public void start() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::cleanup, "telegram-shutdown"));

        LOG.section("TELEGRAM SESSION START");
        LOG.info("TelegramInterface", "Update mode: " + AppConfig.TELEGRAM_MODE);

        if ("webhook".equalsIgnoreCase(AppConfig.TELEGRAM_MODE)) {
            startWebhook();
        } else {
            startPolling();
        }
    }

    /** Signals the interface to stop gracefully. */
    public void stop() {
        running = false;
    }

    // ── Polling ───────────────────────────────────────────────────────────────

    private void startPolling() {
        // Make sure no stale webhook is registered, otherwise getUpdates returns 409
        bot.execute(new DeleteWebhook());
        LOG.info("TelegramInterface", "Polling loop started");

        int offset = 0;

        while (running) {
            try {
                GetUpdatesResponse resp = bot.execute(
                        new GetUpdates().offset(offset).timeout(30));

                if (resp == null || !resp.isOk()) {
                    LOG.warn("TelegramInterface", "getUpdates error: "
                            + (resp != null ? resp.errorCode() + " " + resp.description() : "null response"));
                    Thread.sleep(5_000);
                    continue;
                }

                for (Update u : resp.updates()) {
                    offset = u.updateId() + 1;
                    handleUpdate(u);
                }

                // Avoid busy-loop when there are no updates
                if (resp.updates().isEmpty()) {
                    Thread.sleep(1_000);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            } catch (Exception e) {
                LOG.error("TelegramInterface", "Unexpected error in polling loop", e);
                try { Thread.sleep(5_000); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    running = false;
                }
            }
        }

        LOG.info("TelegramInterface", "Polling loop stopped");
    }

    // ── Webhook ───────────────────────────────────────────────────────────────

    private void startWebhook() {
        String webhookUrl = AppConfig.TELEGRAM_WEBHOOK_URL;
        int    port       = AppConfig.TELEGRAM_WEBHOOK_PORT;

        if (webhookUrl.isBlank()) {
            LOG.error("TelegramInterface", "telegram.webhook.url is empty — aborting webhook mode");
            System.err.println("ERROR: telegram.webhook.url must be set for webhook mode.");
            System.exit(1);
        }

        // Register webhook with Telegram
        bot.execute(new SetWebhook().url(webhookUrl));
        LOG.info("TelegramInterface", "Webhook registered: " + webhookUrl);

        // Start JDK HttpServer
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            LOG.error("TelegramInterface", "Failed to start HttpServer on port " + port, e);
            System.err.println("ERROR: Cannot bind HttpServer to port " + port + ": " + e.getMessage());
            System.exit(1);
            return; // unreachable — satisfies compiler
        }

        server.createContext("/webhook", exchange -> {
            try {
                byte[] body = exchange.getRequestBody().readAllBytes();
                Update update = BotUtils.parseUpdate(
                        new String(body, StandardCharsets.UTF_8));
                handleUpdate(update);
                exchange.sendResponseHeaders(200, 0);
            } catch (Exception e) {
                LOG.error("TelegramInterface", "Error handling webhook request", e);
                exchange.sendResponseHeaders(500, 0);
            } finally {
                exchange.close();
            }
        });

        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        LOG.info("TelegramInterface", "HttpServer listening on port " + port + " at /webhook");

        // Block main thread until stop signal
        try {
            while (running) Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        server.stop(2);
        LOG.info("TelegramInterface", "HttpServer stopped");
    }

    // ── Update handler (shared by both modes) ─────────────────────────────────

    private void handleUpdate(Update u) {
        if (u.message() == null || u.message().text() == null) return;

        long   chatId = u.message().chat().id();
        String text   = u.message().text().trim();

        // Strip non-printable control characters (keep \r \n \t)
        text = text.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");

        if (text.isEmpty()) return;

        if (text.length() > 1000) {
            LOG.warn("TelegramInterface", "Input too long (" + text.length() + " chars) from chat " + chatId);
            bot.execute(new SendMessage(chatId, "Please keep your message under 1000 characters."));
            return;
        }

        int turn = turnCounter.incrementAndGet();
        LOG.divider("TELEGRAM TURN #" + turn);
        LOG.userMessage(text);

        // Immediate feedback so the user knows the bot is working
        bot.execute(new SendMessage(chatId, "⏳ Working on it..."));

        long start = System.currentTimeMillis();
        try {
            String response = agent.processQuery(text);
            long elapsed = System.currentTimeMillis() - start;

            LOG.agentResponse(response);
            LOG.timing("TelegramInterface", "Turn #" + turn + " end-to-end", elapsed);

            // Telegram API allows up to 4096 characters per message
            if (response.length() > TELEGRAM_MAX_LENGTH) {
                response = response.substring(0, TELEGRAM_MAX_LENGTH - 1) + "…";
            }

            bot.execute(new SendMessage(chatId, response));

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            LOG.error("TelegramInterface", "Error on turn #" + turn + " after " + elapsed + " ms", e);

            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            String errorReply;
            if (msg.contains("timeout") || msg.contains("timed out")) {
                errorReply = "⏱ That took too long. Please try something simpler.";
            } else if (msg.contains("rate limit") || msg.contains("quota")) {
                errorReply = "🚦 Too many requests — please wait a moment.";
            } else if (msg.contains("unavailable") || msg.contains("connection")) {
                errorReply = "🔌 Connection issue. Please check your network.";
            } else {
                errorReply = "❌ Something went wrong. Try rephrasing your question.";
            }
            bot.execute(new SendMessage(chatId, errorReply));
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /**
     * Returns the underlying {@link TelegramBot} instance so other services
     * (e.g. {@code NotificationService}) can send proactive messages.
     */
    public TelegramBot getBot() {
        return this.bot;
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    private void cleanup() {
        running = false;
        LOG.info("TelegramInterface", "Total turns: " + turnCounter.get());
        LOG.sessionEnd();
    }
}
