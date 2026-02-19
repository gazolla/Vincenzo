package com.gazapps.util;

import java.util.function.Predicate;

/**
 * Generic retry helper with exponential back-off.
 * Pure Java — no dependency on Playwright, ADK, or any framework.
 */
public final class RetryUtils {

    private RetryUtils() {}

    /**
     * Functional interface equivalent to {@link java.util.function.Supplier} but
     * allowing checked exceptions — necessary for lambdas that call
     * {@code HTTP.send()} or other methods that declare checked exceptions.
     */
    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    /**
     * Executes {@code action} up to {@code maxAttempts} times with exponential
     * back-off between attempts.
     *
     * <p>Delay schedule: {@code initialDelayMs × 2^(attempt−1)}, i.e.
     * 500 ms → 1 000 ms → 2 000 ms for {@code initialDelayMs=500}.
     *
     * <p>The method returns as soon as {@code isFailure.test(result)} is
     * {@code false}. If every attempt either throws or returns a failure result,
     * the last exception is re-thrown (if any); otherwise the last failure result
     * is returned so the caller's existing error-map handling still works.
     *
     * @param context        label used in log output (e.g. "DuckDuckGo.fetchInstantAnswer")
     * @param maxAttempts    total attempts allowed (must be &ge; 1)
     * @param initialDelayMs delay before the second attempt in ms (doubles each time)
     * @param action         the operation to attempt; may throw any {@link Exception}
     * @param isFailure      returns {@code true} if the result should be treated as a
     *                       failure and trigger another attempt
     * @return the first non-failing result, or the last failure result if all
     *         attempts are exhausted without an exception
     * @throws Exception the last exception thrown by {@code action} if every
     *                   attempt ends with an exception
     */
    public static <T> T withRetry(
            String context,
            int maxAttempts,
            long initialDelayMs,
            ThrowingSupplier<T> action,
            Predicate<T> isFailure) throws Exception {

        Exception lastException = null;
        T lastResult = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                T result = action.get();
                if (!isFailure.test(result)) {
                    return result;   // success — exit immediately
                }
                lastResult = result;
                System.err.printf("[RetryUtils] [%s] tentativa %d/%d — resultado é falha%n",
                        context, attempt, maxAttempts);
            } catch (InterruptedException ie) {
                // Always restore the interrupt flag before re-throwing
                Thread.currentThread().interrupt();
                throw ie;
            } catch (Exception e) {
                lastException = e;
                System.err.printf("[RetryUtils] [%s] tentativa %d/%d — exceção: %s%n",
                        context, attempt, maxAttempts, e.getMessage());
            }

            if (attempt < maxAttempts) {
                long delay = initialDelayMs * (1L << (attempt - 1)); // 500, 1000, 2000 …
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw ie;
                }
            }
        }

        // All attempts exhausted
        if (lastException != null) throw lastException;
        return lastResult;  // return last failure-result for caller to handle
    }
}
