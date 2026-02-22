package com.gazapps.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ChatInterface#renderMarkdown(String)}.
 * Verifies that ANSI codes are injected correctly and Markdown
 * artefacts (stray asterisks, raw URLs, etc.) are cleaned up.
 *
 * <p>Tests check for the presence/absence of ANSI sequences and
 * for the expected plain text content — they do NOT assert on the
 * exact ANSI codes to stay resilient to cosmetic tweaks.
 */
class ChatInterfaceRenderTest {

    // ── Null / blank ──────────────────────────────────────────────────────────

    @Test
    void nullInput_returnsEmpty() {
        assertEquals("", ChatInterface.renderMarkdown(null));
    }

    @Test
    void blankInput_returnsEmpty() {
        assertEquals("", ChatInterface.renderMarkdown("   "));
    }

    // ── Plain text ────────────────────────────────────────────────────────────

    @Test
    void plainText_passedThrough() {
        String result = ChatInterface.renderMarkdown("Olá mundo");
        assertTrue(result.contains("Olá mundo"));
    }

    // ── Bold ──────────────────────────────────────────────────────────────────

    @Test
    void bold_doubleAsterisks_removedAndBoldApplied() {
        String result = ChatInterface.renderMarkdown("O **Alpha Centauri** é o mais próximo.");
        assertFalse(result.contains("**"), "Double asterisks should be removed");
        assertTrue(result.contains("Alpha Centauri"), "Bold text must remain");
        assertTrue(result.contains("\033[1m"), "ANSI bold code must be present");
    }

    @Test
    void bold_doubleUnderscores_removedAndBoldApplied() {
        String result = ChatInterface.renderMarkdown("Texto __importante__ aqui.");
        assertFalse(result.contains("__"));
        assertTrue(result.contains("importante"));
        assertTrue(result.contains("\033[1m"));
    }

    // ── Italic ────────────────────────────────────────────────────────────────

    @Test
    void italic_singleAsterisk_removedAndItalicApplied() {
        String result = ChatInterface.renderMarkdown("Texto *itálico* aqui.");
        assertFalse(result.contains("*itálico*"), "Italic Markdown should be removed");
        assertTrue(result.contains("itálico"));
        assertTrue(result.contains("\033[3m"), "ANSI italic code must be present");
    }

    // ── Inline code ───────────────────────────────────────────────────────────

    @Test
    void inlineCode_backticks_removed() {
        String result = ChatInterface.renderMarkdown("Use `System.out.println()` para imprimir.");
        assertFalse(result.contains("`"), "Backticks should be removed");
        assertTrue(result.contains("System.out.println()"), "Code text must remain");
    }

    // ── Links ─────────────────────────────────────────────────────────────────

    @Test
    void link_markdownFormat_textKept_urlDimmed() {
        String result = ChatInterface.renderMarkdown("[National Geographic](https://nationalgeographic.com)");
        assertFalse(result.contains("[National Geographic]"), "Link Markdown syntax removed");
        assertTrue(result.contains("National Geographic"), "Link text must remain");
        assertTrue(result.contains("https://nationalgeographic.com"), "URL must remain");
    }

    // ── Bullets ───────────────────────────────────────────────────────────────

    @Test
    void bullet_asterisk_convertedToBullet() {
        String result = ChatInterface.renderMarkdown("* Hubble\n* James Webb");
        assertFalse(result.startsWith("*"), "Raw * bullet marker should be gone");
        assertTrue(result.contains("•"), "Unicode bullet • must be present");
        assertTrue(result.contains("Hubble"));
        assertTrue(result.contains("James Webb"));
    }

    @Test
    void bullet_dash_convertedToBullet() {
        String result = ChatInterface.renderMarkdown("- Mercúrio\n- Vênus");
        assertTrue(result.contains("•"));
        assertTrue(result.contains("Mercúrio"));
    }

    @Test
    void bullet_nested_indented() {
        String result = ChatInterface.renderMarkdown("  * item aninhado");
        assertTrue(result.contains("•"));
        assertTrue(result.contains("item aninhado"));
    }

    // ── Numbered list ─────────────────────────────────────────────────────────

    @Test
    void numberedList_numberKept_contentFormatted() {
        String result = ChatInterface.renderMarkdown("1. Primeiro\n2. Segundo");
        assertTrue(result.contains("Primeiro"));
        assertTrue(result.contains("Segundo"));
    }

    // ── Horizontal rule ───────────────────────────────────────────────────────

    @Test
    void horizontalRule_dashes_replacedWithLine() {
        String result = ChatInterface.renderMarkdown("---");
        assertFalse(result.contains("---"), "Raw --- should be replaced");
        assertTrue(result.contains("─"), "Unicode box-drawing char must appear");
    }

    @Test
    void horizontalRule_asterisks_replacedWithLine() {
        String result = ChatInterface.renderMarkdown("***");
        assertFalse(result.contains("***"));
        assertTrue(result.contains("─"));
    }

    // ── Headers ───────────────────────────────────────────────────────────────

    @Test
    void header_h1_hashRemoved_textBold() {
        String result = ChatInterface.renderMarkdown("# Título Principal");
        assertFalse(result.startsWith("#"), "# marker must be removed");
        assertTrue(result.contains("TÍTULO PRINCIPAL"), "H1 text uppercased");
        assertTrue(result.contains("\033[1m"));
    }

    @Test
    void header_h2_hashRemoved() {
        String result = ChatInterface.renderMarkdown("## Subtítulo");
        assertFalse(result.contains("##"));
        assertTrue(result.contains("Subtítulo"));
    }

    @Test
    void header_h3_hashRemoved() {
        String result = ChatInterface.renderMarkdown("### Seção");
        assertFalse(result.contains("###"));
        assertTrue(result.contains("Seção"));
    }

    // ── Fenced code block ─────────────────────────────────────────────────────

    @Test
    void codeBlock_backtickFence_contentPreserved() {
        String result = ChatInterface.renderMarkdown("```java\nint x = 1;\n```");
        assertTrue(result.contains("int x = 1;"), "Code content must be preserved");
        assertFalse(result.contains("```"), "Backtick fences must be removed");
    }

    @Test
    void codeBlock_languageLabel_shown() {
        String result = ChatInterface.renderMarkdown("```python\nprint('oi')\n```");
        assertTrue(result.contains("python"), "Language label should appear in box header");
    }

    // ── Stray asterisks ───────────────────────────────────────────────────────

    @Test
    void strayAsterisks_inSources_removed() {
        // Simulates the "Fontes:\n*   [link](url)" pattern from real LLM output
        String input = "Fontes:\n*   [scienceaq.com](https://scienceaq.com)";
        String result = ChatInterface.renderMarkdown(input);
        assertFalse(result.contains("*   ["), "Stray asterisks before links must be removed");
        assertTrue(result.contains("scienceaq.com"), "URL text must remain");
    }

    // ── Real LLM response sample ──────────────────────────────────────────────

    @Test
    void realSample_telescopios_noRawAsterisks() {
        String sample = """
                Atualmente, alguns telescópios em funcionamento:
                *   **Telescópio Espacial Hubble (HST):** Continua sendo valioso.
                *   **Telescópio Espacial James Webb (JWST):** O maior já construído.
                Fontes:
                *   [olhardigital.com.br](https://olhardigital.com.br/hubble)
                """;

        String result = ChatInterface.renderMarkdown(sample);

        // No raw markdown asterisks should remain
        assertFalse(result.contains("**"), "No ** should remain");
        // Content preserved
        assertTrue(result.contains("Hubble"));
        assertTrue(result.contains("James Webb"));
        assertTrue(result.contains("olhardigital.com.br"));
    }

    @Test
    void realSample_alphaCentauri_boldRemoved() {
        String sample = "O **Alpha Centauri** é o mais próximo. A **Proxima Centauri** está a 4.246 anos-luz.";
        String result = ChatInterface.renderMarkdown(sample);

        assertFalse(result.contains("**"));
        assertTrue(result.contains("Alpha Centauri"));
        assertTrue(result.contains("Proxima Centauri"));
    }
}
