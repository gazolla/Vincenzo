package com.gazapps.skills;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testa o método package-private FormSkill.parseJsonFields().
 * IMPORTANTE: este teste deve permanecer no pacote com.gazapps.skills
 * para ter acesso ao método package-private.
 */
class FormSkillTest {

    // null → mapa vazio (não nulo, sem throw)
    @Test
    void parseJsonFields_null_returnsEmptyMap() {
        Map<String, String> result = FormSkill.parseJsonFields(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // blank → mapa vazio
    @Test
    void parseJsonFields_blank_returnsEmptyMap() {
        assertTrue(FormSkill.parseJsonFields("   ").isEmpty());
    }

    // string vazia → mapa vazio
    @Test
    void parseJsonFields_emptyString_returnsEmptyMap() {
        assertTrue(FormSkill.parseJsonFields("").isEmpty());
    }

    // JSON flat padrão → entradas corretas
    @Test
    void parseJsonFields_standardJson_returnsCorrectEntries() {
        Map<String, String> result = FormSkill.parseJsonFields(
                "{\"#name\": \"Alice\", \"#age\": \"30\"}");
        assertEquals(2, result.size());
        assertEquals("Alice", result.get("#name"));
        assertEquals("30", result.get("#age"));
    }

    // Vírgula dentro do valor → não quebra (o bug que o parser artesanal tinha)
    @Test
    void parseJsonFields_commaInsideValue_preserved() {
        Map<String, String> result = FormSkill.parseJsonFields(
                "{\"#city\": \"São Paulo, SP\"}");
        assertEquals(1, result.size());
        assertEquals("São Paulo, SP", result.get("#city"));
    }

    // JSON inválido → mapa vazio, sem throw
    @Test
    void parseJsonFields_malformedJson_returnsEmptyMapNoThrow() {
        assertDoesNotThrow(() -> {
            Map<String, String> result = FormSkill.parseJsonFields("{bad json");
            assertTrue(result.isEmpty());
        });
    }

    // Array JSON (não objeto) → mapa vazio, sem throw
    @Test
    void parseJsonFields_jsonArray_returnsEmptyMapNoThrow() {
        assertDoesNotThrow(() -> {
            Map<String, String> result = FormSkill.parseJsonFields("[\"a\",\"b\"]");
            assertTrue(result.isEmpty());
        });
    }

    // Ordem de inserção preservada (LinkedHashMap)
    @Test
    void parseJsonFields_preservesInsertionOrder() {
        Map<String, String> result = FormSkill.parseJsonFields(
                "{\"#first\": \"1\", \"#second\": \"2\", \"#third\": \"3\"}");
        String[] keys = result.keySet().toArray(new String[0]);
        assertArrayEquals(new String[]{"#first", "#second", "#third"}, keys);
    }

    // Valor numérico em string → convertido para string corretamente
    @Test
    void parseJsonFields_numericValueAsString_converted() {
        Map<String, String> result = FormSkill.parseJsonFields(
                "{\"#qty\": \"5\"}");
        assertEquals("5", result.get("#qty"));
    }

    // Chave com caracteres especiais (seletores CSS) → preservada
    @Test
    void parseJsonFields_specialCharsInKey_preserved() {
        Map<String, String> result = FormSkill.parseJsonFields(
                "{\"input[name=city]\": \"Brasília\"}");
        assertEquals("Brasília", result.get("input[name=city]"));
    }
}
