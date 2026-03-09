package com.gazapps.agent;

import com.gazapps.logging.LogService;
import com.google.adk.agents.Callbacks;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;

import java.util.Optional;

/**
 * Factory for ADK 0.8 structured lifecycle callbacks attached to every agent in the
 * Vincenzo hierarchy (root + all 5 sub-agents).
 *
 * <p>All callbacks are pure observers — they always return {@link Optional#empty()} and
 * never intercept or override ADK's normal execution flow. The only side-effect is
 * structured log output via {@link LogService}.
 *
 * <p>Using the {@code Sync} callback variants (returning plain {@link Optional} instead
 * of RxJava {@code Maybe}) avoids unnecessary reactive overhead for simple logging.
 *
 * <p>Coverage:
 * <ul>
 *   <li>{@link #beforeTool()} — logs tool name, agent, userId and args before execution</li>
 *   <li>{@link #afterTool()} — logs tool result preview after execution</li>
 *   <li>{@link #onToolError()} — logs tool exceptions at ERROR level</li>
 *   <li>{@link #beforeModel()} — logs each LLM call with agent, userId and invocationId</li>
 *   <li>{@link #afterModel()} — logs prompt/output/total token counts from usageMetadata</li>
 *   <li>{@link #onModelError()} — logs LLM errors at ERROR level</li>
 * </ul>
 */
public final class AgentObserver {

    private static final LogService LOG = LogService.getInstance();
    private static final String TAG = "AgentObserver";

    private AgentObserver() {}

    // ── Tool callbacks ────────────────────────────────────────────────────────

    /**
     * Fires before each tool execution.
     * Logs: tool name, agent name, userId, args preview.
     */
    public static Callbacks.BeforeToolCallbackSync beforeTool() {
        return (ctx, tool, args, toolCtx) -> {
            LOG.info(TAG, String.format("TOOL ▶  %-25s  agent=%-15s  userId=%s  args=%s",
                    tool.name(),
                    ctx.agent().name(),
                    ctx.userId(),
                    preview(args)));
            return Optional.empty();
        };
    }

    /**
     * Fires after each successful tool execution.
     * Logs: tool name and result preview (first 120 chars).
     */
    public static Callbacks.AfterToolCallbackSync afterTool() {
        return (ctx, tool, args, toolCtx, result) -> {
            LOG.debug(TAG, String.format("TOOL ✓  %-25s  result=%s",
                    tool.name(),
                    preview(result)));
            return Optional.empty();
        };
    }

    /**
     * Fires when a tool throws an exception.
     * Logs: tool name, agent name and full exception at ERROR level.
     * Returns {@link Optional#empty()} so ADK re-throws the original exception.
     */
    public static Callbacks.OnToolErrorCallbackSync onToolError() {
        return (ctx, tool, args, toolCtx, error) -> {
            LOG.error(TAG, String.format("TOOL ✗  %-25s  agent=%-15s  error=%s",
                    tool.name(),
                    ctx.agent().name(),
                    error.getMessage()),
                    error);
            return Optional.empty();
        };
    }

    // ── Model callbacks ───────────────────────────────────────────────────────

    /**
     * Fires before each LLM call.
     * Logs: agent name, userId and invocationId.
     */
    public static Callbacks.BeforeModelCallbackSync beforeModel() {
        return (ctx, requestBuilder) -> {
            LOG.debug(TAG, String.format("LLM  ▶  agent=%-15s  userId=%s  invocationId=%s",
                    ctx.agentName(), ctx.userId(), ctx.invocationId()));
            return Optional.empty();
        };
    }

    /**
     * Fires after each successful LLM response.
     * Logs prompt / output / total token counts when usageMetadata is present.
     * Token counts are omitted silently when the model does not return them.
     */
    public static Callbacks.AfterModelCallbackSync afterModel() {
        return (ctx, response) -> {
            response.usageMetadata().ifPresent(usage ->
                    LOG.info(TAG, String.format(
                            "LLM  ✓  agent=%-15s  prompt=%5d  output=%5d  total=%5d tokens",
                            ctx.agentName(),
                            usage.promptTokenCount().orElse(0),
                            usage.candidatesTokenCount().orElse(0),
                            usage.totalTokenCount().orElse(0))));
            return Optional.empty();
        };
    }

    /**
     * Fires when the LLM call raises an exception.
     * Logs: agent name, userId and full exception at ERROR level.
     * Returns {@link Optional#empty()} so ADK propagates the original error.
     */
    public static Callbacks.OnModelErrorCallbackSync onModelError() {
        return (ctx, request, error) -> {
            LOG.error(TAG, String.format("LLM  ✗  agent=%-15s  userId=%s  error=%s",
                    ctx.agentName(), ctx.userId(), error.getMessage()),
                    error);
            return Optional.empty();
        };
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /** Truncates any object's toString() to 120 characters for log readability. */
    private static String preview(Object obj) {
        if (obj == null) return "null";
        String s = obj.toString();
        return s.length() > 120 ? s.substring(0, 120) + "…" : s;
    }
}
