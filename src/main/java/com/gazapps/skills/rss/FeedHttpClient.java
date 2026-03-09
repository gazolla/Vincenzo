package com.gazapps.skills.rss;

import com.gazapps.config.AppConfig;
import com.gazapps.util.RetryUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Thin wrapper around {@link HttpClient} for RSS-related HTTP operations.
 *
 * <p>
 * Applies {@code User-Agent}, retry logic, and redirect following consistently
 * across all callers so that individual tool methods stay free of HTTP
 * boilerplate.
 */
public final class FeedHttpClient {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(AppConfig.getInstance().RSS_FETCH_TIMEOUT_SECONDS))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private FeedHttpClient() {
    }

    /**
     * Fetch the HTML body of a webpage as a String.
     *
     * @throws Exception on non-2xx responses or network errors
     */
    public static String fetchHtml(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", AppConfig.getInstance().USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,*/*")
                .GET()
                .build();

        return RetryUtils.withRetry(
                "FeedHttpClient.fetchHtml",
                AppConfig.getInstance().RETRY_MAX_ATTEMPTS,
                AppConfig.getInstance().RETRY_INITIAL_DELAY_MS,
                () -> {
                    HttpResponse<String> resp = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                        throw new java.io.IOException("HTTP " + resp.statusCode());
                    }
                    return resp.body();
                },
                body -> body == null || body.isBlank());
    }

    /**
     * Fetch the raw bytes of a feed URL.
     *
     * @throws Exception on non-2xx responses or network errors
     */
    public static byte[] fetchBytes(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", AppConfig.getInstance().USER_AGENT)
                .header("Accept", "application/rss+xml,application/atom+xml,application/xml,text/xml,*/*")
                .GET()
                .build();

        return RetryUtils.withRetry(
                "FeedHttpClient.fetchBytes",
                AppConfig.getInstance().RETRY_MAX_ATTEMPTS,
                AppConfig.getInstance().RETRY_INITIAL_DELAY_MS,
                () -> {
                    HttpResponse<byte[]> resp = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
                    if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                        throw new java.io.IOException("HTTP " + resp.statusCode());
                    }
                    return resp.body();
                },
                bytes -> bytes == null || bytes.length == 0);
    }

    /**
     * Issue a HEAD request and return {@code true} if the server responds with 200.
     * Used by {@code discoverFeed} to probe common feed paths.
     */
    public static boolean probe(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", AppConfig.getInstance().USER_AGENT)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> resp = HTTP.send(request, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
