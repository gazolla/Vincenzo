package com.gazapps.skills;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PdfSkill.
 *
 * <p>PdfSkill uses Java HttpClient (not Playwright) for downloading PDFs.
 * UrlValidator blocks loopback and private IPs, so a local mock server cannot be used.
 * Tests cover the UrlValidator-blocking paths (no network required) and document
 * what a full integration test would require.
 */
class PdfSkillTest {

    @Test
    void readPdf_privateIpUrl_returnsErrorMap() {
        Map<String, String> result = PdfSkill.readPdf("http://192.168.0.1/document.pdf");

        assertEquals("error", result.get("status"),
                "Private IP should be blocked by UrlValidator");
        assertNotNull(result.get("message"));
        assertTrue(result.get("message").contains("blocked"),
                "Error message should mention 'blocked'");
    }

    @Test
    void readPdf_loopbackUrl_returnsErrorMap() {
        Map<String, String> result = PdfSkill.readPdf("http://127.0.0.1/doc.pdf");

        assertEquals("error", result.get("status"));
        assertNotNull(result.get("message"));
    }

    @Test
    void readPdf_nullUrl_returnsErrorMap() {
        Map<String, String> result = PdfSkill.readPdf(null);

        assertEquals("error", result.get("status"),
                "Null URL should result in an error map");
        assertNotNull(result.get("message"));
    }

    @Test
    void readPdf_blankUrl_returnsErrorMap() {
        Map<String, String> result = PdfSkill.readPdf("   ");

        assertEquals("error", result.get("status"),
                "Blank URL should result in an error map");
    }

    @Test
    void readPdf_invalidScheme_returnsErrorMap() {
        Map<String, String> result = PdfSkill.readPdf("ftp://example.com/file.pdf");

        assertEquals("error", result.get("status"),
                "Non-HTTP scheme should be blocked by UrlValidator");
    }

    @Test
    @Disabled("Requires network access — run manually against a real public PDF URL")
    void readPdf_realPublicUrl_extractsText() {
        // Example: a small public PDF from a well-known source
        Map<String, String> result = PdfSkill.readPdf("https://www.w3.org/WAI/WCAG21/Techniques/pdf/PDF1.pdf");

        assertEquals("success", result.get("status"));
        assertNotNull(result.get("text_content"));
        assertFalse(result.get("text_content").isBlank());
        assertNotNull(result.get("page_count"));
        assertTrue(Integer.parseInt(result.get("page_count")) > 0);
    }
}
