<div align="center">

<img src="docs/vincenzo-robot.png" alt="Vincenzo Robot" width="200"/>

<!-- Save the Vincenzo robot image as docs/vincenzo-robot.png to display it here -->

# Vincenzo

### AI Internet Search Assistant

**An autonomous AI agent powered by [Google ADK](https://google.github.io/adk-docs/) with real-time web search capabilities**

🇺🇸 English · [🇧🇷 Português](README.pt-BR.md)

</div>

---

## About the Name

**Vincenzo** was inspired by **V.I.N.CENT** (*Vital Information Necessary CENTralized*), the iconic robot from the 1979 Disney sci-fi film **"The Black Hole"**. V.I.N.CENT charmed audiences with his intelligence, loyalty, and wit — the kind of robot you'd always want by your side. Since childhood I've wanted a robot like that. Well… now I have one. Meet **Vincenzo** 🤖

---

## What is the Vincenzo Framework?

Vincenzo is a **Java 21 conversational AI framework** built on top of [Google ADK (Agent Development Kit)](https://google.github.io/adk-docs/) that gives a Gemini-powered LLM agent access to a **real web browser** via [Microsoft Playwright](https://playwright.dev/java/).

Unlike simple chatbots that answer only from training data, Vincenzo's agent:

- **Searches the web** in real time via DuckDuckGo and Bing
- **Reads any web page** fully, extracting clean text
- **Takes screenshots** of pages
- **Extracts structured data** from pages using CSS selectors (including HTML attributes via `|attr` syntax)
- **Summarizes** any article or URL
- **Fills out and submits HTML forms** on websites
- **Reads PDF files** downloaded from the web
- **Reads RSS/Atom feeds** and searches them by keyword
- **Schedules monitors** that watch feeds or pages and notify on matches
- **Sends and manages notifications** (CLI queue or Telegram)
- **Stores and retrieves long-term memory** across sessions

The framework is built around the concept of **Skills** — modular Java classes that expose tools to the LLM agent via Google ADK's `@FunctionTool` mechanism. Every new capability you give to Vincenzo is a new Skill.

## Documentation

| Section | Link |
|---|---|
| 🚀 Installation (Local & VPS) | [docs/installation.md](docs/installation.md) |
| ⚙️ Configuration Reference | [docs/configuration.md](docs/configuration.md) |
| 🛠️ Creating New Skills | [docs/creating-skills.md](docs/creating-skills.md) |
| 🧪 Testing Guide | [docs/testing.md](docs/testing.md) |

---

## Quick Start

```bash
# 1. Clone the project
git clone https://github.com/your-org/vincenzo.git
cd vincenzo

# 2. Set your Google AI API key
export GOOGLE_API_KEY="your-api-key-here"

# 3. Install Playwright browsers
mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args=install

# 4. Run
mvn compile exec:java
```

---

## Built-in Skills

| Skill Method | Description |
|---|---|
| `searchWeb(query)` | Searches DuckDuckGo (JSON + HTML) with Bing fallback |
| `fetchPageContent(url)` | Reads the full text content of any web page |
| `screenshotPage(url, filename)` | Takes a screenshot of a web page |
| `summarizeUrl(url)` | Fetches and cleans a page for LLM summarization |
| `extractStructuredData(url, selectors)` | Extracts structured data using CSS selectors; use `selector\|attr` for HTML attributes |
| `fillFormAndSubmit(url, fields, selector)` | Fills and submits HTML forms |
| `readPdf(url)` | Downloads and extracts text from PDF files |
| `discoverFeed(url)` | Auto-detects the RSS/Atom feed URL from a website |
| `readFeed(url)` | Reads and returns items from an RSS/Atom feed |
| `searchInFeed(url, keyword)` | Searches feed items by keyword |
| `scheduleMonitor(url, keyword, interval)` | Schedules a recurring monitor that notifies on keyword matches |
| `listMonitors()` | Lists all active scheduled monitor jobs |
| `cancelMonitor(jobId)` | Cancels and removes a scheduled monitor |
| `sendNotification(message)` | Sends a notification (CLI queue or Telegram) |
| `listPendingNotifications()` | Lists unread notifications |
| `markAsRead(notifId)` | Marks a notification as read |
| `saveMemory(content, tags)` | Saves a memory entry for long-term recall |
| `retrieveMemory(query)` | Searches stored memories by semantic query |
| `listMemories()` | Lists all stored memory entries |
| `updateMemory(id, content)` | Updates an existing memory entry |
| `deleteMemory(id)` | Deletes a memory entry |

---

## Interface Modes

Vincenzo supports two interface modes configured via `application.properties`:

| Mode | Description |
|---|---|
| `cli` (default) | Interactive terminal chat session |
| `telegram` | Telegram bot (polling or webhook) |

---

## Prerequisites

- Java 21+
- Maven 3.9+
- A [Google AI Studio API key](https://aistudio.google.com/app/apikey)
- (Optional) A Telegram bot token from [@BotFather](https://t.me/botfather) for Telegram mode

---

## License

MIT License — see [LICENSE](LICENSE) for details.
