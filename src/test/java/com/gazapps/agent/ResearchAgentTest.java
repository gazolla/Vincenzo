package com.gazapps.agent;

import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.SequentialAgent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ResearchAgent}.
 *
 * <p>
 * These tests verify the structure and configuration of the SequentialAgent
 * without making any LLM API calls or network requests.
 */
class ResearchAgentTest {

    @Test
    void build_returnsSequentialAgent() {
        SequentialAgent agent = ResearchAgent.build();
        assertNotNull(agent);
    }

    @Test
    void build_hasCorrectName() {
        SequentialAgent agent = ResearchAgent.build();
        assertEquals("research-agent", agent.name());
    }

    @Test
    void build_hasThreeSubAgents() {
        SequentialAgent agent = ResearchAgent.build();
        List<? extends com.google.adk.agents.BaseAgent> subAgents = agent.subAgents();
        assertNotNull(subAgents);
        assertEquals(3, subAgents.size(),
                "ResearchAgent must have exactly 3 sub-agents: searcher, synthesizer, fact-checker");
    }

    @Test
    void build_subAgentsAreOrderedCorrectly() {
        SequentialAgent agent = ResearchAgent.build();
        List<? extends com.google.adk.agents.BaseAgent> subAgents = agent.subAgents();

        assertEquals("searcher", subAgents.get(0).name(), "First sub-agent must be 'searcher'");
        assertEquals("synthesizer", subAgents.get(1).name(), "Second sub-agent must be 'synthesizer'");
        assertEquals("fact-checker", subAgents.get(2).name(), "Third sub-agent must be 'fact-checker'");
    }

    @Test
    void build_subAgentsAreLlmAgents() {
        SequentialAgent agent = ResearchAgent.build();
        for (var subAgent : agent.subAgents()) {
            assertInstanceOf(LlmAgent.class, subAgent,
                    "Sub-agent '" + subAgent.name() + "' must be an LlmAgent");
        }
    }

    @Test
    void build_searcherHasSearchTools() {
        SequentialAgent agent = ResearchAgent.build();
        LlmAgent searcher = (LlmAgent) agent.subAgents().get(0);

        // Searcher must have tools; synthesizer and fact-checker typically have none
        // In ADK 0.6.0, tools() returns Single<List<BaseTool>> — use blockingGet() to unwrap.
        assertNotNull(searcher.tools());
        assertFalse(searcher.tools().blockingGet().isEmpty(),
                "SearcherAgent must have at least searchWeb and fetchPageContent tools");
    }

    @Test
    void build_calledTwice_returnsDifferentInstances() {
        // build() must be idempotent — each call returns a fresh instance
        SequentialAgent first = ResearchAgent.build();
        SequentialAgent second = ResearchAgent.build();
        assertNotSame(first, second,
                "build() should return a new SequentialAgent instance on each call");
    }
}
