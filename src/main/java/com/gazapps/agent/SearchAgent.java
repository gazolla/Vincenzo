package com.gazapps.agent;

import com.gazapps.config.AppConfig;
import com.gazapps.logging.LogService;
import com.gazapps.skills.WebOrchestrator;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;

/**
 * ADK-based agent that wraps all skill tools.
 * All ADK events (tool calls, model responses) are logged in detail.
 */
public class SearchAgent {

    private static final String USER_ID = "local-user";

    private static final LogService LOG = LogService.getInstance();

    private final InMemoryRunner runner;
    private final Session session;
    private final RunConfig runConfig;

    public SearchAgent() {
        LOG.section("AGENT INIT");
        LOG.info("SearchAgent", "Building LlmAgent with model: " + AppConfig.LLM_MODEL);

        BaseAgent agent = buildAgent();
        this.runner = new InMemoryRunner(agent);
        this.session = runner.sessionService()
                .createSession(runner.appName(), USER_ID)
                .blockingGet();
        this.runConfig = RunConfig.builder().build();

        LOG.info("SearchAgent", "Session created — id: " + session.id()
                + "  userId: " + session.userId()
                + "  app: " + runner.appName());
    }

    private static BaseAgent buildAgent() {
        return LlmAgent.builder()
                .name("internet-search-assistant")
                .description("A helpful assistant that can search the internet and read web pages")
                .model(AppConfig.LLM_MODEL)
                .instruction(
                        """
                                You are a helpful AI assistant called Vincenzo with internet access powered by a real web browser.
                                Today's date and time: %s

                                You have access to the following tools:
                                - searchWeb(query): searches DuckDuckGo/Bing and returns real results (titles, URLs, snippets)
                                - fetchPageContent(url): opens and reads the full text of any web page
                                - screenshotPage(url, filename): takes a screenshot of a web page
                                - summarizeUrl(url): fetches and cleans a page's content for summarization
                                - extractStructuredData(url, selectors): extracts structured JSON data from a page using CSS selectors
                                - fillFormAndSubmit(url, fields, submitSelector): fills HTML form fields and submits the form
                                - readPdf(url): downloads and extracts text from a PDF file

                                MANDATORY rules - follow these without exception:
                                1. ALWAYS use searchWeb for ANY request involving: prices, flights, tickets, hotels, news, weather,
                                   exchange rates, sports scores, product availability, events, schedules, or ANY real-world data
                                   that changes over time. DO NOT answer from memory for these topics.
                                2. After getting search results, use fetchPageContent on 1-2 of the most relevant URLs to get
                                   detailed and accurate information before answering.
                                3. Always include the source URLs in your answer.
                                4. Respond in the SAME LANGUAGE the user writes in (if Portuguese, answer in Portuguese).
                                5. Only skip searching for pure math, definitions, or stable facts that cannot change.
                                6. If the URL ends with .pdf or the content is clearly a PDF document, use readPdf instead of fetchPageContent.
                                7. To extract prices, tables, or lists from a page with known structure, prefer extractStructuredData.
                                8. Use summarizeUrl when the user asks to summarize or get the key points of a specific URL or article.
                                9. Use fillFormAndSubmit to search on sites that require form interaction and have no public API.
                                   IMPORTANT: Before calling fillFormAndSubmit, you MUST first call fetchPageContent on the target URL
                                   to inspect the page HTML and identify the correct CSS selectors for the form fields and submit button.
                                   Never guess selectors — always inspect the page source first.

                                Example: if asked about flight prices, hotel rates, or anything commercial → searchWeb immediately.
                                Example for fillFormAndSubmit: fetchPageContent(url) first → identify selectors → then fillFormAndSubmit(url, fields, submitSelector).
                                """
                                .formatted(java.time.LocalDateTime.now()
                                        .format(java.time.format.DateTimeFormatter
                                                .ofPattern("dd/MM/yyyy HH:mm"))))
                .tools(
                        com.google.adk.tools.FunctionTool.create(WebOrchestrator.class, "searchWeb"),
                        com.google.adk.tools.FunctionTool.create(com.gazapps.skills.WebContentSkill.class,
                                "fetchPageContent"),
                        com.google.adk.tools.FunctionTool.create(com.gazapps.skills.WebContentSkill.class,
                                "screenshotPage"),
                        com.google.adk.tools.FunctionTool.create(com.gazapps.skills.SummarizeSkill.class,
                                "summarizeUrl"),
                        com.google.adk.tools.FunctionTool.create(com.gazapps.skills.ExtractSkill.class,
                                "extractStructuredData"),
                        com.google.adk.tools.FunctionTool.create(com.gazapps.skills.FormSkill.class,
                                "fillFormAndSubmit"),
                        com.google.adk.tools.FunctionTool.create(com.gazapps.skills.PdfSkill.class,
                                "readPdf"))
                .build();
    }

    /**
     * Sends a user message to the agent and returns its final text response.
     * Every ADK event emitted during the turn is logged.
     */
    public String processQuery(String userMessage) {
        LOG.section("AGENT TURN");
        LOG.info("SearchAgent", "Processing query: \"" + userMessage + "\"");

        long start = System.currentTimeMillis();
        Content userContent = Content.fromParts(Part.fromText(userMessage));

        Flowable<Event> events = runner.runAsync(
                session.userId(),
                session.id(),
                userContent,
                runConfig);

        StringBuilder response = new StringBuilder();
        final int[] eventIdx = { 0 };

        events.blockingForEach(event -> {
            int idx = ++eventIdx[0];
            boolean isFinal = event.finalResponse();
            String content = safeStringify(event);
            String typeHint = isFinal ? "FINAL_RESPONSE" : detectEventType(event, content);

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

        LOG.timing("SearchAgent", "processQuery total", System.currentTimeMillis() - start);
        LOG.info("SearchAgent", "Total ADK events received: " + eventIdx[0]);

        String result = response.toString().trim();
        if (result.isEmpty()) {
            LOG.warn("SearchAgent", "No final response content — returning fallback message");
            return "I could not generate a response. Please try again.";
        }
        return result;
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

    /** Best-effort heuristic to label an intermediate event type for the log. */
    private String detectEventType(Event event, String content) {
        if (content == null)
            return "EMPTY";
        String lower = content.toLowerCase();
        if (lower.contains("functioncall") || lower.contains("function_call"))
            return "TOOL_CALL";
        if (lower.contains("functionresponse") || lower.contains("function_response"))
            return "TOOL_RESPONSE";
        if (lower.length() > 10)
            return "MODEL_CHUNK";
        return "OTHER";
    }
}
