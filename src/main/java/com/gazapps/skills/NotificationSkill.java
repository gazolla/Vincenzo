package com.gazapps.skills;

import com.gazapps.logging.LogService;
import com.gazapps.services.NotificationService;
import com.gazapps.services.NotificationService.Notification;
import com.gazapps.util.SkillStatus;
import com.gazapps.util.StringUtils;
import com.google.adk.tools.Annotations.Schema;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ADK skill for sending and managing user notifications.
 *
 * <p>Three tools:
 * <ul>
 *   <li>{@link #sendNotification} — deliver a message via Telegram or in-memory queue</li>
 *   <li>{@link #listPendingNotifications} — list queued (undelivered) notifications</li>
 *   <li>{@link #markAsRead} — acknowledge a queued notification</li>
 * </ul>
 *
 * <p>Delegates to {@link NotificationService} which handles both Telegram delivery
 * (when {@code notification.telegram.chat.id} is configured) and the in-memory
 * fallback queue used in CLI mode.
 */
public class NotificationSkill {

    private static final LogService LOG = LogService.getInstance();

    // ── Tool 1: sendNotification ──────────────────────────────────────────────

    @Schema(description = """
            Send a proactive notification or alert message to the user.
            In Telegram mode (when notification.telegram.chat.id is configured),
            the message is delivered immediately via Telegram.
            In CLI mode, the notification is stored in a queue and can be retrieved
            with listPendingNotifications().
            Use this to alert the user about important findings, monitor matches,
            or any information that requires immediate attention.
            """)
    public static Map<String, String> sendNotification(
            @Schema(name = "message", description = "The notification text to send to the user. Keep it concise and informative.") String message) {

        LOG.section("TOOL CALL: sendNotification");

        if (message == null || message.isBlank()) {
            LOG.warn("NotificationSkill", "Rejected blank message");
            Map<String, String> err = new HashMap<>();
            err.put("status",  SkillStatus.ERROR.value());
            err.put("message", "message must not be blank");
            return err;
        }

        LOG.info("NotificationSkill", "Sending notification: " + StringUtils.truncate(message, 80));

        String notifId = NotificationService.getInstance().send(message);

        String delivery = "sent_telegram".equals(notifId) ? "telegram" : "queued";

        Map<String, String> result = new HashMap<>();
        result.put("status",          SkillStatus.SUCCESS.value());
        result.put("notification_id", notifId);
        result.put("delivery",        delivery);
        result.put("message_preview", StringUtils.truncate(message, 100));

        LOG.toolResult("sendNotification", result);
        return result;
    }

    // ── Tool 2: listPendingNotifications ─────────────────────────────────────

    @Schema(description = """
            List all notifications currently held in the in-memory queue.
            This is primarily useful in CLI mode where Telegram is not active.
            In Telegram mode, notifications are delivered immediately and do not
            accumulate in the queue unless Telegram delivery fails.
            Each notification has an id, message, timestamp, and read status.
            Use markAsRead() to acknowledge notifications after viewing them.
            """)
    public static Map<String, String> listPendingNotifications() {

        LOG.section("TOOL CALL: listPendingNotifications");

        List<Notification> pending = NotificationService.getInstance().listPending();

        JsonArray jsonNotifs = new JsonArray();
        for (Notification n : pending) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id",        n.id);
            obj.addProperty("message",   n.message);
            obj.addProperty("timestamp", n.timestamp);
            obj.addProperty("read",      n.read);
            jsonNotifs.add(obj);
        }

        Map<String, String> result = new HashMap<>();
        result.put("status",        SkillStatus.SUCCESS.value());
        result.put("count",         String.valueOf(pending.size()));
        result.put("notifications", jsonNotifs.toString());

        LOG.toolResult("listPendingNotifications", result);
        return result;
    }

    // ── Tool 3: markAsRead ────────────────────────────────────────────────────

    @Schema(description = """
            Mark a queued notification as read by its id.
            Use listPendingNotifications() first to see notification ids.
            This is useful in CLI mode to acknowledge notifications after reviewing them.
            Returns success if the notification was found and marked, error if not found.
            """)
    public static Map<String, String> markAsRead(
            @Schema(name = "notificationId", description = "The notification id to mark as read (e.g. 'notif-3'). Use listPendingNotifications() to find this id.") String notificationId) {

        LOG.section("TOOL CALL: markAsRead");
        LOG.info("NotificationSkill", "notificationId=" + notificationId);

        if (notificationId == null || notificationId.isBlank()) {
            Map<String, String> err = new HashMap<>();
            err.put("status",          "error");
            err.put("notification_id", notificationId != null ? notificationId : "");
            err.put("message",         "notificationId must not be blank");
            return err;
        }

        boolean found = NotificationService.getInstance().markRead(notificationId);

        Map<String, String> result = new HashMap<>();
        if (found) {
            result.put("status",          SkillStatus.SUCCESS.value());
            result.put("notification_id", notificationId);
            result.put("message",         "Notification marked as read");
        } else {
            result.put("status",          SkillStatus.ERROR.value());
            result.put("notification_id", notificationId);
            result.put("message",         "Notification not found: " + notificationId
                    + ". Use listPendingNotifications() to see available ids.");
        }

        LOG.toolResult("markAsRead", result);
        return result;
    }
}
