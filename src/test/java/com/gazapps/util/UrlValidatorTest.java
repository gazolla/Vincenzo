package com.gazapps.util;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UrlValidatorTest {

    // ── null / blank ─────────────────────────────────────────────────────────

    @Test
    void validate_null_throws() {
        assertThrows(IllegalArgumentException.class, () -> UrlValidator.validate(null));
    }

    @Test
    void validate_blank_throws() {
        assertThrows(IllegalArgumentException.class, () -> UrlValidator.validate("   "));
    }

    @Test
    void validate_empty_throws() {
        assertThrows(IllegalArgumentException.class, () -> UrlValidator.validate(""));
    }

    // ── malformed ─────────────────────────────────────────────────────────────

    @Test
    void validate_malformedUrl_throws() {
        // Spaces in host make URI.create throw
        assertThrows(IllegalArgumentException.class,
                () -> UrlValidator.validate("http://not a host/path"));
    }

    // ── scheme ────────────────────────────────────────────────────────────────

    @Test
    void validate_ftpScheme_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> UrlValidator.validate("ftp://example.com/file.txt"));
    }

    @Test
    void validate_fileScheme_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> UrlValidator.validate("file:///etc/passwd"));
    }

    @Test
    void validate_noScheme_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> UrlValidator.validate("example.com/path"));
    }

    // ── loopback (no DNS needed — literal IPs) ────────────────────────────────

    @Test
    void validate_127_0_0_1_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> UrlValidator.validate("http://127.0.0.1/"));
    }

    @Test
    void validate_127_1_2_3_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> UrlValidator.validate("http://127.1.2.3/"));
    }

    // ── RFC 1918 private ranges ────────────────────────────────────────────────

    @Test
    void validate_private_10_0_0_1_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> UrlValidator.validate("http://10.0.0.1/"));
    }

    @Test
    void validate_private_172_16_0_1_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> UrlValidator.validate("http://172.16.0.1/"));
    }

    @Test
    void validate_private_172_31_255_255_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> UrlValidator.validate("http://172.31.255.255/"));
    }

    @Test
    void validate_private_192_168_1_1_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> UrlValidator.validate("http://192.168.1.1/"));
    }

    // ── link-local ────────────────────────────────────────────────────────────

    @Test
    void validate_linkLocal_169_254_0_1_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> UrlValidator.validate("http://169.254.0.1/"));
    }

    // ── RFC 6598 Shared Address Space (100.64–127.x) ─────────────────────────

    @Test
    void validate_rfc6598_100_64_0_1_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> UrlValidator.validate("http://100.64.0.1/"));
    }

    @Test
    void validate_rfc6598_100_127_0_1_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> UrlValidator.validate("http://100.127.255.255/"));
    }

    // ── RFC 2544 Benchmarking (198.18–19.x) ──────────────────────────────────

    @Test
    void validate_rfc2544_198_18_0_1_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> UrlValidator.validate("http://198.18.0.1/"));
    }

    @Test
    void validate_rfc2544_198_19_0_1_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> UrlValidator.validate("http://198.19.0.1/"));
    }

    // ── TEST-NETs (RFC 5737) ─────────────────────────────────────────────────

    @Test
    void validate_testNet2_198_51_100_1_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> UrlValidator.validate("http://198.51.100.1/"));
    }

    @Test
    void validate_testNet3_203_0_113_1_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> UrlValidator.validate("http://203.0.113.1/"));
    }

    // ── IPv6 loopback ─────────────────────────────────────────────────────────

    @Test
    void validate_ipv6Loopback_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> UrlValidator.validate("http://[::1]/"));
    }

    // ── Boundary: 100.63.x is NOT in RFC 6598 range ──────────────────────────
    // InetAddress.getByName("100.63.0.1") resolves immediately, no DNS needed.

    @Test
    void validate_100_63_0_1_notBlocked() {
        // 100.63.x.x is outside the RFC 6598 range — should NOT be blocked
        assertDoesNotThrow(() -> UrlValidator.validate("http://100.63.0.1/"));
    }

    // ── Valid public URLs (require DNS — tagged so CI can exclude them) ───────

    @Test
    @Tag("network")
    void validate_validHttps_doesNotThrow() {
        assertDoesNotThrow(() -> UrlValidator.validate("https://www.google.com/"));
    }

    @Test
    @Tag("network")
    void validate_validHttp_doesNotThrow() {
        assertDoesNotThrow(() -> UrlValidator.validate("http://example.com/"));
    }
}
