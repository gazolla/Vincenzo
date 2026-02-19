package com.gazapps.skills;

import com.gazapps.config.AppConfig;
import com.gazapps.logging.LogService;
import com.gazapps.util.RetryUtils;
import com.gazapps.util.UrlValidator;
import com.google.adk.tools.Annotations.Schema;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * ADK tool that downloads a PDF from a URL and extracts its text content.
 * Uses Apache PDFBox 3.x for text extraction; no Playwright required.
 */
public class PdfSkill {

    private static final LogService LOG = LogService.getInstance();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(AppConfig.PDF_HTTP_TIMEOUT_SECONDS))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Schema(description = """
            Download a PDF from a URL and extract its full text content.
            Use this whenever the URL ends with .pdf or the content is known
            to be a PDF document (editais, contracts, reports, legislation, etc.).
            Do NOT use fetchPageContent for PDFs — use this tool instead.
            Returns the extracted text, page count, and character count.
            """)
    public static Map<String, String> readPdf(
            @Schema(name = "url", description = "The full URL of the PDF file to download and read") String url) {

        LOG.section("TOOL CALL: readPdf");
        LOG.info("PdfSkill", "URL: " + url);
        long start = System.currentTimeMillis();

        try {
            UrlValidator.validate(url);
        } catch (IllegalArgumentException e) {
            LOG.warn("PdfSkill", "Blocked unsafe URL: " + e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("status", "error");
            error.put("url", url);
            error.put("message", "URL blocked for security reasons: " + e.getMessage());
            return error;
        }

        try {
            // Build request once — HttpRequest is immutable and safe to reuse across retries
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", AppConfig.USER_AGENT)
                    .header("Accept", "application/pdf,*/*")
                    .GET()
                    .build();

            // Retry the HTTP download step only (PDFBox extraction is CPU-local, no retry needed)
            byte[] pdfBytes = RetryUtils.withRetry(
                    "PdfSkill.download",
                    AppConfig.RETRY_MAX_ATTEMPTS,
                    AppConfig.RETRY_INITIAL_DELAY_MS,
                    () -> {
                        HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
                        LOG.timing("PdfSkill", "HTTP download", System.currentTimeMillis() - start);
                        if (response.statusCode() != 200) {
                            LOG.warn("PdfSkill", "HTTP " + response.statusCode() + " for " + url);
                            throw new java.io.IOException("HTTP " + response.statusCode()); // → retry
                        }
                        return response.body();
                    },
                    bytes -> bytes == null || bytes.length == 0
            );
            LOG.info("PdfSkill", "Downloaded " + pdfBytes.length + " bytes");

            // Extract text using PDFBox 3.x API
            try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
                int pageCount = doc.getNumberOfPages();
                LOG.info("PdfSkill", "PDF pages: " + pageCount);

                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(doc);
                int rawLen = text.length();
                LOG.info("PdfSkill", "Extracted text length: " + rawLen + " chars");

                if (text.length() > AppConfig.PDF_MAX_CHARS) {
                    text = text.substring(0, AppConfig.PDF_MAX_CHARS) + "... [content truncated]";
                    LOG.debug("PdfSkill", "Text truncated to " + AppConfig.PDF_MAX_CHARS + " chars");
                }

                Map<String, String> result = new HashMap<>();
                result.put("status", "success");
                result.put("url", url);
                result.put("page_count", String.valueOf(pageCount));
                result.put("text_content", text);
                result.put("char_count", String.valueOf(rawLen));

                LOG.timing("PdfSkill", "Total execution", System.currentTimeMillis() - start);
                LOG.toolResult("readPdf", result);
                return result;
            }

        } catch (Exception e) {
            LOG.error("PdfSkill", "Failed for " + url, e);
            Map<String, String> error = new HashMap<>();
            error.put("status", "error");
            error.put("url", url);
            error.put("message", "PDF read failed: " + e.getMessage());
            return error;
        }
    }
}
