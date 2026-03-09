package com.gazapps.agent;

import com.gazapps.config.AppConfig;
import com.gazapps.logging.LogService;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.Instruction;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.adk.sessions.SessionKey;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

import java.util.concurrent.TimeUnit;

/**
 * ADK-based agent that wraps all skill tools.
 * All ADK events (tool calls, model responses) are logged in detail.
 */
public class SearchAgent {

        private static final String USER_ID = "local-user";

        private static final LogService LOG = LogService.getInstance();

        private final InMemoryRunner runner;
        private final RunConfig runConfig;

        /**
         * Maps userId → ADK {@link SessionKey} (appName + userId + sessionId bundle).
         * Bounded to 1 000 entries and evicted after 24 h of inactivity to prevent
         * unbounded growth in long-running Telegram deployments.
         */
        private final Cache<String, SessionKey> sessionKeys = CacheBuilder.newBuilder()
                        .maximumSize(1_000)
                        .expireAfterAccess(24, TimeUnit.HOURS)
                        .build();

        public SearchAgent() {
                LOG.section("AGENT INIT");
                LOG.info("SearchAgent", "Building LlmAgent with model: " + AppConfig.getInstance().LLM_MODEL);

                BaseAgent agent = buildAgent();
                this.runner = new InMemoryRunner(agent);
                this.runConfig = RunConfig.builder()
                                .setMaxLlmCalls(AppConfig.getInstance().LLM_MAX_CALLS_AGENT)
                                .build();

                LOG.info("SearchAgent", "Agent ready — multi-session mode (sessions created lazily per userId)");
        }

        /**
         * Returns (or lazily creates) the type-safe {@link SessionKey} for the given
         * userId. The key bundles appName, userId and sessionId together, preventing
         * positional-argument mix-ups when calling {@code runAsync} or
         * {@code deleteSession}.
         */
        private SessionKey sessionKeyFor(String userId) {
                SessionKey cached = sessionKeys.getIfPresent(userId);
                if (cached != null) return cached;

                Session s = runner.sessionService()
                                .createSession(runner.appName(), userId)
                                .blockingGet();
                SessionKey key = s.sessionKey();
                LOG.info("SearchAgent", "Session created — key: " + key);
                sessionKeys.put(userId, key);
                return key;
        }

        private static BaseAgent buildAgent() {
                // Root agent — pure routing, no direct tools.
                // AutoFlow: ADK injects a 'transferToAgent' tool for each sub-agent.
                // The root LLM decides which specialist to delegate to based on each
                // sub-agent's 'description'. Sub-agents respond directly to the user
                // (disallowTransferToParent=true) — no extra round-trip back to root.
                return LlmAgent.builder()
                                .name("vincenzo")
                                .description("Vincenzo — AI assistant with internet access, memory and monitoring capabilities.")
                                .model(AppConfig.getInstance().LLM_MODEL)
                                .instruction(new Instruction.Provider(ctx -> Single.just(
                                                """
                                                                You are Vincenzo, a helpful AI assistant with internet access.
                                                                Today's date and time: %s

                                                                You have 5 specialist agents. Delegate ALL tasks to the appropriate one:

                                                                - WebAgent: internet searches, fetching web pages, screenshots, deep research reports.
                                                                  Use for: any real-world data (news, prices, weather, flights, sports, etc.).

                                                                - ContentAgent: summarize URLs, extract structured data, fill HTML forms, read PDFs.
                                                                  Use for: summarization, data extraction, form submission, PDF reading.

                                                                - FeedAgent: RSS/Atom feeds — discover, read, search.
                                                                  Use for: anything involving news feeds or RSS subscriptions.

                                                                - MonitorAgent: recurring keyword monitors, scheduling, notifications.
                                                                  Use for: setting up alerts, managing monitors, sending/reading notifications.

                                                                - MemoryAgent: persistent memories — save, retrieve, list, update, delete.
                                                                  Use for: storing preferences, notes, research findings or reminders across sessions.

                                                                Rules:
                                                                1. ALWAYS delegate — never answer real-world or tool-based questions from your own knowledge.
                                                                2. Respond in the SAME LANGUAGE the user writes in.
                                                                3. For compound tasks (e.g. "search and save to memory"), delegate sequentially:
                                                                   first to WebAgent for search, then to MemoryAgent to save the result.
                                                                4. Only answer directly (without delegating) for: pure math, simple definitions,
                                                                   or factual questions that cannot possibly change (e.g. "what is 2+2").
                                                                """.formatted(java.time.LocalDateTime.now()
                                                                                .format(java.time.format.DateTimeFormatter
                                                                                                .ofPattern("dd/MM/yyyy HH:mm"))))))
                                .subAgents(
                                                SubAgents.webAgent(),
                                                SubAgents.contentAgent(),
                                                SubAgents.feedAgent(),
                                                SubAgents.monitorAgent(),
                                                SubAgents.memoryAgent())
                                .build();
        }

        /**
         * Sends a user message to the agent and returns the final text response.
         * Uses the session associated with {@code userId}; creates one lazily if
         * needed.
         *
         * @param userMessage the user's message
         * @param userId      user identifier (e.g. Telegram chat_id); use
         *                    {@link #USER_ID} for CLI
         */
        public String processQuery(String userMessage, String userId) {
                LOG.section("AGENT TURN");
                LOG.info("SearchAgent", "Processing query for userId=" + userId
                                + ": \"" + userMessage + "\"");

                long start = System.currentTimeMillis();
                SessionKey key = sessionKeyFor(userId);
                Content userContent = Content.fromParts(Part.fromText(userMessage));

                Flowable<Event> events = runner.runAsync(key, userContent, runConfig);

                StringBuilder response = new StringBuilder();
                final int[] eventIdx = { 0 };

                try {
                        events.blockingForEach(event -> {
                                int idx = ++eventIdx[0];
                                boolean isFinal = event.finalResponse();
                                String content = safeStringify(event);
                                String typeHint = isFinal ? "FINAL_RESPONSE" : detectEventType(event);

                                LOG.debug("SearchAgent", String.format(
                                                "Event #%d  type=%-22s final=%-5b  content_preview=%s",
                                                idx, typeHint, isFinal,
                                                content != null && content.length() > 120
                                                                ? content.substring(0, 120) + "..."
                                                                : content));

                                if (isFinal) {
                                        LOG.divider("FINAL RESPONSE EVENT #" + idx);
                                        LOG.info("SearchAgent", "Response text length: "
                                                        + (content != null ? content.length() : 0) + " chars");
                                        response.append(content);
                                }
                        });
                } catch (Exception e) {
                        LOG.error("SearchAgent", "Event stream failed for userId=" + userId, e);
                }

                LOG.timing("SearchAgent", "processQuery total", System.currentTimeMillis() - start);
                LOG.info("SearchAgent", "Total ADK events received: " + eventIdx[0]);

                String result = response.toString().trim();
                if (result.isEmpty()) {
                        LOG.warn("SearchAgent", "No final response content — returning fallback message");
                        return "I could not generate a response. Please try again.";
                }
                return result;
        }

        /**
         * Convenience overload for CLI mode — uses the default {@link #USER_ID}
         * session.
         */
        public String processQuery(String userMessage) {
                return processQuery(userMessage, USER_ID);
        }

        // ── Helpers ───────────────────────────────────────────────────────────────

        private String safeStringify(Event event) {
                try {
                        return event.stringifyContent();
                } catch (Exception e) {
                        LOG.warn("SearchAgent", "Could not stringify event: " + e.getMessage());
                        return "<unstringifiable>";
                }
        }

        /**
         * Labels an intermediate ADK event using the structured fields exposed by
         * {@link Event} in ADK 0.8 — no string serialisation or substring matching.
         *
         * <p>Priority order (an event can carry multiple signals simultaneously):
         * <ol>
         *   <li>Tool invocation — {@code functionCalls()} non-empty</li>
         *   <li>Tool result — {@code functionResponses()} non-empty</li>
         *   <li>Agent hand-off — {@code actions().transferToAgent()} present</li>
         *   <li>Agent finished — {@code actions().endOfAgent()}</li>
         *   <li>LLM error — {@code errorCode()} present</li>
         *   <li>Streaming chunk — {@code partial()} is {@code true}</li>
         *   <li>Model text — {@code content()} present (no tool payloads)</li>
         *   <li>Turn boundary — {@code turnComplete()} is {@code true}</li>
         *   <li>Unknown — none of the above</li>
         * </ol>
         */
        private String detectEventType(Event event) {
                if (!event.functionCalls().isEmpty())
                        return "TOOL_CALL";
                if (!event.functionResponses().isEmpty())
                        return "TOOL_RESPONSE";
                if (event.actions().transferToAgent().isPresent())
                        return "AGENT_TRANSFER";
                if (event.actions().endOfAgent())
                        return "END_OF_AGENT";
                if (event.errorCode().isPresent())
                        return "ERROR";
                if (event.partial().orElse(false))
                        return "MODEL_CHUNK";
                if (event.content().isPresent())
                        return "MODEL_TEXT";
                if (event.turnComplete().orElse(false))
                        return "TURN_COMPLETE";
                return "UNKNOWN";
        }
}
