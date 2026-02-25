package com.gazapps.agent;

import com.gazapps.config.AppConfig;
import com.gazapps.skills.*;
import com.google.adk.agents.LlmAgent;
import com.google.adk.tools.FunctionTool;

import java.util.List;

/**
 * Factory for the 5 specialized sub-agents used by the Vincenzo root agent.
 *
 * <p>
 * Each sub-agent has:
 * <ul>
 * <li>A focused {@code description} — used by AutoFlow (ADK) to route requests
 * from the root agent via {@code transferToAgent}.</li>
 * <li>A focused {@code instruction} — only the rules relevant to its
 * domain.</li>
 * <li>{@code disallowTransferToParent=true} — the sub-agent responds directly
 * to the user after tool execution, skipping the extra LLM round-trip back
 * to root. This is the ADK-documented latency optimization for leaf
 * agents.</li>
 * <li>{@code disallowTransferToPeers=true} — prevents uncontrolled
 * cross-delegation
 * between siblings; all routing goes through root.</li>
 * </ul>
 *
 * <p>
 * <b>Tool sharing note:</b> {@code fetchPageContent} is registered on both
 * {@code WebAgent} and {@code ContentAgent}. This is intentional — the ADK rule
 * requires that {@code fillFormAndSubmit} be preceded by
 * {@code fetchPageContent}
 * to inspect the page first. Duplicating the FunctionTool is valid; each call
 * to
 * {@code FunctionTool.create()} produces an independent tool instance.
 */
public final class SubAgents {

        private SubAgents() {
        }

        /** Returns all 5 sub-agents in the preferred routing priority order. */
        public static List<LlmAgent> all() {
                return List.of(
                                webAgent(),
                                contentAgent(),
                                feedAgent(),
                                monitorAgent(),
                                memoryAgent());
        }

        // ── 1. WebAgent ───────────────────────────────────────────────────────────

        /**
         * Handles internet search, page fetching, screenshots and deep research.
         * This is the most frequently delegated sub-agent.
         */
        public static LlmAgent webAgent() {
                return LlmAgent.builder()
                                .name("WebAgent")
                                .description(
                                                "Searches the internet, fetches web page content, takes screenshots, and performs deep research reports.")
                                .model(AppConfig.LLM_MODEL)
                                .instruction(
                                                """
                                                                You are a web search specialist. Your ONLY job is to search the internet and return real, current results.

                                                                CRITICAL — NEVER answer from training knowledge:
                                                                You MUST call searchWeb BEFORE answering ANY question about:
                                                                - People (presidents, politicians, celebrities, athletes, executives)
                                                                - Current events, news, prices, weather, stock markets
                                                                - Sports scores, rankings, schedules
                                                                - Any fact that may have changed since your training data

                                                                It is %s. Your training data is outdated. Always search first.

                                                                MANDATORY workflow:
                                                                1. Call searchWeb(query) — ALWAYS, no exceptions for real-world topics.
                                                                2. Call fetchPageContent on 1-2 of the most relevant URLs for detail.
                                                                3. Answer with the real data found. Include source URLs.
                                                                4. Respond in the SAME LANGUAGE as the user.

                                                                Exception: skip searchWeb ONLY for pure math or fixed definitions (e.g. "what is sqrt(2)").

                                                                Use deepResearch(query) for comprehensive reports requiring multiple sources.
                                                                ALWAYS confirm with the user before calling deepResearch.
                                                                """
                                                                .formatted(java.time.LocalDate.now()))
                                .tools(
                                                FunctionTool.create(WebOrchestrator.class, "searchWeb"),
                                                FunctionTool.create(WebContentSkill.class, "fetchPageContent"),
                                                FunctionTool.create(WebContentSkill.class, "screenshotPage"),
                                                FunctionTool.create(ResearchSkill.class, "deepResearch"))
                                .disallowTransferToPeers(true)
                                .build();
        }

        // ── 2. ContentAgent ───────────────────────────────────────────────────────

        /**
         * Handles page summarization, structured data extraction, form filling and PDF
         * reading.
         * Includes fetchPageContent because fillFormAndSubmit requires prior page
         * inspection.
         */
        public static LlmAgent contentAgent() {
                return LlmAgent.builder()
                                .name("ContentAgent")
                                .description(
                                                "Summarizes web pages, extracts structured data, fills HTML forms, and reads PDF documents.")
                                .model(AppConfig.LLM_MODEL)
                                .instruction(
                                                """
                                                                You are a content extraction and processing specialist.

                                                                Rules:
                                                                1. Use summarizeUrl when the user asks to summarize or get key points of a URL or article.
                                                                2. Use extractStructuredData(url, selectors) to extract prices, tables, links or lists
                                                                   from pages with known structure. Use plain CSS selectors, e.g. {"title":".name","price":".price"}.
                                                                   To extract an HTML attribute, append |attrName, e.g. {"link":".title > a|href"}.
                                                                3. Use fillFormAndSubmit to search on sites requiring form interaction.
                                                                   IMPORTANT: Always call fetchPageContent on the target URL FIRST to inspect the page
                                                                   HTML and identify the correct CSS selectors. Never guess selectors.
                                                                4. If the URL ends with .pdf or content is clearly a PDF, use readPdf instead of fetchPageContent.
                                                                5. Respond in the SAME LANGUAGE the user writes in.
                                                                """)
                                .tools(
                                                FunctionTool.create(WebContentSkill.class, "fetchPageContent"), // required
                                                                                                                // for
                                                                                                                // fillForm
                                                                                                                // pre-inspection
                                                FunctionTool.create(SummarizeSkill.class, "summarizeUrl"),
                                                FunctionTool.create(ExtractSkill.class, "extractStructuredData"),
                                                FunctionTool.create(FormSkill.class, "fillFormAndSubmit"),
                                                FunctionTool.create(PdfSkill.class, "readPdf"))
                                .disallowTransferToParent(true)
                                .disallowTransferToPeers(true)
                                .build();
        }

        // ── 3. FeedAgent ─────────────────────────────────────────────────────────

        /**
         * Handles RSS/Atom feed discovery, reading and keyword search.
         */
        public static LlmAgent feedAgent() {
                return LlmAgent.builder()
                                .name("FeedAgent")
                                .description("Discovers, reads and searches RSS and Atom news feeds.")
                                .model(AppConfig.LLM_MODEL)
                                .instruction("""
                                                You are an RSS/Atom feed specialist.

                                                Rules:
                                                1. Use discoverFeed(url) when the user provides a website URL and wants to subscribe
                                                   to its news feed. It auto-detects the RSS/Atom feed link from the site HTML.
                                                2. Use readFeed(feedUrl, maxItems) to read the latest items from a feed.
                                                   Prefer this over fetchPageContent for news sites with feeds — it is faster and structured.
                                                3. Use searchInFeed(feedUrl, keyword) to find specific topics inside a feed.
                                                   Prefer this over readFeed when the user wants to find something specific.
                                                4. Respond in the SAME LANGUAGE the user writes in.
                                                """)
                                .tools(
                                                FunctionTool.create(RssSkill.class, "discoverFeed"),
                                                FunctionTool.create(RssSkill.class, "readFeed"),
                                                FunctionTool.create(RssSkill.class, "searchInFeed"))
                                .disallowTransferToParent(true)
                                .disallowTransferToPeers(true)
                                .build();
        }

        // ── 4. MonitorAgent ───────────────────────────────────────────────────────

        /**
         * Handles recurring keyword monitors, notifications and scheduling.
         */
        public static LlmAgent monitorAgent() {
                return LlmAgent.builder()
                                .name("MonitorAgent")
                                .description(
                                                "Sets up and manages recurring keyword monitors on web pages or feeds, and handles notifications.")
                                .model(AppConfig.LLM_MODEL)
                                .instruction(
                                                """
                                                                You are a monitoring and notification specialist.

                                                                Rules:
                                                                1. Use scheduleMonitor(url, keyword, intervalMinutes, description) to set up recurring monitors.
                                                                   ALWAYS confirm the URL, keyword and interval with the user before scheduling.
                                                                   Minimum interval is 5 minutes. Recommended: 60 min for news feeds.
                                                                2. Use listMonitors() to show all active monitor jobs, their last result and next run time.
                                                                3. Use cancelMonitor(jobId) to stop a monitor. Always call listMonitors() first to confirm jobId.
                                                                4. Use sendNotification(message) to proactively send a message or alert to the user.
                                                                5. Use listPendingNotifications() to show queued notifications not yet delivered (CLI mode).
                                                                6. Use markAsRead(notificationId) after the user acknowledges a queued notification.
                                                                7. Respond in the SAME LANGUAGE the user writes in.
                                                                """)
                                .tools(
                                                FunctionTool.create(SchedulerSkill.class, "scheduleMonitor"),
                                                FunctionTool.create(SchedulerSkill.class, "listMonitors"),
                                                FunctionTool.create(SchedulerSkill.class, "cancelMonitor"),
                                                FunctionTool.create(NotificationSkill.class, "sendNotification"),
                                                FunctionTool.create(NotificationSkill.class,
                                                                "listPendingNotifications"),
                                                FunctionTool.create(NotificationSkill.class, "markAsRead"))
                                .disallowTransferToParent(true)
                                .disallowTransferToPeers(true)
                                .build();
        }

        // ── 5. MemoryAgent ────────────────────────────────────────────────────────

        /**
         * Handles persistent memory: save, retrieve, list, update, delete.
         */
        public static LlmAgent memoryAgent() {
                return LlmAgent.builder()
                                .name("MemoryAgent")
                                .description(
                                                "Saves, retrieves, updates and deletes persistent memories, preferences, notes and task context.")
                                .model(AppConfig.LLM_MODEL)
                                .instruction("""
                                                You are a persistent memory specialist.

                                                Rules:
                                                1. Use saveMemory(content, tags, category) to store facts, preferences, notes, research
                                                   findings or task context explicitly requested by the user, or judged important to retain.
                                                   Always confirm with the user what to save.
                                                   category must be one of: 'preference', 'note', 'research', 'task'.
                                                2. Use retrieveMemory(query, tags) to search stored memories. Call this at the START
                                                   of any turn where the user references past context or preferences.
                                                   Do NOT answer from session history alone — check memory first.
                                                3. Use listMemories(tag="") to show all memories; use tag filters for specific categories.
                                                4. Use deleteMemory(id) only after confirming with the user. Always call listMemories()
                                                   or retrieveMemory() first to verify the id.
                                                5. Use updateMemory(id, content, tags) to amend existing memories. Always call
                                                   retrieveMemory first to find the id, then confirm the update with the user.
                                                6. Respond in the SAME LANGUAGE the user writes in.
                                                """)
                                .tools(
                                                FunctionTool.create(MemorySkill.class, "saveMemory"),
                                                FunctionTool.create(MemorySkill.class, "retrieveMemory"),
                                                FunctionTool.create(MemorySkill.class, "listMemories"),
                                                FunctionTool.create(MemorySkill.class, "deleteMemory"),
                                                FunctionTool.create(MemorySkill.class, "updateMemory"))
                                .disallowTransferToParent(true)
                                .disallowTransferToPeers(true)
                                .build();
        }
}
