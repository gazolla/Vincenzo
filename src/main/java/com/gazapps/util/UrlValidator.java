package com.gazapps.util;

import java.net.InetAddress;
import java.net.URI;

/**
 * Validates URLs before navigation or download to prevent SSRF attacks.
 * <p>
 * Only allows HTTP/HTTPS URLs pointing to public IP addresses.
 * Rejects file://, ftp://, and any URL that resolves to a private,
 * loopback, or link-local address.
 */
public class UrlValidator {

    private UrlValidator() {}

    /**
     * Validates the given URL for safe external navigation.
     *
     * @param url the URL string to validate
     * @throws IllegalArgumentException if the URL is null, blank, uses a
     *         disallowed scheme, or resolves to a private/loopback address
     */
    public static void validate(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL must not be null or blank");
        }

        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed URL: " + url, e);
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException(
                    "URL scheme '" + scheme + "' is not allowed. Only http and https are permitted. URL: " + url);
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL has no host: " + url);
        }

        try {
            InetAddress address = InetAddress.getByName(host);
            if (isPrivateOrReserved(address)) {
                throw new IllegalArgumentException(
                        "URL resolves to a private or loopback address, which is not allowed: " + url
                        + " → " + address.getHostAddress());
            }
        } catch (IllegalArgumentException e) {
            throw e; // re-throw our own validation errors as-is
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not resolve host '" + host + "' in URL: " + url, e);
        }
    }

    /**
     * Returns true if the given address is private, loopback, or link-local.
     * Covers IPv4 private ranges (RFC 1918), loopback, and IPv6 loopback/link-local.
     */
    private static boolean isPrivateOrReserved(InetAddress address) {
        if (address.isLoopbackAddress()) return true;
        if (address.isSiteLocalAddress()) return true;   // 10.x, 172.16-31.x, 192.168.x
        if (address.isLinkLocalAddress()) return true;   // 169.254.x / fe80::
        if (address.isAnyLocalAddress()) return true;    // 0.0.0.0 / ::

        // Extra check for IPv4 addresses not caught by the above
        byte[] raw = address.getAddress();
        if (raw.length == 4) {
            int b0 = raw[0] & 0xFF;
            int b1 = raw[1] & 0xFF;
            // 127.x.x.x  (loopback — already covered, but be explicit)
            if (b0 == 127) return true;
            // 100.64.x.x – 100.127.x.x  (Shared Address Space, RFC 6598)
            if (b0 == 100 && b1 >= 64 && b1 <= 127) return true;
            // 192.0.0.x  (IETF Protocol Assignments)
            if (b0 == 192 && b1 == 0 && (raw[2] & 0xFF) == 0) return true;
            // 198.18.x.x – 198.19.x.x  (Benchmarking, RFC 2544)
            if (b0 == 198 && (b1 == 18 || b1 == 19)) return true;
            // 198.51.100.x  (TEST-NET-2, RFC 5737)
            if (b0 == 198 && b1 == 51 && (raw[2] & 0xFF) == 100) return true;
            // 203.0.113.x  (TEST-NET-3, RFC 5737)
            if (b0 == 203 && b1 == 0 && (raw[2] & 0xFF) == 113) return true;
        }

        return false;
    }
}
