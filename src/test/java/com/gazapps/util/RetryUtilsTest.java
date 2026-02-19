package com.gazapps.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RetryUtilsTest {

    // Sucesso na primeira tentativa → action chamada 1x
    @Test
    void withRetry_successOnFirstAttempt_callsActionOnce() throws Exception {
        AtomicInteger calls = new AtomicInteger(0);
        String result = RetryUtils.withRetry(
                "test", 3, 1L,
                () -> { calls.incrementAndGet(); return "ok"; },
                r -> false);
        assertEquals("ok", result);
        assertEquals(1, calls.get());
    }

    // isFailure=true na primeira, sucesso na segunda → action chamada 2x
    @Test
    void withRetry_failureThenSuccess_callsActionTwice() throws Exception {
        AtomicInteger calls = new AtomicInteger(0);
        String result = RetryUtils.withRetry(
                "test", 3, 1L,
                () -> calls.incrementAndGet() == 1 ? "fail" : "ok",
                r -> "fail".equals(r));
        assertEquals("ok", result);
        assertEquals(2, calls.get());
    }

    // isFailure=true em todas as tentativas → retorna último resultado sem throw
    @Test
    void withRetry_allAttemptsFailViaIsFailure_returnsLastResult() throws Exception {
        String result = RetryUtils.withRetry(
                "test", 3, 1L,
                () -> "bad",
                r -> true);
        assertEquals("bad", result);
    }

    // action lança em todas as tentativas → lança após maxAttempts
    @Test
    void withRetry_actionAlwaysThrows_throwsAfterAllAttempts() {
        AtomicInteger calls = new AtomicInteger(0);
        Exception ex = assertThrows(Exception.class, () ->
                RetryUtils.withRetry(
                        "test", 3, 1L,
                        () -> { calls.incrementAndGet(); throw new RuntimeException("boom"); },
                        r -> false));
        assertEquals("boom", ex.getMessage());
        assertEquals(3, calls.get());
    }

    // Lança 2x, sucesso na 3ª → retorna resultado sem throw
    @Test
    void withRetry_throwsTwiceThenSucceeds_returnsSuccess() throws Exception {
        AtomicInteger calls = new AtomicInteger(0);
        String result = RetryUtils.withRetry(
                "test", 3, 1L,
                () -> {
                    int n = calls.incrementAndGet();
                    if (n < 3) throw new RuntimeException("transient");
                    return "recovered";
                },
                r -> false);
        assertEquals("recovered", result);
        assertEquals(3, calls.get());
    }

    // maxAttempts=1 → sem retry, lança imediatamente se action lança
    @Test
    void withRetry_maxAttemptsOne_throwsImmediately() {
        AtomicInteger calls = new AtomicInteger(0);
        assertThrows(RuntimeException.class, () ->
                RetryUtils.withRetry(
                        "test", 1, 1L,
                        () -> { calls.incrementAndGet(); throw new RuntimeException("x"); },
                        r -> false));
        assertEquals(1, calls.get());
    }

    // maxAttempts=1 com isFailure=true → retorna resultado de falha (sem retry)
    @Test
    void withRetry_maxAttemptsOne_isFailure_returnsFailureResult() throws Exception {
        String result = RetryUtils.withRetry(
                "test", 1, 1L,
                () -> "failure",
                r -> true);
        assertEquals("failure", result);
    }

    // initialDelayMs=0 → completa sem travar
    @Test
    void withRetry_withZeroInitialDelay_completes() throws Exception {
        String result = RetryUtils.withRetry(
                "test", 2, 0L,
                () -> "done",
                r -> false);
        assertEquals("done", result);
    }

    // Checked exception propagada corretamente
    @Test
    void withRetry_checkedExceptionPropagated() {
        assertThrows(java.io.IOException.class, () ->
                RetryUtils.withRetry(
                        "test", 2, 1L,
                        () -> { throw new java.io.IOException("network"); },
                        r -> false));
    }
}
