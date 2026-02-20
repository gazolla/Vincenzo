package com.gazapps.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BrowserService UA rotation logic.
 * No Playwright / browser is started — only the static helper methods are exercised.
 */
class BrowserServiceUaRotationTest {

    @BeforeEach
    void resetIndex() {
        // Reset the rotation index before each test so tests are independent
        BrowserService.UA_INDEX.set(0);
    }

    @Test
    void nextUaProfile_returnsNonNullProfile() {
        var profile = BrowserService.nextUaProfile();
        assertNotNull(profile);
        assertFalse(profile.userAgent().isBlank());
    }

    @Test
    void nextUaProfile_fiveCallsReturnDistinctUserAgents() {
        Set<String> agents = new HashSet<>();
        // Pool has 5 profiles; 5 sequential calls should yield 5 distinct UAs
        for (int i = 0; i < 5; i++) {
            agents.add(BrowserService.nextUaProfile().userAgent());
        }
        assertEquals(5, agents.size(), "Expected 5 distinct User-Agent strings from the pool");
    }

    @Test
    void nextUaProfile_sixthCallWrapsAroundToFirstProfile() {
        // call index 0..4 to exhaust the pool
        String first = BrowserService.nextUaProfile().userAgent();
        for (int i = 1; i < 5; i++) {
            BrowserService.nextUaProfile();
        }
        // 6th call (index 5 % 5 == 0) should return the same UA as the first call
        String sixth = BrowserService.nextUaProfile().userAgent();
        assertEquals(first, sixth, "Expected wrap-around to the first UA profile on call #6");
    }

    @Test
    void nextUaProfile_eachProfileHasConsistentSecChUaHeaders() {
        // Ensure every profile carries non-blank sec-ch-ua hints (consistency check)
        for (int i = 0; i < 5; i++) {
            var profile = BrowserService.nextUaProfile();
            assertFalse(profile.secChUa().isBlank(),
                "Profile " + i + " has blank sec-ch-ua");
            assertFalse(profile.secChUaPlatform().isBlank(),
                "Profile " + i + " has blank sec-ch-ua-platform");
        }
    }
}
