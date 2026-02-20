package com.gazapps.util;

import com.gazapps.logging.LogService;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe circuit breaker (pure Java, no external library).
 *
 * <p>State machine:</p>
 * <pre>
 *   CLOSED ──(failures >= threshold)──► OPEN
 *   OPEN   ──(resetMs elapsed)       ──► HALF_OPEN
 *   HALF_OPEN ──(success)            ──► CLOSED
 *   HALF_OPEN ──(failure)            ──► OPEN
 * </pre>
 *
 * <p>Usage:</p>
 * <pre>
 *   if (cb.allowCall()) {
 *       try {
 *           result = doSomething();
 *           cb.recordSuccess();
 *       } catch (Exception e) {
 *           cb.recordFailure();
 *           throw e;
 *       }
 *   } else {
 *       // fast-fail / use fallback
 *   }
 * </pre>
 */
public class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private static final LogService LOG = LogService.getInstance();

    private final String name;
    private final int    failureThreshold;
    private final long   resetMs;

    private volatile State      state           = State.CLOSED;
    private final AtomicInteger failureCount    = new AtomicInteger(0);
    private final AtomicLong    lastFailureTime = new AtomicLong(0);

    /**
     * @param name             label used in log messages
     * @param failureThreshold consecutive failures needed to open the circuit
     * @param resetMs          milliseconds to wait in OPEN before trying HALF_OPEN
     */
    public CircuitBreaker(String name, int failureThreshold, long resetMs) {
        this.name             = name;
        this.failureThreshold = failureThreshold;
        this.resetMs          = resetMs;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the call should be attempted.
     * <ul>
     *   <li>CLOSED → always allow</li>
     *   <li>OPEN → deny unless {@code resetMs} has elapsed (then → HALF_OPEN, allow one probe)</li>
     *   <li>HALF_OPEN → allow one probe call</li>
     * </ul>
     */
    public synchronized boolean allowCall() {
        switch (state) {
            case CLOSED:
                return true;

            case OPEN:
                if (System.currentTimeMillis() - lastFailureTime.get() >= resetMs) {
                    state = State.HALF_OPEN;
                    LOG.info("CircuitBreaker", "[" + name + "] OPEN → HALF_OPEN (probe allowed)");
                    return true;
                }
                return false;

            case HALF_OPEN:
                // only one probe at a time; subsequent callers are denied until probe resolves
                return true;

            default:
                return false;
        }
    }

    /**
     * Records a successful call.
     * If the circuit was HALF_OPEN it transitions to CLOSED and resets the counter.
     */
    public synchronized void recordSuccess() {
        if (state == State.HALF_OPEN) {
            state = State.CLOSED;
            failureCount.set(0);
            LOG.info("CircuitBreaker", "[" + name + "] HALF_OPEN → CLOSED (probe succeeded)");
        } else if (state == State.CLOSED) {
            // reset failure streak on any success while closed
            failureCount.set(0);
        }
    }

    /**
     * Records a failed call.
     * Increments the failure counter; opens the circuit when the threshold is reached.
     * In HALF_OPEN, immediately re-opens.
     */
    public synchronized void recordFailure() {
        lastFailureTime.set(System.currentTimeMillis());
        int failures = failureCount.incrementAndGet();

        if (state == State.HALF_OPEN) {
            state = State.OPEN;
            LOG.warn("CircuitBreaker", "[" + name + "] HALF_OPEN → OPEN (probe failed)");
        } else if (state == State.CLOSED && failures >= failureThreshold) {
            state = State.OPEN;
            LOG.warn("CircuitBreaker", "[" + name + "] CLOSED → OPEN (failures=" + failures + ")");
        }
    }

    /** Returns the current state (for monitoring / tests). */
    public State getState() {
        return state;
    }

    /** Returns the current failure count (for monitoring / tests). */
    public int getFailureCount() {
        return failureCount.get();
    }

    @Override
    public String toString() {
        return "CircuitBreaker{name='" + name + "', state=" + state
                + ", failures=" + failureCount.get() + "}";
    }
}
