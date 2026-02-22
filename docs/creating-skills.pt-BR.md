# 🛠️ Criando Novas Skills

[🇺🇸 English](creating-skills.md) · 🇧🇷 Português

As Skills são os blocos de construção do Vincenzo. Cada Skill é uma **classe Java simples** contendo um ou mais **métodos estáticos** que o LLM Gemini pode chamar como ferramentas. Adicionar uma nova capacidade ao Vincenzo significa escrever uma nova Skill.

---

## Como as Skills Funcionam

1. Você escreve uma **classe Java** com **métodos public static** anotados com `@Schema`.
2. Você registra os métodos em `SearchAgent.java` usando `FunctionTool.create(...)`.
3. O Google ADK serializa as assinaturas dos métodos em descritores JSON Schema que o Gemini entende.
4. Quando o usuário pede algo que precisa da sua ferramenta, o Gemini a chama automaticamente.

---

## Passo a Passo: Criando uma Nova Skill

### Exemplo: uma `WeatherSkill` que busca dados de clima

#### Passo 1 — Crie a classe da Skill

Crie um novo arquivo:  
`src/main/java/com/gazapps/skills/WeatherSkill.java`

```java
package com.gazapps.skills;

import com.google.adk.tools.Annotations.Schema;
import java.util.HashMap;
import java.util.Map;

/**
 * Ferramenta ADK que busca dados de clima atual para uma cidade.
 */
public class WeatherSkill {

    @Schema(description = """
            Busca o clima atual de uma cidade específica.
            Retorna temperatura, condições, umidade e velocidade do vento.
            Use isso sempre que o usuário perguntar sobre o clima atual ou previsão.
            """)
    public static Map<String, String> getWeather(
            @Schema(name = "city", description = "O nome da cidade, ex: 'São Paulo' ou 'New York'")
            String city) {

        Map<String, String> result = new HashMap<>();
        try {
            // TODO: chamar uma API de clima real aqui
            // Para demonstração, retornando dados fictícios
            result.put("status", "success");
            result.put("city", city);
            result.put("temperature", "25°C");
            result.put("conditions", "Parcialmente nublado");
            result.put("humidity", "72%");
            result.put("wind", "15 km/h NE");
            return result;

        } catch (Exception e) {
            result.put("status", "error");
            result.put("city", city);
            result.put("message", "Falha ao buscar clima: " + e.getMessage());
            return result;
        }
    }
}
```

#### Passo 2 — Registre a Skill em `SearchAgent.java`

Abra `src/main/java/com/gazapps/agent/SearchAgent.java` e adicione sua ferramenta à lista `.tools(...)`:

```java
import com.gazapps.skills.WeatherSkill;
// ... imports existentes ...

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
                    // ✅ SUA NOVA SKILL:
                    com.google.adk.tools.FunctionTool.create(WeatherSkill.class, "getWeather")
            )
            .build();
}
```

#### Passo 3 — (Opcional) Atualize as instruções do agente

Em `SearchAgent.java`, adicione uma linha ao bloco `instruction(...)` explicando quando o Gemini deve usar sua nova ferramenta:

```java
.instruction("""
        ...regras existentes...
        - getWeather(city): busca o clima atual de uma cidade. Use para qualquer pergunta sobre clima.
        """)
```

#### Passo 4 — Execute e teste

```bash
mvn compile exec:java
```

Pergunte: *"Como está o tempo no Rio de Janeiro agora?"*  
O Vincenzo chamará `getWeather("Rio de Janeiro")` automaticamente.

---

## Regras de Design de Skills

| Regra | Motivo |
|---|---|
| Os métodos devem ser `public static` | `FunctionTool.create` do ADK requer métodos estáticos |
| Retorne `Map<String, String>` | Serialização mais simples para ADK → Gemini |
| Sempre inclua o campo `status` (`"success"` ou `"error"`) | Permite ao LLM detectar falhas |
| Mantenha as descrições do `@Schema` claras e práticas | O Gemini as usa para decidir quando chamar cada ferramenta |
| Seja específico sobre **quando** usar a ferramenta no `@Schema` | Evita que o Gemini a invoque incorretamente |
| Trate todas as exceções internamente | Nunca deixe uma Skill lançar exceções para o agente |

---

## Múltiplos Métodos por Classe de Skill

Uma única classe de Skill pode expor múltiplas ferramentas. Exemplo:

```java
public class WeatherSkill {

    @Schema(description = "Obter o clima atual de uma cidade.")
    public static Map<String, String> getWeather(
            @Schema(name = "city", description = "Nome da cidade") String city) {
        // ...
    }

    @Schema(description = "Obter previsão do tempo de 7 dias para uma cidade.")
    public static Map<String, String> getWeatherForecast(
            @Schema(name = "city", description = "Nome da cidade") String city,
            @Schema(name = "days", description = "Número de dias de previsão (1-7)") String days) {
        // ...
    }
}
```

Registre cada método individualmente:

```java
com.google.adk.tools.FunctionTool.create(WeatherSkill.class, "getWeather"),
com.google.adk.tools.FunctionTool.create(WeatherSkill.class, "getWeatherForecast"),
```

---

## Usando `BrowserService` na Sua Skill

Se sua ferramenta precisa abrir uma página web, use o `BrowserService` compartilhado em vez de criar uma nova instância do Playwright. Isso garante o reuso eficiente do navegador.

```java
import com.gazapps.services.BrowserService;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

public class MinhaSkill {

    public static Map<String, String> minhaFerramenta(
            @Schema(name = "url", description = "URL a visitar") String url) {

        return BrowserService.execute(page -> {
            page.navigate(url, new Page.NavigateOptions().setTimeout(20_000));
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            String texto = page.innerText("body");

            Map<String, String> result = new HashMap<>();
            result.put("status", "success");
            result.put("content", texto);
            return result;
        });
    }
}
```

---

## Adicionando Retry a uma Skill de Browser

Para skills de browser **somente-leitura / idempotentes** (sem envio de formulários, sem efeitos colaterais), envolva a chamada `BrowserService.execute(...)` com `RetryUtils.withRetry(...)` para repetir automaticamente em caso de erros transitórios de rede ou timeout:

```java
import com.gazapps.config.AppConfig;
import com.gazapps.util.BrowserErrors;
import com.gazapps.util.RetryUtils;

public class MinhaSkill {

    public static Map<String, String> minhaFerramenta(String url) {
        try {
            return RetryUtils.withRetry(
                    "MinhaSkill.minhaFerramenta",
                    AppConfig.RETRY_MAX_ATTEMPTS,
                    AppConfig.RETRY_INITIAL_DELAY_MS,
                    () -> BrowserService.execute(page -> {
                        try {
                            // ... seu código de browser ...
                            Map<String, String> result = new HashMap<>();
                            result.put("status", "success");
                            return result;
                        } catch (Exception e) {
                            Map<String, String> error = new HashMap<>();
                            error.put("status", "error");
                            error.put("error_type", BrowserErrors.classify(e)); // ← classificar
                            error.put("message", "Falha: " + e.getMessage());
                            return error;
                        }
                    }),
                    result -> "error".equals(result.get("status")) // ← predicado de retry
            );
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("status", "error");
            error.put("error_type", BrowserErrors.classify(e));
            error.put("message", "Falha após retries: " + e.getMessage());
            return error;
        }
    }
}
```

> **NÃO adicione retry a skills com efeitos colaterais** (envio de formulários, pagamentos, escritas) — um retry poderia duplicar a ação. Para essas, adicione apenas a classificação `error_type` no bloco catch.

---

## Classificando Erros com `BrowserErrors`

Sempre adicione `error_type` aos mapas de erro para que o LLM possa fornecer mensagens de falha precisas:

```java
import com.gazapps.util.BrowserErrors;

// Em qualquer bloco catch:
error.put("error_type", BrowserErrors.classify(e));
```

`BrowserErrors.classify(Exception)` retorna um dos seguintes valores:

| Valor | Quando |
|---|---|
| `"timeout"` | Navegação ou resposta excedeu o tempo limite |
| `"network"` | Falha de DNS ou conexão recusada |
| `"blocked"` | HTTP 403/401 ou detecção de bot |
| `"unknown"` | Qualquer outra exceção |

---

## Usando `LogService` na Sua Skill

Todas as Skills devem registrar logs usando o `LogService` compartilhado:

```java
import com.gazapps.logging.LogService;

public class MinhaSkill {

    private static final LogService LOG = LogService.getInstance();

    public static Map<String, String> minhaFerramenta(String input) {
        LOG.section("TOOL CALL: minhaFerramenta");
        LOG.info("MinhaSkill", "Input: " + input);
        long inicio = System.currentTimeMillis();

        // ... realizar o trabalho ...

        LOG.timing("MinhaSkill", "minhaFerramenta total", System.currentTimeMillis() - inicio);
        return resultado;
    }
}
```

---

## Checklist Rápido

```
[ ] Criar: src/main/java/com/gazapps/skills/SuaSkill.java
[ ] Método é public static
[ ] Anotar classe/método com @Schema (descrições claras)
[ ] Retornar Map<String, String> com chave "status"
[ ] Capturar todas as exceções e retornar mapa de erro
[ ] Adicionar error_type via BrowserErrors.classify(e) em todos os blocos catch
[ ] Se idempotente (somente-leitura), envolver com RetryUtils.withRetry(...)
[ ] Registrar em SearchAgent.java com FunctionTool.create(...)
[ ] (Opcional) Adicionar dica de uso nas instruções do agente
[ ] mvn compile exec:java  →  testar
```
