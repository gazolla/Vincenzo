package com.gazapps.services;

import com.gazapps.config.AppConfig;
import com.gazapps.logging.LogService;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Singleton service responsible for delivering notifications to the user.
 *
 * <p>In Telegram mode, the {@link TelegramBot} reference is injected via
 * {@link #setBot(TelegramBot)} from {@code Main.java} after the interface is
 * created. When the bot is available, notifications are sent immediately to the
 * configured {@code notification.telegram.chat.id}.</p>
 *
 * <p>In CLI mode (or when the bot is not yet set), notifications are queued
 * in-memory and can be retrieved with {@link #listPending()}.</p>
 */
public class NotificationService {

    private static final LogService LOG = LogService.getInstance();
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static volatile NotificationService instance;

    public static NotificationService getInstance() {
        if (instance == null) {
            synchronized (NotificationService.class) {
                if (instance == null) {
                    instance = new NotificationService();
                }
            }
        }
        return instance;
    }

    private NotificationService() {
        LOG.info("NotificationService", "Initialized (queue max=" + AppConfig.getInstance().NOTIFICATION_QUEUE_MAX_SIZE + ")");
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private volatile TelegramBot bot = null;
    private final ConcurrentLinkedQueue<Notification> pendingQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger counter = new AtomicInteger(0);

    // ── Notification record ───────────────────────────────────────────────────

    public static class Notification {
        public final String id;
        public final String message;
        public final String timestamp;
        public volatile boolean read;

        public Notification(String id, String message) {
            this.id        = id;
            this.message   = message;
            this.timestamp = LocalDateTime.now().format(ISO);
            this.read      = false;
        }
    }

    // ── Bot injection ─────────────────────────────────────────────────────────

    /**
     * Injects the {@link TelegramBot} instance so proactive Telegram messages
     * can be sent. Called once from {@code Main.java} in Telegram mode.
     */
    public void setBot(TelegramBot bot) {
        this.bot = bot;
        LOG.info("NotificationService", "TelegramBot reference set — Telegram notifications active");
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Send a notification to the user.
     *
     * <p>If a Telegram bot is configured and {@code notification.telegram.chat.id}
     * is set, the message is sent immediately via Telegram and {@code "sent_telegram"}
     * is returned. Otherwise the message is enqueued and a {@code "notif-N"} id
     * is returned.</p>
     *
     * @param message the notification text
     * @return {@code "sent_telegram"} on Telegram delivery, or {@code "notif-N"} on queue
     */
    public String send(String message) {
        String chatId = AppConfig.getInstance().NOTIFICATION_TELEGRAM_CHAT_ID;

        if (bot != null && !chatId.isBlank()) {
            try {
                bot.execute(new SendMessage(Long.parseLong(chatId.trim()), message));
                LOG.info("NotificationService", "Telegram notification sent to chatId=" + chatId);
                return "sent_telegram";
            } catch (Exception e) {
                LOG.warn("NotificationService", "Telegram send failed, falling back to queue: " + e.getMessage());
            }
        }

        return enqueue(message);
    }

    /**
     * Returns all notifications currently in the pending queue (read or unread).
     */
    public List<Notification> listPending() {
        return new ArrayList<>(pendingQueue);
    }

    /**
     * Marks a notification as read by its id.
     *
     * @param id the notification id (e.g. {@code "notif-3"})
     * @return {@code true} if found and marked; {@code false} if not found
     */
    public boolean markRead(String id) {
        for (Notification n : pendingQueue) {
            if (n.id.equals(id)) {
                n.read = true;
                LOG.info("NotificationService", "Notification " + id + " marked as read");
                return true;
            }
        }
        LOG.warn("NotificationService", "Notification not found: " + id);
        return false;
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private String enqueue(String message) {
        int maxSize = AppConfig.getInstance().NOTIFICATION_QUEUE_MAX_SIZE;
        // Evict oldest if at capacity
        while (pendingQueue.size() >= maxSize) {
            Notification evicted = pendingQueue.poll();
            if (evicted != null) {
                LOG.debug("NotificationService", "Evicted oldest notification: " + evicted.id);
            }
        }
        String id = "notif-" + counter.incrementAndGet();
        pendingQueue.add(new Notification(id, message));
        LOG.info("NotificationService", "Queued notification " + id
                + " (queue size=" + pendingQueue.size() + ")");
        return id;
    }
}
