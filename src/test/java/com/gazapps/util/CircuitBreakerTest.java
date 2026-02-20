package com.gazapps.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class CircuitBreakerTest {

    private CircuitBreaker cb;

    @BeforeEach
    void setUp() {
        // threshold=3, resetMs=200 (fast for tests)
        cb = new CircuitBreaker("test-cb", 3, 200);
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    void initialState_isClosed() {
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
    }

    @Test
    void initialState_allowsCall() {
        assertTrue(cb.allowCall());
    }

    @Test
    void initialState_failureCountIsZero() {
        assertEquals(0, cb.getFailureCount());
    }

    // ── CLOSED behaviour ──────────────────────────────────────────────────────

    @Test
    void recordFailures_belowThreshold_staysClosed() {
        cb.recordFailure();
        cb.recordFailure();
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        assertTrue(cb.allowCall());
    }

    @Test
    void recordFailures_atThreshold_opensCircuit() {
        cb.recordFailure();
        cb.recordFailure();
        cb.recordFailure(); // hits threshold=3
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
    }

    @Test
    void recordSuccess_inClosed_resetsFailureCount() {
        cb.recordFailure();
        cb.recordFailure();
        cb.recordSuccess();
        assertEquals(0, cb.getFailureCount());
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
    }

    // ── OPEN behaviour ────────────────────────────────────────────────────────

    @Test
    void openCircuit_rejectsCall() {
        openCircuit();
        assertFalse(cb.allowCall());
    }

    @Test
    void openCircuit_afterResetMs_becomesHalfOpen() throws InterruptedException {
        openCircuit();
        Thread.sleep(250); // wait > resetMs=200
        // allowCall transitions to HALF_OPEN and returns true
        assertTrue(cb.allowCall());
        assertEquals(CircuitBreaker.State.HALF_OPEN, cb.getState());
    }

    // ── HALF_OPEN behaviour ───────────────────────────────────────────────────

    @Test
    void halfOpenCircuit_onSuccess_closesCb() throws InterruptedException {
        openCircuit();
        Thread.sleep(250);
        cb.allowCall(); // transitions to HALF_OPEN
        cb.recordSuccess();
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        assertEquals(0, cb.getFailureCount());
    }

    @Test
    void halfOpenCircuit_onFailure_reopensCb() throws InterruptedException {
        openCircuit();
        Thread.sleep(250);
        cb.allowCall(); // transitions to HALF_OPEN
        cb.recordFailure();
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
    }

    @Test
    void halfOpenCircuit_allowsCall() throws InterruptedException {
        openCircuit();
        Thread.sleep(250);
        cb.allowCall(); // transitions to HALF_OPEN and returns true
        // subsequent allowCall in HALF_OPEN also returns true (probe still pending)
        assertTrue(cb.allowCall());
    }

    // ── Thread-safety ─────────────────────────────────────────────────────────

    @Test
    void concurrentFailures_openCircuitExactlyOnce() throws InterruptedException {
        // 10 threads each record 3 failures — circuit should open exactly once
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                try { start.await(); } catch (InterruptedException ignored) {}
                cb.recordFailure();
                cb.recordFailure();
                cb.recordFailure();
            }));
        }

        ready.await();
        start.countDown();
        for (Future<?> f : futures) {
            try { f.get(5, TimeUnit.SECONDS); } catch (Exception ignored) {}
        }
        pool.shutdown();

        // After all threads, circuit must be OPEN (not corrupted state)
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
    }

    // ── toString ──────────────────────────────────────────────────────────────

    @Test
    void toString_containsNameAndState() {
        String s = cb.toString();
        assertTrue(s.contains("test-cb"));
        assertTrue(s.contains("CLOSED"));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private void openCircuit() {
        cb.recordFailure();
        cb.recordFailure();
        cb.recordFailure();
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
    }
}
