package com.gazapps.skills;

import com.gazapps.services.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link NotificationSkill}.
 *
 * <p>All tests operate without a Telegram bot (CLI mode), so deliveries
 * always fall back to the in-memory queue. The {@link NotificationService}
 * singleton is reset between tests.
 */
class NotificationSkillTest {

    @BeforeEach
    void setUp() throws Exception {
        resetNotificationSingleton();
    }

    @AfterEach
    void tearDown() throws Exception {
        resetNotificationSingleton();
    }

    // ── sendNotification — input validation ───────────────────────────────────

    @Test
    void sendNotification_nullMessage_returnsErrorMap() {
        Map<String, String> result = NotificationSkill.sendNotification(null);
        assertEquals("error", result.get("status"));
        assertNotNull(result.get("message"));
    }

    @Test
    void sendNotification_blankMessage_returnsErrorMap() {
        Map<String, String> result = NotificationSkill.sendNotification("   ");
        assertEquals("error", result.get("status"));
        assertTrue(result.get("message").toLowerCase().contains("blank") ||
                   result.get("message").toLowerCase().contains("message"));
    }

    @Test
    void sendNotification_emptyMessage_returnsErrorMap() {
        Map<String, String> result = NotificationSkill.sendNotification("");
        assertEquals("error", result.get("status"));
    }

    // ── sendNotification — success path (CLI mode / queue) ───────────────────

    @Test
    void sendNotification_validMessage_returnsSuccessMap() {
        Map<String, String> result = NotificationSkill.sendNotification("Hello user!");
        assertEquals("success", result.get("status"));
    }

    @Test
    void sendNotification_noBotConfigured_deliveryIsQueued() {
        Map<String, String> result = NotificationSkill.sendNotification("Test message");
        assertEquals("queued", result.get("delivery"),
                "Without Telegram bot, delivery should be 'queued'");
    }

    @Test
    void sendNotification_validMessage_hasNotificationId() {
        Map<String, String> result = NotificationSkill.sendNotification("Alert!");
        assertNotNull(result.get("notification_id"));
        assertFalse(result.get("notification_id").isBlank());
    }

    @Test
    void sendNotification_queued_notificationIdStartsWithNotif() {
        Map<String, String> result = NotificationSkill.sendNotification("Queued message");
        String id = result.get("notification_id");
        assertTrue(id.startsWith("notif-"), "Queued notification id should start with 'notif-'");
    }

    @Test
    void sendNotification_hasMessagePreview() {
        Map<String, String> result = NotificationSkill.sendNotification("Short message");
        assertNotNull(result.get("message_preview"));
        assertFalse(result.get("message_preview").isBlank());
    }

    @Test
    void sendNotification_longMessage_previewIsTruncated() {
        String longMsg = "A".repeat(200);
        Map<String, String> result = NotificationSkill.sendNotification(longMsg);
        assertEquals("success", result.get("status"));
        assertTrue(result.get("message_preview").length() <= 103,
                "Preview should be truncated to 100 chars + '...'");
    }

    @Test
    void sendNotification_messageAppearsInQueue() {
        NotificationSkill.sendNotification("Track me");

        Map<String, String> listResult = NotificationSkill.listPendingNotifications();
        assertEquals("1", listResult.get("count"));
        assertTrue(listResult.get("notifications").contains("Track me"));
    }

    // ── listPendingNotifications ──────────────────────────────────────────────

    @Test
    void listPendingNotifications_empty_returnsSuccessWithZeroCount() {
        Map<String, String> result = NotificationSkill.listPendingNotifications();
        assertEquals("success", result.get("status"));
        assertEquals("0", result.get("count"));
        assertEquals("[]", result.get("notifications").trim());
    }

    @Test
    void listPendingNotifications_afterSend_showsNotification() {
        NotificationSkill.sendNotification("First notification");

        Map<String, String> result = NotificationSkill.listPendingNotifications();
        assertEquals("success", result.get("status"));
        assertEquals("1", result.get("count"));
        assertTrue(result.get("notifications").contains("First notification"));
    }

    @Test
    void listPendingNotifications_alwaysHasNotificationsKey() {
        Map<String, String> result = NotificationSkill.listPendingNotifications();
        assertNotNull(result.get("notifications"));
    }

    @Test
    void listPendingNotifications_multipleMessages_countIsCorrect() {
        NotificationSkill.sendNotification("Message 1");
        NotificationSkill.sendNotification("Message 2");
        NotificationSkill.sendNotification("Message 3");

        Map<String, String> result = NotificationSkill.listPendingNotifications();
        assertEquals("3", result.get("count"));
    }

    @Test
    void listPendingNotifications_jsonContainsId() {
        NotificationSkill.sendNotification("With id");

        Map<String, String> result = NotificationSkill.listPendingNotifications();
        assertTrue(result.get("notifications").contains("notif-"),
                "JSON should contain notification id");
    }

    @Test
    void listPendingNotifications_jsonContainsTimestamp() {
        NotificationSkill.sendNotification("With timestamp");

        Map<String, String> result = NotificationSkill.listPendingNotifications();
        assertTrue(result.get("notifications").contains("timestamp"),
                "JSON should contain timestamp field");
    }

    // ── markAsRead ────────────────────────────────────────────────────────────

    @Test
    void markAsRead_nullId_returnsErrorMap() {
        Map<String, String> result = NotificationSkill.markAsRead(null);
        assertEquals("error", result.get("status"));
        assertNotNull(result.get("message"));
    }

    @Test
    void markAsRead_blankId_returnsErrorMap() {
        Map<String, String> result = NotificationSkill.markAsRead("   ");
        assertEquals("error", result.get("status"));
        assertTrue(result.get("message").toLowerCase().contains("blank") ||
                   result.get("message").toLowerCase().contains("notificationid"));
    }

    @Test
    void markAsRead_unknownId_returnsErrorMap() {
        Map<String, String> result = NotificationSkill.markAsRead("notif-999999");
        assertEquals("error", result.get("status"));
        assertTrue(result.get("message").toLowerCase().contains("not found") ||
                   result.get("message").contains("notif-999999"));
    }

    @Test
    void markAsRead_existingId_returnsSuccessMap() {
        Map<String, String> sent = NotificationSkill.sendNotification("Mark me");
        String id = sent.get("notification_id");

        Map<String, String> result = NotificationSkill.markAsRead(id);
        assertEquals("success", result.get("status"));
        assertEquals(id, result.get("notification_id"));
    }

    @Test
    void markAsRead_existingId_notificationAppearsAsRead() {
        Map<String, String> sent = NotificationSkill.sendNotification("Read me");
        String id = sent.get("notification_id");

        NotificationSkill.markAsRead(id);

        // Verify via service directly
        List<NotificationService.Notification> pending =
                NotificationService.getInstance().listPending();
        boolean isRead = pending.stream()
                .filter(n -> n.id.equals(id))
                .findFirst()
                .map(n -> n.read)
                .orElse(false);

        assertTrue(isRead, "Notification should be marked as read");
    }

    @Test
    void markAsRead_hasMessageKey() {
        Map<String, String> sent = NotificationSkill.sendNotification("Test");
        String id = sent.get("notification_id");

        Map<String, String> result = NotificationSkill.markAsRead(id);
        assertNotNull(result.get("message"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void resetNotificationSingleton() throws Exception {
        Field field = NotificationService.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, null);
    }
}
