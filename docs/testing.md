# 🧪 Testing Guide

🇺🇸 English · [🇧🇷 Português](testing.pt-BR.md)

One test prompt per tool, with objective and expected result.
Run these in the Vincenzo CLI: `mvn package -DskipTests && java -jar target/*.jar`

---

## 1. WebContentSkill

### `fetchPageContent`

```
Read the content of https://www.gov.br/pt-br and tell me the main topics listed.
```

**Objective:** Confirm that Vincenzo navigates to the page, extracts clean text, and responds with the content.
**Expected result:** List of topics/services from the gov.br portal.

---

### `screenshotPage`

```
Take a screenshot of https://www.uol.com.br and save it as uol-home.png
```

**Objective:** Confirm that the PNG file is saved at `work/screenshots/uol-home.png`.
**Expected result:** Message informing the saved file path.

---

## 2. SummarizeSkill

### `summarizeUrl`

```
Summarize the article at https://google.github.io/adk-docs/deploy/cloud-run/
```

**Objective:** Verify that `summarizeUrl` is called (not `fetchPageContent`), retry works correctly, and the summary is coherent.
**Expected result:** Summary with the main points of the Cloud Run deployment article.

> **Alternative:** `Summarize the article at https://tecnoblog.net/noticias/apple-mac-mini-m4-review/`

---

## 3. ExtractSkill

### `extractStructuredData`

```
Extract the titles and links of the main stories from https://news.ycombinator.com using CSS selectors.
```

**Objective:** Confirm that Vincenzo uses the `|href` syntax to extract the href attribute from links, returning distinct titles and URLs.
**Expected result:** List with `title` (text) and `link` (real URL, e.g. `https://...`) for each Hacker News story.
**Expected selectors:** `{"title": ".titleline > a", "link": ".titleline > a|href"}`

> **Note on `|attr` syntax:** To extract an HTML attribute (href, src, data-*) instead of text content,
> append `|attrName` to the CSS selector. Example: `".titleline > a|href"` extracts the `href`
> attribute; `"img.cover|src"` extracts the image source URL.

---

## 4. PdfSkill

### `readPdf`

```
Read and summarize this PDF: https://www.africau.edu/images/default/sample.pdf
```

**Objective:** Confirm that Vincenzo uses `readPdf` (not `fetchPageContent`) when it detects the `.pdf` extension.
**Expected result:** Summary of the PDF content.

> **Alternative (large PDF):** `Read and summarize this PDF: https://www.bndes.gov.br/SiteBNDES/export/sites/default/bndes_pt/Galerias/Arquivos/empresa/RelAnual/ra2023/BNDES_Relatorio_Anual_2023.pdf`

---

## 5. FormSkill

### `fillFormAndSubmit`

```
Search for bus tickets from São Paulo to Rio de Janeiro on 03/15/2026 at https://www.buscaonibus.com.br
```

**Objective:** Confirm that Vincenzo inspects the HTML via `fetchPageContent`, identifies the form field selectors (origin, destination, date), fills them in, and returns the results.
**Expected result:** List of ticket options with schedules and prices.

> **Note:** Sites that render entirely via heavy JavaScript (Google Maps, modern SPAs) are not suitable
> for `fillFormAndSubmit` — the DOM arrives empty at `DOMCONTENTLOADED`. Bus/flight search sites
> typically have more accessible traditional HTML forms.
>
> **Confirmed anti-pattern:** `https://www.google.com/maps` returned only 61 chars of content —
> the form only exists after multiple JS render cycles and bot detection. Vincenzo correctly refused
> to attempt FormSkill without valid selectors.

---

## 6. RssSkill

### `discoverFeed`

```
What is the RSS feed of https://www.theverge.com?
```

**Objective:** Confirm that Vincenzo automatically detects the RSS/Atom feed link in the HTML.
**Expected result:** Feed URL (e.g. `https://www.theverge.com/rss/index.xml`).

---

### `readFeed`

```
Read the latest 5 news items from the feed https://feeds.bbci.co.uk/news/rss.xml
```

**Objective:** Confirm structured feed reading with title, link, date, and description per item.
**Expected result:** List of the 5 most recent BBC News items.

---

### `searchInFeed`

```
Is there any news about artificial intelligence in the feed https://feeds.bbci.co.uk/news/technology/rss.xml?
```

**Objective:** Confirm that keyword search in the feed returns only relevant items.
**Expected result:** Feed items containing "artificial intelligence" in the title or description (or a "no results" message if none found).

---

## 7. SchedulerSkill

### `scheduleMonitor`

```
Monitor the feed https://feeds.bbci.co.uk/news/technology/rss.xml every 60 minutes and notify me when a story about "bitcoin" appears.
```

**Objective:** Confirm that Vincenzo asks for confirmation before scheduling, and that the job is persisted in `work/scheduler-jobs.json`.
**Expected result:** Job created with an id like `job-xxxxxxxx`, confirmation of scheduling.

---

### `listMonitors`

```
Which monitors are active?
```

**Objective:** List scheduled jobs with status, URL, keyword, and next execution time.
**Expected result:** Table or list with active jobs (including the one created above).

---

### `cancelMonitor`

```
Cancel the bitcoin monitor we just created.
```

**Objective:** Confirm that Vincenzo calls `listMonitors` first to get the id, asks for confirmation, and removes the job.
**Expected result:** Cancellation confirmation; `work/scheduler-jobs.json` no longer contains the job.

---

## 8. NotificationSkill

### `sendNotification`

```
Send me a notification saying: "Reminder: review the Vincenzo project metrics tomorrow at 9am."
```

**Objective:** In CLI mode, confirm that the notification goes to the internal queue.
**Expected result:** Status `success`, `notif-id` returned.

---

### `listPendingNotifications`

```
Do I have pending notifications?
```

**Objective:** Confirm that the notification sent above appears in the queue with `read: false`.
**Expected result:** List with the metrics review notification.

---

### `markAsRead`

```
Mark the notification we just saw as read.
```

**Objective:** Confirm that Vincenzo fetches the id via `listPendingNotifications` and marks it as read.
**Expected result:** Status `success`; the notification changes to `read: true`.

---

## 9. MemorySkill

### `saveMemory`

```
Remember that I prefer Python, use VSCode, and my current project is called Vincenzo.
```

**Objective:** Confirm that Vincenzo saves with descriptive tags and persists to `work/memory-store.json`.
**Expected result:** `id` of type `mem-xxxxxxxx`, confirmation of what was saved.

> **Note:** Vincenzo tends to save immediately when the user uses an explicit imperative like "remember".
> This is acceptable behavior — upfront confirmation is more relevant when the agent decides to save
> on its own (without an explicit user request).

---

### `retrieveMemory`

```
What do you know about my language preferences?
```

**Objective:** Confirm that Vincenzo queries the memory store before answering (does not rely solely on session history).
**Expected result:** The Python preference appears in the response, with a reference to stored memory.

---

### `listMemories`

```
List everything you have stored about me.
```

**Objective:** Confirm full listing with id, content, tags, category, and timestamp.
**Expected result:** List of all entries in `work/memory-store.json`.

---

### `updateMemory`

```
Update my preferences memory: in addition to Python, I also use Go for backend services.
```

**Objective:** Confirm that Vincenzo fetches the correct id via `retrieveMemory`, proposes the update to the user, and calls `updateMemory` with the new content.
**Expected result:** Memory updated with new content; `updatedAt` more recent than `createdAt`.

---

### `deleteMemory`

```
Delete the memory about the Vincenzo project.
```

**Objective:** Confirm that Vincenzo asks for explicit confirmation before deleting, and that the entry disappears from `work/memory-store.json`.
**Expected result:** Deletion confirmation; entry no longer appears in `listMemories`.

---

## Integration Test — Multi-Skill Chain

```
Search for the latest news about Google's Gemini 2.5 model, read the most relevant article you find,
save a summary to memory with the tag "research,ai" and notify me when you are done.
```

**Objective:** Exercise `searchWeb → fetchPageContent → saveMemory → sendNotification` in sequence.
**Expected result:** Article summary presented, memory saved with id `mem-*`, notification in the queue.

> **⚠️ Important when testing:** Send the prompt all at once, without interruptions. A fragmented prompt
> (broken and resent) causes the agent to respond from session memory without calling tools. Verify in
> the log that all 4 tool calls appear in sequence before considering the test valid.
>
> **Known anomaly (session 20260222_114447):** With a fragmented prompt, the agent responded with
> content from its own knowledge without evidence of `searchWeb` in the log, then claimed to have
> saved and notified without visible tool calls. Re-test with a clean prompt in a new session.

---

## Notes

- All prompts work in the default CLI mode (`interface.mode=cli`).
- To test retry on timeout: use a URL known to be slow or to block bots (e.g. banking sites, foreign government portals).
- To observe `error_type` in the log: use an invalid URL like `https://this-site-does-not-exist-xyzxyz.com`.
- The session log is at `logs/session-*.log` for a complete audit of tool calls.
