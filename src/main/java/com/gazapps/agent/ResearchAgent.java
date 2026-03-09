package com.gazapps.agent;

import com.gazapps.config.AppConfig;
import com.gazapps.skills.WebContentSkill;
import com.gazapps.skills.WebOrchestrator;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.SequentialAgent;

/**
 * A {@link SequentialAgent} that performs multi-step web research autonomously.
 *
 * <p>
 * The pipeline executes three specialized {@link LlmAgent} sub-agents in order:
 * <ol>
 * <li><b>SearcherAgent</b> — uses {@code searchWeb} and
 * {@code fetchPageContent} to collect
 * raw content from multiple sources.</li>
 * <li><b>SynthesizerAgent</b> — reads the searcher's findings from session
 * history and
 * produces a structured report.</li>
 * <li><b>FactCheckerAgent</b> — reviews the report, verifies factual claims
 * against
 * source URLs, and finalizes the output.</li>
 * </ol>
 *
 * <p>
 * Sub-agents communicate via the shared session history: each agent's response
 * is visible to subsequent agents via the conversation context, following the
 * ADK SequentialAgent contract.
 *
 * <p>
 * This agent is not registered with the main {@link SearchAgent} runner;
 * instead
 * it is invoked by {@link com.gazapps.skills.ResearchSkill#deepResearch} with
 * its
 * own isolated {@link com.google.adk.runner.InMemoryRunner} and session.
 */
public final class ResearchAgent {

    private ResearchAgent() {
    }

    /** Builds and returns a new {@link SequentialAgent} for deep research. */
    public static SequentialAgent build() {
        return SequentialAgent.builder()
                .name("research-agent")
                .description("Multi-step research: search, synthesize, and fact-check sources.")
                .subAgents(
                        buildSearcherAgent(),
                        buildSynthesizerAgent(),
                        buildFactCheckerAgent())
                .build();
    }

    // ── Sub-agents ────────────────────────────────────────────────────────────

    /**
     * SearcherAgent — searches the web and reads the top relevant pages.
     * Has access to {@code searchWeb} and {@code fetchPageContent}.
     */
    private static LlmAgent buildSearcherAgent() {
        return LlmAgent.builder()
                .name("searcher")
                .description("Searches the web and reads relevant pages for the research query.")
                .model(AppConfig.getInstance().LLM_MODEL)
                .instruction("""
                        You are a web research assistant.
                        Your task:
                        1. Take the research query from the user message.
                        2. Use searchWeb to find the most relevant results.
                        3. Use fetchPageContent on the 3 most relevant URLs to get full content.
                        4. Present the collected findings as numbered facts with source URLs.
                           Include concrete data, dates, and figures when available.
                        Be thorough and factual. Do not synthesize yet — just collect and present raw findings.
                        Respond in the SAME LANGUAGE as the user's query.
                        """)
                .tools(
                        com.google.adk.tools.FunctionTool.create(WebOrchestrator.class, "searchWeb"),
                        com.google.adk.tools.FunctionTool.create(WebContentSkill.class, "fetchPageContent"))
                .build();
    }

    /**
     * SynthesizerAgent — reads SearcherAgent output from history and creates a
     * structured report.
     * No tools required — works entirely from conversation history.
     */
    private static LlmAgent buildSynthesizerAgent() {
        return LlmAgent.builder()
                .name("synthesizer")
                .description("Synthesizes raw research findings into a structured report.")
                .model(AppConfig.getInstance().LLM_MODEL)
                .instruction("""
                        You are a research synthesizer.
                        Review the research findings provided by the searcher agent in the conversation history.
                        Create a comprehensive structured report using these sections:

                        ## Contexto
                        (background and scope of the topic)

                        ## Dados e Fatos Principais
                        (key data points, figures, and verified facts with sources)

                        ## Análise
                        (interpretation of the data, trends, key players, implications)

                        ## Conclusão
                        (summary and takeaways)

                        Be objective, precise, and cite the source URL next to each major fact.
                        Respond in the SAME LANGUAGE as the original query.
                        """)
                .build();
    }

    /**
     * FactCheckerAgent — reviews the synthesizer's report, verifies claims, and
     * finalizes output.
     */
    private static LlmAgent buildFactCheckerAgent() {
        return LlmAgent.builder()
                .name("fact-checker")
                .description("Verifies factual claims in the report and finalizes with source attribution.")
                .model(AppConfig.getInstance().LLM_MODEL)
                .instruction("""
                        You are a fact-checker and editor.
                        Review the structured report from the synthesizer (visible in the conversation history).
                        For each major factual claim:
                          - If it has a source URL cited inline, keep it as-is.
                          - If it has no source, mark it as [sem verificação] and suggest where to verify.
                        Add a final section:

                        ## Fontes
                        (numbered list of all URLs cited in the report)

                        Return the COMPLETE final report with all sections, improved and fact-checked.
                        Respond in the SAME LANGUAGE as the original query.
                        """)
                .build();
    }
}
