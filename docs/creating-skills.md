# 🛠️ Creating New Skills

🇺🇸 English · [🇧🇷 Português](creating-skills.pt-BR.md)

Skills are the building blocks of Vincenzo. Each Skill is a **plain Java class** containing one or more **static methods** that the Gemini LLM can call as tools. Adding a new capability to Vincenzo means writing a new Skill.

---

## How Skills Work

1. You write a **Java class** with **static public methods** annotated with `@Schema`.
2. You register the methods in `SearchAgent.java` using `FunctionTool.create(...)`.
3. The Google ADK serializes your method signatures into JSON Schema descriptors that Gemini understands.
4. When the user asks something that needs your tool, Gemini calls it automatically.

---

## Step-by-Step: Creating a New Skill

### Example: a `WeatherSkill` that fetches weather data

#### Step 1 — Create the Skill class

Create a new file:  
`src/main/java/com/gazapps/skills/WeatherSkill.java`

```java
package com.gazapps.skills;

import com.google.adk.tools.Annotations.Schema;
import java.util.HashMap;
import java.util.Map;

/**
 * ADK tool that fetches current weather data for a given city.
 */
public class WeatherSkill {

    @Schema(description = """
            Fetch the current weather for a specific city.
            Returns temperature, conditions, humidity, and wind speed.
            Use this whenever the user asks about current weather or forecast.
            """)
    public static Map<String, String> getWeather(
            @Schema(name = "city", description = "The city name, e.g. 'São Paulo' or 'New York'")
            String city) {

        Map<String, String> result = new HashMap<>();
        try {
            // TODO: call a real weather API here
            // For demonstration, returning fake data
            result.put("status", "success");
            result.put("city", city);
            result.put("temperature", "25°C");
            result.put("conditions", "Partly cloudy");
            result.put("humidity", "72%");
            result.put("wind", "15 km/h NE");
            return result;

        } catch (Exception e) {
            result.put("status", "error");
            result.put("city", city);
            result.put("message", "Failed to fetch weather: " + e.getMessage());
            return result;
        }
    }
}
```

#### Step 2 — Register the Skill in `SearchAgent.java`

Open `src/main/java/com/gazapps/agent/SearchAgent.java` and add your tool to the `.tools(...)` list:

```java
import com.gazapps.skills.WeatherSkill;
// ... existing imports ...

private static BaseAgent buildAgent() {
    return LlmAgent.builder()
            .name("internet-search-assistant")
            // ...
            .tools(
                    com.google.adk.tools.FunctionTool.create(WebOrchestrator.class, "searchWeb"),
                    com.google.adk.tools.FunctionTool.create(WebContentSkill.class, "fetchPageContent"),
                    com.google.adk.tools.FunctionTool.create(WebContentSkill.class, "screenshotPage"),
                    com.google.adk.tools.FunctionTool.create(SummarizeSkill.class, "summarizeUrl"),
                    com.google.adk.tools.FunctionTool.create(ExtractSkill.class, "extractStructuredData"),
                    com.google.adk.tools.FunctionTool.create(FormSkill.class, "fillFormAndSubmit"),
                    com.google.adk.tools.FunctionTool.create(PdfSkill.class, "readPdf"),
                    // ✅ YOUR NEW SKILL:
                    com.google.adk.tools.FunctionTool.create(WeatherSkill.class, "getWeather")
            )
            .build();
}
```

#### Step 3 — (Optional) Update the agent instructions

In `SearchAgent.java`, add a line to the `instruction(...)` block mentioning when Gemini should use your new tool:

```java
.instruction("""
        ...existing rules...
        - getWeather(city): fetches current weather for a city. Use for any weather-related questions.
        """)
```

#### Step 4 — Run and test

```bash
mvn compile exec:java
```

Ask: *"What's the weather in Rio de Janeiro right now?"*  
Vincenzo will call `getWeather("Rio de Janeiro")` automatically.

---

## Skill Design Rules

| Rule | Reason |
|---|---|
| Methods must be `public static` | ADK's `FunctionTool.create` requires static methods |
| Return `Map<String, String>` | Simplest serialization for ADK → Gemini |
| Always include a `status` field (`"success"` or `"error"`) | Lets the LLM detect failures |
| Keep `@Schema` descriptions clear and actionable | Gemini uses them to decide when to call each tool |
| Be specific about **when** to use the tool in `@Schema` | Prevents Gemini from invoking the tool incorrectly |
| Handle all exceptions internally | Never let a Skill throw exceptions to the agent |

---

## Multiple Methods Per Skill Class

A single Skill class can expose multiple tools. Example:

```java
public class WeatherSkill {

    @Schema(description = "Get current weather for a city.")
    public static Map<String, String> getWeather(
            @Schema(name = "city", description = "City name") String city) {
        // ...
    }

    @Schema(description = "Get a 7-day weather forecast for a city.")
    public static Map<String, String> getWeatherForecast(
            @Schema(name = "city", description = "City name") String city,
            @Schema(name = "days", description = "Number of forecast days (1-7)") String days) {
        // ...
    }
}
```

Register each method individually:

```java
com.google.adk.tools.FunctionTool.create(WeatherSkill.class, "getWeather"),
com.google.adk.tools.FunctionTool.create(WeatherSkill.class, "getWeatherForecast"),
```

---

## Using `BrowserService` in Your Skill

If your tool needs to open a web page, use the shared `BrowserService` instead of creating a new Playwright instance. This ensures efficient browser reuse.

```java
import com.gazapps.services.BrowserService;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

public class MySkill {

    public static Map<String, String> myTool(
            @Schema(name = "url", description = "URL to visit") String url) {

        return BrowserService.execute(page -> {
            page.navigate(url, new Page.NavigateOptions().setTimeout(20_000));
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            String text = page.innerText("body");

            Map<String, String> result = new HashMap<>();
            result.put("status", "success");
            result.put("content", text);
            return result;
        });
    }
}
```

---

## Using `LogService` in Your Skill

All Skills should log using the shared `LogService`:

```java
import com.gazapps.logging.LogService;

public class MySkill {

    private static final LogService LOG = LogService.getInstance();

    public static Map<String, String> myTool(String input) {
        LOG.section("TOOL CALL: myTool");
        LOG.info("MySkill", "Input: " + input);
        long start = System.currentTimeMillis();

        // ... perform the work ...

        LOG.timing("MySkill", "myTool total", System.currentTimeMillis() - start);
        return result;
    }
}
```

---

## Quick Checklist

```
[ ] Create: src/main/java/com/gazapps/skills/YourSkill.java
[ ] Method is public static
[ ] Annotate class/method with @Schema (clear descriptions)
[ ] Return Map<String, String> with "status" key
[ ] Catch all exceptions and return error map
[ ] Register in SearchAgent.java with FunctionTool.create(...)
[ ] (Optional) Add usage hint to agent instructions
[ ] mvn compile exec:java  →  test it
```
