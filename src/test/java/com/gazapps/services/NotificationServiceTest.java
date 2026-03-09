package com.gazapps.services;

import com.gazapps.config.AppConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link NotificationService}.
 *
 * <p>All tests operate in CLI mode (no Telegram bot injected), so messages
 * are always delivered to the in-memory queue. The singleton is reset between
 * tests via reflection to ensure isolation.
 */
class NotificationServiceTest {

    @BeforeEach
    void setUp() throws Exception {
        resetSingleton();
    }

    @AfterEach
    void tearDown() throws Exception {
        resetSingleton();
    }

    // ── send — no bot (CLI mode) ──────────────────────────────────────────────

    @Test
    void send_withNoBotSet_queuesNotification() {
        String id = NotificationService.getInstance().send("Hello world");
        assertTrue(id.startsWith("notif-"), "Should return a queue id starting with 'notif-'");
    }

    @Test
    void send_withNoBotSet_notificationAppearsInQueue() {
        NotificationService.getInstance().send("Test notification");

        List<NotificationService.Notification> pending =
                NotificationService.getInstance().listPending();
        assertEquals(1, pending.size());
        assertEquals("Test notification", pending.get(0).message);
    }

    @Test
    void send_multipleMessages_allQueued() {
        NotificationService svc = NotificationService.getInstance();
        svc.send("Message 1");
        svc.send("Message 2");
        svc.send("Message 3");

        assertEquals(3, svc.listPending().size());
    }

    @Test
    void send_incrementingIds_areUnique() {
        NotificationService svc = NotificationService.getInstance();
        String id1 = svc.send("First");
        String id2 = svc.send("Second");

        assertNotEquals(id1, id2);
        assertTrue(id1.startsWith("notif-"));
        assertTrue(id2.startsWith("notif-"));
    }

    // ── listPending ───────────────────────────────────────────────────────────

    @Test
    void listPending_empty_returnsEmptyList() {
        List<NotificationService.Notification> pending =
                NotificationService.getInstance().listPending();
        assertNotNull(pending);
        assertTrue(pending.isEmpty());
    }

    @Test
    void listPending_returnsSnapshot_notLiveReference() {
        NotificationService svc = NotificationService.getInstance();
        svc.send("First");

        List<NotificationService.Notification> snapshot = svc.listPending();

        svc.send("Second");

        // Snapshot should not reflect subsequent additions
        assertEquals(1, snapshot.size(), "Snapshot should reflect state at call time");
    }

    // ── Notification fields ───────────────────────────────────────────────────

    @Test
    void send_notificationHasTimestamp() {
        NotificationService.getInstance().send("Test");

        NotificationService.Notification n =
                NotificationService.getInstance().listPending().get(0);

        assertNotNull(n.timestamp);
        assertFalse(n.timestamp.isBlank());
    }

    @Test
    void send_notificationIsUnreadByDefault() {
        NotificationService.getInstance().send("Test");

        NotificationService.Notification n =
                NotificationService.getInstance().listPending().get(0);

        assertFalse(n.read, "New notification should be unread");
    }

    @Test
    void send_notificationHasCorrectMessage() {
        String msg = "This is a specific test message";
        NotificationService.getInstance().send(msg);

        NotificationService.Notification n =
                NotificationService.getInstance().listPending().get(0);

        assertEquals(msg, n.message);
    }

    // ── markRead ──────────────────────────────────────────────────────────────

    @Test
    void markRead_existingId_returnsTrue() {
        String id = NotificationService.getInstance().send("Test");

        boolean result = NotificationService.getInstance().markRead(id);

        assertTrue(result);
    }

    @Test
    void markRead_existingId_setsReadFlag() {
        String id = NotificationService.getInstance().send("Test");
        NotificationService.getInstance().markRead(id);

        NotificationService.Notification n =
                NotificationService.getInstance().listPending().get(0);

        assertTrue(n.read, "Notification should be marked as read");
    }

    @Test
    void markRead_unknownId_returnsFalse() {
        boolean result = NotificationService.getInstance().markRead("notif-999999");
        assertFalse(result);
    }

    @Test
    void markRead_nullId_returnsFalse() {
        boolean result = NotificationService.getInstance().markRead(null);
        assertFalse(result);
    }

    @Test
    void markRead_blankId_returnsFalse() {
        boolean result = NotificationService.getInstance().markRead("   ");
        assertFalse(result);
    }

    // ── Queue capacity / eviction ─────────────────────────────────────────────

    @Test
    void send_queueAtCapacity_evictsOldest() {
        // Fill the queue to its configured capacity (default 100) then add one more
        int maxSize = AppConfig.getInstance().NOTIFICATION_QUEUE_MAX_SIZE;
        NotificationService svc = NotificationService.getInstance();

        String firstId = svc.send("First message — should be evicted");

        // Fill the rest of the queue
        for (int i = 1; i < maxSize; i++) {
            svc.send("Message " + i);
        }

        // One more — should evict "First"
        String lastId = svc.send("Last message — should remain");

        List<NotificationService.Notification> pending = svc.listPending();

        assertEquals(maxSize, pending.size(),
                "Queue should not exceed max capacity");

        boolean containsFirst = pending.stream().anyMatch(n -> n.id.equals(firstId));
        assertFalse(containsFirst, "Oldest notification should have been evicted");

        boolean containsLast = pending.stream().anyMatch(n -> n.id.equals(lastId));
        assertTrue(containsLast, "Newest notification should be present");
    }

    // ── setBot — null safety ──────────────────────────────────────────────────

    @Test
    void setBot_null_doesNotThrow() {
        assertDoesNotThrow(() -> NotificationService.getInstance().setBot(null));
    }

    @Test
    void send_afterSetBotNull_stillQueues() {
        NotificationService svc = NotificationService.getInstance();
        svc.setBot(null);

        String id = svc.send("Test after null bot");
        assertTrue(id.startsWith("notif-"));
        assertEquals(1, svc.listPending().size());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void resetSingleton() throws Exception {
        Field field = NotificationService.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, null);
    }

}
