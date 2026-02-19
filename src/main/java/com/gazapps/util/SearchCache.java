package com.gazapps.util;

import com.gazapps.config.AppConfig;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheStats;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Cache em memória de resultados de busca, baseado em Guava Cache.
 *
 * <p>Chaves são queries normalizadas (lowercase, espaços colapsados).
 * O cache é configurável via {@code AppConfig}: tamanho máximo (LRU eviction),
 * TTL e flag de habilitação.
 *
 * <p>Guava ({@code com.google.guava:guava}) está disponível como dependência
 * transitiva do {@code google-adk} — nenhuma entrada adicional no pom.xml
 * é necessária.
 */
public final class SearchCache {

    private static final Cache<String, Map<String, String>> CACHE = buildCache();

    private SearchCache() {}

    /**
     * Constrói o cache com os parâmetros lidos do AppConfig.
     * Método separado do campo para facilitar testes que precisam de TTL customizado.
     */
    static Cache<String, Map<String, String>> buildCache() {
        return CacheBuilder.newBuilder()
                .maximumSize(AppConfig.SEARCH_CACHE_MAX_SIZE)
                .expireAfterWrite(AppConfig.SEARCH_CACHE_TTL_MINUTES, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    /**
     * Normaliza a query para uso como chave de cache.
     * Lowercase + trim + colapso de espaços múltiplos.
     * Nunca lança exceção.
     */
    public static String normalize(String query) {
        if (query == null) return "";
        return query.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    /**
     * Retorna o resultado cacheado para a query normalizada,
     * ou {@code null} se ausente ou expirado.
     */
    public static Map<String, String> get(String normalizedQuery) {
        return CACHE.getIfPresent(normalizedQuery);
    }

    /**
     * Armazena o resultado no cache com a query normalizada como chave.
     */
    public static void put(String normalizedQuery, Map<String, String> result) {
        CACHE.put(normalizedQuery, result);
    }

    /**
     * Remove uma entrada específica do cache (invalidação manual).
     */
    public static void invalidate(String normalizedQuery) {
        CACHE.invalidate(normalizedQuery);
    }

    /**
     * Remove todas as entradas do cache.
     */
    public static void clear() {
        CACHE.invalidateAll();
    }

    /**
     * Retorna uma string com as estatísticas do cache
     * (hits, misses, hitRate, tamanho atual).
     */
    public static String stats() {
        CacheStats s = CACHE.stats();
        return String.format("hits=%d misses=%d hitRate=%.1f%% size=%d",
                s.hitCount(), s.missCount(), s.hitRate() * 100, CACHE.size());
    }
}
