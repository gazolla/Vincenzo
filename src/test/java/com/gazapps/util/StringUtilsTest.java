package com.gazapps.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StringUtilsTest {

    @Test
    void truncate_nullInput_returnsLiteralNull() {
        assertEquals("null", StringUtils.truncate(null, 10));
    }

    @Test
    void truncate_exactLength_returnsUnchanged() {
        assertEquals("hello", StringUtils.truncate("hello", 5));
    }

    @Test
    void truncate_shorterThanMax_returnsUnchanged() {
        assertEquals("hi", StringUtils.truncate("hi", 100));
    }

    @Test
    void truncate_emptyString_maxZero_returnsEmpty() {
        assertEquals("", StringUtils.truncate("", 0));
    }

    @Test
    void truncate_longerThanMax_appendsEllipsis() {
        String result = StringUtils.truncate("abcde", 3);
        assertTrue(result.endsWith("..."), "Must end with ...");
    }

    @Test
    void truncate_longerThanMax_totalLengthIsMaxPlusThree() {
        int max = 4;
        String result = StringUtils.truncate("abcdefgh", max);
        assertEquals(max + 3, result.length());
    }

    @Test
    void truncate_longerThanMax_prefixMatchesOriginal() {
        String result = StringUtils.truncate("abcdefgh", 4);
        assertEquals("abcd...", result);
    }

    @Test
    void truncate_maxZeroWithNonEmptyInput_returnsEllipsisOnly() {
        assertEquals("...", StringUtils.truncate("abc", 0));
    }
}
