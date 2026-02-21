package com.gazapps.skills;

import com.gazapps.skills.rss.FeedItem;
import com.gazapps.skills.rss.FeedParser;
import com.gazapps.skills.rss.FeedTextUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RssSkill} and its supporting components.
 *
 * <p>
 * All tests here are pure unit tests — no network calls, no Playwright.
 * Integration tests (real network) are grouped at the bottom and annotated
 * {@code @Disabled}.
 */
class RssSkillTest {

  // ── discoverFeed — URL validation ─────────────────────────────────────────

  @Test
  void discoverFeed_nullUrl_returnsErrorMap() {
    Map<String, String> result = RssSkill.discoverFeed(null);
    assertEquals("error", result.get("status"));
    assertNotNull(result.get("message"));
  }

  @Test
  void discoverFeed_privateIpUrl_returnsErrorMap() {
    Map<String, String> result = RssSkill.discoverFeed("http://192.168.0.1/feed");
    assertEquals("error", result.get("status"));
    assertTrue(result.get("message").toLowerCase().contains("blocked"));
  }

  @Test
  void discoverFeed_loopbackUrl_returnsErrorMap() {
    Map<String, String> result = RssSkill.discoverFeed("http://127.0.0.1/feed");
    assertEquals("error", result.get("status"));
    assertTrue(result.get("message").toLowerCase().contains("blocked"));
  }

  @Test
  void discoverFeed_internalIp10_returnsErrorMap() {
    Map<String, String> result = RssSkill.discoverFeed("http://10.0.0.1/rss");
    assertEquals("error", result.get("status"));
    assertTrue(result.get("message").toLowerCase().contains("blocked"));
  }

  @Test
  void discoverFeed_ftpScheme_returnsErrorMap() {
    Map<String, String> result = RssSkill.discoverFeed("ftp://example.com/feed");
    assertEquals("error", result.get("status"));
    assertNotNull(result.get("message"));
  }

  // ── readFeed — URL validation ─────────────────────────────────────────────

  @Test
  void readFeed_privateIpUrl_returnsErrorMap() {
    Map<String, String> result = RssSkill.readFeed("http://192.168.1.100/rss", 10);
    assertEquals("error", result.get("status"));
    assertTrue(result.get("message").toLowerCase().contains("blocked"));
  }

  @Test
  void readFeed_loopbackUrl_returnsErrorMap() {
    Map<String, String> result = RssSkill.readFeed("http://127.0.0.1/rss", 5);
    assertEquals("error", result.get("status"));
    assertTrue(result.get("message").toLowerCase().contains("blocked"));
  }

  @Test
  void readFeed_nullUrl_returnsErrorMap() {
    Map<String, String> result = RssSkill.readFeed(null, 10);
    assertEquals("error", result.get("status"));
    assertNotNull(result.get("message"));
  }

  // ── searchInFeed — input validation ───────────────────────────────────────

  @Test
  void searchInFeed_privateIpUrl_returnsErrorMap() {
    Map<String, String> result = RssSkill.searchInFeed("http://10.0.0.1/feed", "java");
    assertEquals("error", result.get("status"));
    assertTrue(result.get("message").toLowerCase().contains("blocked"));
  }

  @Test
  void searchInFeed_blankKeyword_returnsErrorMap() {
    Map<String, String> result = RssSkill.searchInFeed("https://example.com/feed", "  ");
    assertEquals("error", result.get("status"));
    assertTrue(result.get("message").toLowerCase().contains("keyword"));
  }

  @Test
  void searchInFeed_nullKeyword_returnsErrorMap() {
    Map<String, String> result = RssSkill.searchInFeed("https://example.com/feed", null);
    assertEquals("error", result.get("status"));
    assertNotNull(result.get("message"));
  }

  @Test
  void searchInFeed_nullUrl_returnsErrorMap() {
    Map<String, String> result = RssSkill.searchInFeed(null, "java");
    assertEquals("error", result.get("status"));
    assertNotNull(result.get("message"));
  }

  // ── FeedParser — XML parsing (RSS 2.0) ────────────────────────────────────

  @Test
  void parseItems_validRss2_returnsItems() throws Exception {
    byte[] xml = RSS2_SAMPLE.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    List<FeedItem> items = FeedParser.parseItems(xml, 10);

    assertEquals(2, items.size());

    assertEquals("First Article", items.get(0).title);
    assertEquals("https://example.com/article1", items.get(0).link);
    assertEquals("Mon, 17 Feb 2025 10:00:00 GMT", items.get(0).pubDate);
    assertFalse(items.get(0).description.isBlank());

    assertEquals("Second Article", items.get(1).title);
    assertEquals("https://example.com/article2", items.get(1).link);
  }

  @Test
  void parseItems_maxItemsRespected() throws Exception {
    byte[] xml = RSS2_SAMPLE.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    List<FeedItem> items = FeedParser.parseItems(xml, 1);
    assertEquals(1, items.size());
  }

  @Test
  void parseItems_maxItemsZero_returnsEmpty() throws Exception {
    byte[] xml = RSS2_SAMPLE.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    List<FeedItem> items = FeedParser.parseItems(xml, 0);
    assertEquals(0, items.size());
  }

  // ── FeedParser — XML parsing (Atom 1.0) ──────────────────────────────────

  @Test
  void parseItems_validAtom_returnsEntries() throws Exception {
    byte[] xml = ATOM_SAMPLE.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    List<FeedItem> items = FeedParser.parseItems(xml, 10);

    assertEquals(2, items.size());

    assertEquals("Atom Entry One", items.get(0).title);
    assertEquals("https://example.com/entry1", items.get(0).link);
    assertFalse(items.get(0).pubDate.isBlank());

    assertEquals("Atom Entry Two", items.get(1).title);
  }

  // ── FeedParser — HTML stripping in descriptions ───────────────────────────

  @Test
  void parseItems_htmlTagsInDescription_areStripped() throws Exception {
    byte[] xml = RSS_WITH_HTML_DESC.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    List<FeedItem> items = FeedParser.parseItems(xml, 10);

    assertFalse(items.get(0).description.isEmpty());
    assertFalse(items.get(0).description.contains("<p>"),
        "HTML tags should be stripped from description");
    assertFalse(items.get(0).description.contains("<b>"),
        "HTML tags should be stripped from description");
  }

  // ── FeedParser — description truncation ──────────────────────────────────

  @Test
  void parseItems_longDescription_isTruncated() throws Exception {
    byte[] xml = RSS_WITH_LONG_DESC.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    List<FeedItem> items = FeedParser.parseItems(xml, 10);

    int maxChars = com.gazapps.config.AppConfig.RSS_MAX_DESCRIPTION_CHARS;
    assertTrue(items.get(0).description.length() <= maxChars + 3,
        "Description should be truncated to RSS_MAX_DESCRIPTION_CHARS (plus '...')");
  }

  // ── FeedTextUtils — isolated unit tests ───────────────────────────────────

  @Test
  void stripHtml_removesTagsAndCollapseSpaces() {
    String result = FeedTextUtils.stripHtml("<p>Hello <b>world</b>!</p>");
    assertEquals("Hello world !", result);
  }

  @Test
  void stripHtml_nullInput_returnsEmpty() {
    assertEquals("", FeedTextUtils.stripHtml(null));
  }

  @Test
  void extractHref_findsQuotedValue() {
    String html = "<link rel=\"alternate\" type=\"application/rss+xml\" href=\"/feed.rss\"/>";
    int idx = html.indexOf("application/rss+xml");
    assertEquals("/feed.rss", FeedTextUtils.extractHref(html, idx));
  }

  @Test
  void baseUrl_extractsSchemeAndHost() {
    assertEquals("https://example.com", FeedTextUtils.baseUrl("https://example.com/some/path?q=1"));
  }

  @Test
  void baseUrl_includesPort() {
    assertEquals("http://example.com:8080", FeedTextUtils.baseUrl("http://example.com:8080/rss"));
  }

  // ── searchInFeed — keyword filtering (via RssSkill.parseItems bridge) ─────

  @Test
  void searchInFeed_keywordInTitle_isFound() throws Exception {
    byte[] xml = RSS2_SAMPLE.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    List<FeedItem> all = RssSkill.parseItems(xml, 10);

    String keyword = "first";
    List<FeedItem> matches = all.stream()
        .filter(i -> i.title.toLowerCase().contains(keyword)
            || i.description.toLowerCase().contains(keyword))
        .toList();

    assertEquals(1, matches.size());
    assertTrue(matches.get(0).title.toLowerCase().contains("first"));
  }

  @Test
  void searchInFeed_keywordIsCaseInsensitive() throws Exception {
    byte[] xml = RSS2_SAMPLE.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    List<FeedItem> all = RssSkill.parseItems(xml, 10);

    String keyword = "FIRST";
    List<FeedItem> matches = all.stream()
        .filter(i -> i.title.toLowerCase().contains(keyword.toLowerCase())
            || i.description.toLowerCase().contains(keyword.toLowerCase()))
        .toList();

    assertEquals(1, matches.size());
  }

  @Test
  void searchInFeed_noMatch_returnsEmptyList() throws Exception {
    byte[] xml = RSS2_SAMPLE.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    List<FeedItem> all = RssSkill.parseItems(xml, 10);

    String keyword = "xyzzynonexistent";
    List<FeedItem> matches = all.stream()
        .filter(i -> i.title.toLowerCase().contains(keyword)
            || i.description.toLowerCase().contains(keyword))
        .toList();

    assertEquals(0, matches.size());
  }

  // ── Integration tests (disabled — require real network) ───────────────────

  @Test
  @Disabled("Requires network access — run manually against a live site")
  void discoverFeed_realSite_returnsFeedUrl() {
    Map<String, String> result = RssSkill.discoverFeed("https://github.com/blog");
    assertEquals("success", result.get("status"));
    assertFalse(result.get("feed_url").isBlank());
  }

  @Test
  @Disabled("Requires network access — run manually against a live RSS feed")
  void readFeed_realFeed_returnsItems() {
    Map<String, String> result = RssSkill.readFeed("https://feeds.feedburner.com/TechCrunch/", 5);
    assertEquals("success", result.get("status"));
    assertNotNull(result.get("items"));
    assertTrue(Integer.parseInt(result.get("item_count")) > 0);
  }

  @Test
  @Disabled("Requires network access — run manually against a live RSS feed")
  void searchInFeed_realFeed_returnsMatches() {
    Map<String, String> result = RssSkill.searchInFeed(
        "https://feeds.feedburner.com/TechCrunch/", "AI");
    assertEquals("success", result.get("status"));
    assertNotNull(result.get("matches"));
  }

  // ── Inline XML fixtures ───────────────────────────────────────────────────

  private static final String RSS2_SAMPLE = """
      <?xml version="1.0" encoding="UTF-8"?>
      <rss version="2.0">
        <channel>
          <title>Test Feed</title>
          <link>https://example.com</link>
          <item>
            <title>First Article</title>
            <link>https://example.com/article1</link>
            <pubDate>Mon, 17 Feb 2025 10:00:00 GMT</pubDate>
            <description>This is the first article description.</description>
          </item>
          <item>
            <title>Second Article</title>
            <link>https://example.com/article2</link>
            <pubDate>Tue, 18 Feb 2025 09:00:00 GMT</pubDate>
            <description>This is the second article about something else.</description>
          </item>
        </channel>
      </rss>
      """;

  private static final String ATOM_SAMPLE = """
      <?xml version="1.0" encoding="UTF-8"?>
      <feed xmlns="http://www.w3.org/2005/Atom">
        <title>Atom Test Feed</title>
        <entry>
          <title>Atom Entry One</title>
          <link href="https://example.com/entry1"/>
          <updated>2025-02-17T10:00:00Z</updated>
          <summary>Summary of entry one.</summary>
        </entry>
        <entry>
          <title>Atom Entry Two</title>
          <link href="https://example.com/entry2"/>
          <updated>2025-02-18T09:00:00Z</updated>
          <summary>Summary of entry two.</summary>
        </entry>
      </feed>
      """;

  private static final String RSS_WITH_HTML_DESC = """
      <?xml version="1.0" encoding="UTF-8"?>
      <rss version="2.0">
        <channel>
          <title>HTML Feed</title>
          <item>
            <title>HTML Article</title>
            <link>https://example.com/html</link>
            <description><![CDATA[<p>This is a <b>bold</b> description with <a href="#">links</a>.</p>]]></description>
          </item>
        </channel>
      </rss>
      """;

  private static final String RSS_WITH_LONG_DESC;

  static {
    String longDesc = "A".repeat(2000);
    RSS_WITH_LONG_DESC = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0">
          <channel>
            <title>Long Desc Feed</title>
            <item>
              <title>Long Description Article</title>
              <link>https://example.com/long</link>
              <description>""" + longDesc + """
              </description>
            </item>
          </channel>
        </rss>
        """;
  }
}
