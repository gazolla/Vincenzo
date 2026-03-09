package com.gazapps.skills;

import com.gazapps.agent.ResearchAgent;
import com.gazapps.config.AppConfig;
import com.gazapps.logging.LogService;
import com.gazapps.util.SessionState;
import com.gazapps.util.SkillStatus;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ADK tool that delegates to {@link ResearchAgent} (a {@code SequentialAgent})
 * to perform autonomous, multi-step web research.
 *
 * <p>
 * Each call creates an isolated {@link InMemoryRunner} session so the research
 * pipeline does not interfere with the main Vincenzo conversation session.
 *
 * <p>
 * The final response collected from the {@code FactCheckerAgent} (last
 * sub-agent)
 * is returned as the {@code "report"} key in the result map.
 */
public class ResearchSkill {

    private static final LogService LOG = LogService.getInstance();
    private static final RunConfig RUN_CONFIG = RunConfig.builder()
            .setMaxLlmCalls(AppConfig.getInstance().LLM_MAX_CALLS_RESEARCH)
            .build();

    /**
     * Lazily-built singleton research runner shared across calls to avoid
     * re-building the SequentialAgent on each invocation.
     */
    private static final class Runner {
        static final InMemoryRunner INSTANCE;
        static {
            BaseAgent agent = ResearchAgent.build();
            INSTANCE = new InMemoryRunner(agent);
            LOG.info("ResearchSkill", "ResearchAgent runner initialized — app: " + INSTANCE.appName());
        }
    }

    private static final AtomicLong SESSION_COUNTER = new AtomicLong(0);

    // ─────────────────────────────────────────────────────────────────────────

    @Schema(description = """
            Performs deep, autonomous, multi-step web research on a complex topic.
            Use this when the user asks for a report, analysis, detailed comparison,
            or any task requiring multiple sources and structured synthesis.
            The research pipeline: search → read sources → synthesize → fact-check.
            DO NOT use for simple factual questions — use searchWeb for those.
            Returns a comprehensive structured report with verified sources.
            This tool takes longer than searchWeb (multiple LLM turns internally).
            Always confirm with the user before using this tool.
            """)
    public static Map<String, String> deepResearch(
            @Schema(name = "query", description = "The research topic or question to investigate in depth") String query,
            @Schema(name = "toolContext") ToolContext toolContext) {

        LOG.section("TOOL CALL: deepResearch");
        LOG.info("ResearchSkill", "Query: \"" + query + "\"");
        long start = System.currentTimeMillis();

        Session session = null;
        try {
            // Each deepResearch call gets its own isolated session
            String sessionUserId = "research-" + SESSION_COUNTER.incrementAndGet();
            session = Runner.INSTANCE.sessionService()
                    .createSession(Runner.INSTANCE.appName(), sessionUserId)
                    .blockingGet();

            LOG.info("ResearchSkill", "Research session created — id: " + session.id());

            Content userContent = Content.fromParts(Part.fromText(query));
            Flowable<Event> events = Runner.INSTANCE.runAsync(
                    session.userId(), session.id(), userContent, RUN_CONFIG);

            // Collect all final-response events; last one = FactCheckerAgent output
            StringBuilder lastReport = new StringBuilder();
            final int[] eventCount = { 0 };

            events.blockingForEach(event -> {
                eventCount[0]++;
                if (event.finalResponse()) {
                    try {
                        String content = event.stringifyContent();
                        if (content != null && !content.isBlank()) {
                            // Replace previous report — we want the last sub-agent's output
                            lastReport.setLength(0);
                            lastReport.append(content);
                        }
                    } catch (Exception e) {
                        LOG.warn("ResearchSkill", "Could not stringify event: " + e.getMessage());
                    }
                }
            });

            LOG.info("ResearchSkill", "Research complete — events: " + eventCount[0]);
            LOG.timing("ResearchSkill", "deepResearch total", System.currentTimeMillis() - start);

            String report = lastReport.toString().trim();
            if (report.isEmpty()) {
                LOG.warn("ResearchSkill", "No final report content produced");
                report = "Research completed but produced no structured output. Try rephrasing your query.";
            }

            // Share report in session.state for potential follow-up tools
            SessionState.put(toolContext, SessionState.KEY_LAST_FETCH_CONTENT, report);

            Map<String, String> result = new HashMap<>();
            result.put("status", SkillStatus.SUCCESS.value());
            result.put("query", query);
            result.put("report", report);
            result.put("events_processed", String.valueOf(eventCount[0]));

            LOG.toolResult("deepResearch", result);
            return result;

        } catch (Exception e) {
            LOG.error("ResearchSkill", "deepResearch failed for query: " + query, e);
            Map<String, String> error = new HashMap<>();
            error.put("status", SkillStatus.ERROR.value());
            error.put("query", query);
            error.put("message", "Deep research failed: " + e.getMessage());
            return error;
        } finally {
            if (session != null) {
                try {
                    Runner.INSTANCE.sessionService()
                            .deleteSession(Runner.INSTANCE.appName(), session.userId(), session.id())
                            .blockingAwait();
                    LOG.debug("ResearchSkill", "Research session deleted — id: " + session.id());
                } catch (Exception ex) {
                    LOG.warn("ResearchSkill", "Failed to delete research session: " + ex.getMessage());
                }
            }
        }
    }
}
