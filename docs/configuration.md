# ⚙️ Configuration Reference

🇺🇸 English · [🇧🇷 Português](configuration.pt-BR.md)

All configuration is stored in `src/main/resources/application.properties`.  
After building the fat JAR, you can override it by placing an `application.properties` file in the **same directory as the JAR** (no recompilation needed).

---

## Table of Contents

1. [Logging](#logging)
2. [Work Directories](#work-directories)
3. [Browser & HTTP](#browser--http)
4. [Playwright Timeouts](#playwright-timeouts)
5. [Content Limits](#content-limits)
6. [FormSkill](#formskill)
7. [PdfSkill](#pdfskill)
8. [LLM / Model](#llm--model)
9. [Retry Policy](#retry-policy)
10. [Locale & Region](#locale--region)
11. [Log Management](#log-management)
12. [Search Cache](#search-cache)
13. [Search Pipeline](#search-pipeline)
14. [Telegram](#telegram)

---

## Logging

Controls the SLF4J SimpleLogger output. Applied at JVM startup before any logging occurs.

```properties
# Log level for all SLF4J output: trace | debug | info | warn | error
slf4j.defaultLogLevel=warn

# Show date/time in log lines
slf4j.showDateTime=false

# Show thread names in log lines
slf4j.showThreadName=false

# Show the logger class name in log lines
slf4j.showLogName=false
```

> **Tip:** Set `slf4j.defaultLogLevel=debug` during development to see all ADK/gRPC events.

---

## Work Directories

Directories where Vincenzo stores generated files. Paths are relative to the working directory (where the JAR is launched).

```properties
# Directory for browser screenshots
work.screenshots.dir=work/screenshots

# Directory for Playwright downloads
work.downloads.dir=work/downloads
```

Both directories are created automatically if they do not exist.

---

## Browser & HTTP

```properties
# User-Agent sent with all browser requests
browser.user-agent=Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) ...

# Timeout for the DuckDuckGo Instant Answer JSON API (seconds)
http.connect.timeout.seconds=8
```

---

## Playwright Timeouts

Navigation timeouts for Playwright (values are in **milliseconds**).

```properties
# fetchPageContent navigation timeout
fetch.page.navigate.timeout.ms=20000

# screenshotPage navigation timeout
screenshot.navigate.timeout.ms=15000

# summarizeUrl navigation timeout
summarize.navigate.timeout.ms=20000

# extractStructuredData navigation timeout
extract.navigate.timeout.ms=20000

# fillFormAndSubmit navigation timeout
form.navigate.timeout.ms=20000
```

---

## Content Limits

Controls how many characters are passed to the LLM for each skill (to avoid exceeding model context windows).

```properties
# Max characters returned by fetchPageContent
fetch.page.max.chars=5000

# Max characters from DuckDuckGo HTML search results
ddg.page.text.max.chars=6000

# Max characters from Bing search results
bing.page.text.max.chars=6000

# Max characters returned by summarizeUrl
summarize.max.chars=8000

# Max items returned by extractStructuredData
extract.max.items=50
```

---

## FormSkill

Settings for `fillFormAndSubmit`:

```properties
# Wait time after form submission (ms) — lets the page settle before reading results
form.after.submit.wait.ms=3000

# Max characters returned from the result page after form submission
form.result.max.chars=5000
```

---

## PdfSkill

Settings for `readPdf`:

```properties
# HTTP connection timeout for PDF downloads (seconds)
pdf.http.timeout.seconds=30

# Max characters extracted from the PDF
pdf.max.chars=10000
```

---

## LLM / Model

```properties
# Gemini model used by the LlmAgent
# Available models: gemini-2.5-flash, gemini-2.0-flash, gemini-1.5-pro, etc.
# See: https://ai.google.dev/gemini-api/docs/models
llm.model=gemini-2.5-flash
```

---

## Retry Policy

Vincenzo automatically retries failed browser operations with exponential backoff.

```properties
# Total number of attempts (1 = no retry; 3 = initial + 2 retries)
retry.max.attempts=3

# Initial delay between retries in milliseconds
# Delay doubles on each retry: 500ms → 1000ms → 2000ms
retry.initial.delay.ms=500
```

---

## Locale & Region

```properties
# BCP 47 locale for the Playwright browser context
# Examples: pt-BR, en-US, es-ES, fr-FR
browser.locale=pt-BR

# DuckDuckGo region for the kl= parameter
# Examples: br-pt (Brazil/Portuguese), us-en (US/English), de-de, fr-fr
ddg.region=br-pt
```

---

## Log Management

Vincenzo writes structured session log files to the `logs/` directory.

```properties
# Maximum number of session log files to keep
# Oldest file is deleted when the limit is exceeded
log.max.files=10

# Maximum size per log file in KB (0 = no rotation)
log.max.size.kb=512
```

---

## Search Cache

An in-memory LRU cache for `searchWeb` results to avoid redundant browser requests.

```properties
# Enable or disable the search results cache
search.cache.enabled=true

# Max number of distinct queries to keep in memory (LRU eviction)
search.cache.max.size=500

# Time-to-live for each cached entry (minutes)
search.cache.ttl.minutes=60
```

---

## Search Pipeline

Controls how DuckDuckGo and Bing are orchestrated.

```properties
# Run DDG HTML and Bing in parallel instead of sequentially
# Default: false (sequential is safer and less resource-intensive)
search.parallel.enabled=false

# Timeout for the parallel search (ms)
search.parallel.timeout.ms=25000

# DDG HTML Circuit Breaker
# After N consecutive failures, DDG HTML is skipped for reset.ms milliseconds
search.circuit.ddg.failure.threshold=5
search.circuit.ddg.reset.ms=30000
```

---

## Telegram

```properties
# Interface mode: "cli" (terminal) or "telegram" (bot)
interface.mode=cli

# Telegram bot token (from @BotFather)
# Recommended: set via TELEGRAM_BOT_TOKEN environment variable instead
telegram.bot.token=

# Telegram update mode: "polling" (no server needed) or "webhook"
telegram.mode=polling

# Webhook mode: port the built-in HTTP server listens on
telegram.webhook.port=8443

# Webhook mode: public HTTPS URL for Telegram to POST updates to
telegram.webhook.url=
```

### Switching to Telegram mode

1. Create a bot via [@BotFather](https://t.me/botfather) and copy the token.
2. Set the token in `.env`:

   ```env
   TELEGRAM_BOT_TOKEN=123456:ABC-DEF...
   ```

3. Change the interface mode in `application.properties`:

   ```properties
   interface.mode=telegram
   telegram.mode=polling
   ```

4. Restart Vincenzo.

> Use `polling` mode for quick testing and VPS deployments without a public domain.  
> Use `webhook` mode for production setups with a public HTTPS endpoint.
