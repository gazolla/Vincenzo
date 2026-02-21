package com.gazapps.skills.rss;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses RSS 2.0 and Atom 1.0 feed documents into {@link FeedItem} lists.
 *
 * <p>
 * XXE attacks are prevented via {@code DocumentBuilderFactory} feature flags.
 */
public final class FeedParser {

    private FeedParser() {
    }

    /**
     * Parse RSS 2.0 or Atom 1.0 bytes into a list of {@link FeedItem}s.
     *
     * @param bytes    raw XML bytes of the feed
     * @param maxItems maximum number of items to return
     */
    public static List<FeedItem> parseItems(byte[] bytes, int maxItems) throws Exception {
        Document doc = parseXml(bytes);
        String rootTag = doc.getDocumentElement().getTagName().toLowerCase();

        return rootTag.contains("rss") ? parseRss(doc, maxItems) : parseAtom(doc, maxItems);
    }

    /**
     * Extract the feed/channel title from raw feed bytes.
     *
     * @return the title, or an empty string if not found or parsing fails
     */
    public static String parseFeedTitle(byte[] bytes) {
        try {
            Document doc = parseXml(bytes);
            // RSS 2.0: <channel><title>
            NodeList channels = doc.getElementsByTagName("channel");
            if (channels.getLength() > 0) {
                String t = textOf((Element) channels.item(0), "title");
                if (!t.isEmpty())
                    return t;
            }
            // Atom 1.0: top-level <title>
            NodeList titles = doc.getElementsByTagName("title");
            if (titles.getLength() > 0) {
                return titles.item(0).getTextContent().trim();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    // ── private ──────────────────────────────────────────────────────────────

    private static List<FeedItem> parseRss(Document doc, int maxItems) {
        NodeList nodes = doc.getElementsByTagName("item");
        int limit = Math.min(nodes.getLength(), maxItems);
        List<FeedItem> items = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            Element el = (Element) nodes.item(i);
            FeedItem item = new FeedItem();
            item.title = textOf(el, "title");
            item.link = textOf(el, "link");
            item.pubDate = textOf(el, "pubDate");
            item.description = FeedTextUtils.truncate(FeedTextUtils.stripHtml(textOf(el, "description")));
            items.add(item);
        }
        return items;
    }

    private static List<FeedItem> parseAtom(Document doc, int maxItems) {
        NodeList nodes = doc.getElementsByTagName("entry");
        int limit = Math.min(nodes.getLength(), maxItems);
        List<FeedItem> items = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            Element el = (Element) nodes.item(i);
            FeedItem item = new FeedItem();
            item.title = textOf(el, "title");
            item.pubDate = firstNonEmpty(textOf(el, "updated"), textOf(el, "published"));
            // Atom <link> carries the URL in an href attribute
            NodeList linkEls = el.getElementsByTagName("link");
            if (linkEls.getLength() > 0) {
                Element linkEl = (Element) linkEls.item(0);
                item.link = linkEl.getAttribute("href");
                if (item.link.isEmpty())
                    item.link = linkEl.getTextContent().trim();
            }
            item.description = FeedTextUtils.truncate(FeedTextUtils.stripHtml(
                    firstNonEmpty(textOf(el, "summary"), textOf(el, "content"))));
            items.add(item);
        }
        return items;
    }

    /** Build a secure DocumentBuilder and parse bytes into a DOM Document. */
    private static Document parseXml(byte[] bytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(null); // suppress stderr warnings
        return builder.parse(new ByteArrayInputStream(bytes));
    }

    /** Get the text content of the first child element with the given tag. */
    private static String textOf(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        return nl.getLength() > 0 ? nl.item(0).getTextContent().trim() : "";
    }

    /** Return the first non-blank string from the candidates. */
    private static String firstNonEmpty(String... candidates) {
        for (String c : candidates)
            if (c != null && !c.isBlank())
                return c;
        return "";
    }
}
