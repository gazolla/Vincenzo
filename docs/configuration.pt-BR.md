# ⚙️ Referência de Configuração

[🇺🇸 English](configuration.md) · 🇧🇷 Português

Toda a configuração fica em `src/main/resources/application.properties`.  
Após compilar o fat JAR, você pode substituir as configurações colocando um arquivo `application.properties` no **mesmo diretório do JAR** (sem necessidade de recompilar).

---

## Índice

1. [Logging](#logging)
2. [Diretórios de Trabalho](#diretórios-de-trabalho)
3. [Navegador e HTTP](#navegador-e-http)
4. [Timeouts do Playwright](#timeouts-do-playwright)
5. [Limites de Conteúdo](#limites-de-conteúdo)
6. [FormSkill](#formskill)
7. [PdfSkill](#pdfskill)
8. [LLM / Modelo](#llm--modelo)
9. [Política de Retry](#política-de-retry)
10. [Locale e Região](#locale-e-região)
11. [Gerenciamento de Logs](#gerenciamento-de-logs)
12. [Cache de Busca](#cache-de-busca)
13. [Pipeline de Busca](#pipeline-de-busca)
14. [RssSkill](#rssskill)
15. [SchedulerSkill](#schedulerskill)
16. [NotificationSkill](#notificationskill)
17. [MemorySkill](#memoryskill)
18. [Telegram](#telegram)

---

## Logging

Controla a saída do SLF4J SimpleLogger. Aplicado na inicialização da JVM, antes de qualquer log.

```properties
# Nível de log: trace | debug | info | warn | error
slf4j.defaultLogLevel=warn

# Exibir data/hora nas linhas de log
slf4j.showDateTime=false

# Exibir nome da thread nas linhas de log
slf4j.showThreadName=false

# Exibir o nome da classe logger nas linhas de log
slf4j.showLogName=false
```

> **Dica:** Use `slf4j.defaultLogLevel=debug` durante o desenvolvimento para ver todos os eventos ADK/gRPC.

---

## Diretórios de Trabalho

Diretórios onde o Vincenzo armazena os arquivos gerados. Os caminhos são relativos ao diretório de trabalho (onde o JAR é executado).

```properties
# Diretório para screenshots do navegador
work.screenshots.dir=work/screenshots

# Diretório para downloads do Playwright
work.downloads.dir=work/downloads
```

Ambos os diretórios são criados automaticamente se não existirem.

---

## Navegador e HTTP

```properties
# User-Agent enviado em todas as requisições do navegador
browser.user-agent=Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) ...

# Timeout para a API JSON de Resposta Instantânea do DuckDuckGo (segundos)
http.connect.timeout.seconds=8
```

---

## Timeouts do Playwright

Timeouts de navegação para o Playwright (valores em **milissegundos**).

```properties
# Timeout de navegação do fetchPageContent
fetch.page.navigate.timeout.ms=20000

# Timeout de navegação do screenshotPage
screenshot.navigate.timeout.ms=15000

# Timeout de navegação do summarizeUrl (aumentado — artigos longos com muitos assets)
summarize.navigate.timeout.ms=25000

# Timeout de navegação do extractStructuredData
extract.navigate.timeout.ms=20000

# Timeout de navegação do fillFormAndSubmit (aumentado — JS pesado e redirecionamentos pós-submit)
form.navigate.timeout.ms=30000
```

---

## Limites de Conteúdo

Controla quantos caracteres são enviados ao LLM para cada skill (para não exceder a janela de contexto do modelo).

```properties
# Máximo de caracteres retornados pelo fetchPageContent
fetch.page.max.chars=5000

# Máximo de caracteres dos resultados HTML do DuckDuckGo
ddg.page.text.max.chars=6000

# Máximo de caracteres dos resultados do Bing
bing.page.text.max.chars=6000

# Máximo de caracteres retornados pelo summarizeUrl
summarize.max.chars=8000

# Máximo de itens retornados pelo extractStructuredData
extract.max.items=50
```

---

## FormSkill

Configurações para `fillFormAndSubmit`:

```properties
# Tempo de espera após envio do formulário (ms) — aguarda a página carregar antes de ler os resultados
form.after.submit.wait.ms=3000

# Máximo de caracteres retornados da página de resultado após o envio
form.result.max.chars=5000
```

---

## PdfSkill

Configurações para `readPdf`:

```properties
# Timeout de conexão HTTP para download de PDFs (segundos)
pdf.http.timeout.seconds=30

# Máximo de caracteres extraídos do PDF
pdf.max.chars=10000
```

---

## LLM / Modelo

```properties
# Modelo Gemini usado pelo LlmAgent
# Modelos disponíveis: gemini-2.5-flash, gemini-2.0-flash, gemini-1.5-pro, etc.
# Veja: https://ai.google.dev/gemini-api/docs/models
llm.model=gemini-2.5-flash
```

---

## Política de Retry

O Vincenzo tenta novamente automaticamente operações de browser com falha usando backoff exponencial.

```properties
# Número total de tentativas (1 = sem retry; 3 = inicial + 2 retries)
retry.max.attempts=3

# Atraso inicial entre tentativas em milissegundos
# O atraso dobra a cada retry: 500ms → 1000ms → 2000ms
retry.initial.delay.ms=500
```

---

## Locale e Região

```properties
# Locale BCP 47 para o contexto do navegador Playwright
# Exemplos: pt-BR, en-US, es-ES, fr-FR
browser.locale=pt-BR

# Região do DuckDuckGo para o parâmetro kl=
# Exemplos: br-pt (Brasil/Português), us-en (EUA/Inglês), de-de, fr-fr
ddg.region=br-pt
```

---

## Gerenciamento de Logs

O Vincenzo escreve arquivos de log de sessão estruturados no diretório `logs/`.

```properties
# Número máximo de arquivos de log de sessão a manter
# O mais antigo é deletado quando o limite é excedido
log.max.files=10

# Tamanho máximo por arquivo de log em KB (0 = sem rotação)
log.max.size.kb=512
```

---

## Cache de Busca

Um cache LRU em memória para resultados de `searchWeb`, evitando requisições redundantes ao navegador.

```properties
# Habilitar ou desabilitar o cache de resultados de busca
search.cache.enabled=true

# Máximo de queries distintas a manter em memória (evicção LRU)
search.cache.max.size=500

# Tempo de vida de cada entrada no cache (minutos)
search.cache.ttl.minutes=60
```

---

## Pipeline de Busca

Controla como DuckDuckGo e Bing são orquestrados.

```properties
# Executar DDG HTML e Bing em paralelo em vez de sequencialmente
# Padrão: false (sequencial é mais seguro e menos intensivo em recursos)
search.parallel.enabled=false

# Timeout para busca paralela (ms)
search.parallel.timeout.ms=25000

# Circuit Breaker do DDG HTML
# Após N falhas consecutivas, o DDG HTML é ignorado por reset.ms milissegundos
search.circuit.ddg.failure.threshold=5
search.circuit.ddg.reset.ms=30000
```

---

## RssSkill

Configurações para `discoverFeed`, `readFeed` e `searchInFeed`:

```properties
# Timeout de conexão HTTP para buscas de feeds RSS/Atom (segundos)
rss.fetch.timeout.seconds=15

# Número máximo de itens retornados pelo readFeed
rss.max.items=20

# Máximo de caracteres da descrição de cada item do feed
rss.max.description.chars=500
```

---

## SchedulerSkill

Configurações para `scheduleMonitor`, `listMonitors` e `cancelMonitor`:

```properties
# Número máximo de jobs de monitor simultâneos
scheduler.max.jobs=20

# Intervalo mínimo entre execuções de jobs (minutos)
scheduler.min.interval.minutes=5

# Caminho do arquivo JSON para persistir jobs agendados (relativo ao diretório de trabalho)
scheduler.jobs.file=work/scheduler-jobs.json
```

---

## NotificationSkill

Configurações para `sendNotification`, `listPendingNotifications` e `markAsRead`:

```properties
# chat_id do Telegram para notificações proativas (obrigatório no modo Telegram)
notification.telegram.chat.id=

# Máximo de notificações na fila em memória (fallback CLI)
notification.queue.max.size=100
```

> No modo `cli`, as notificações são armazenadas em memória e recuperadas via `listPendingNotifications`.
> No modo `telegram`, são enviadas diretamente ao chat especificado em `notification.telegram.chat.id`.

---

## MemorySkill

Configurações para `saveMemory`, `retrieveMemory`, `listMemories`, `updateMemory` e `deleteMemory`:

```properties
# Caminho do arquivo JSON para persistir entradas de memória (relativo ao diretório de trabalho)
memory.storage.file=work/memory-store.json

# Número máximo de entradas de memória armazenadas (novos saves são rejeitados ao exceder o limite)
memory.max.items=500
```

---

## Telegram

```properties
# Modo de interface: "cli" (terminal) ou "telegram" (bot)
interface.mode=cli

# Token do bot Telegram (do @BotFather)
# Recomendado: definir via variável de ambiente TELEGRAM_BOT_TOKEN
telegram.bot.token=

# Modo de atualização do Telegram: "polling" (sem servidor) ou "webhook"
telegram.mode=polling

# Modo webhook: porta em que o servidor HTTP embutido escuta
telegram.webhook.port=8443

# Modo webhook: URL HTTPS pública para onde o Telegram enviará as atualizações
telegram.webhook.url=
```

### Ativando o modo Telegram

1. Crie um bot via [@BotFather](https://t.me/botfather) e copie o token.
2. Defina o token no `.env`:

   ```env
   TELEGRAM_BOT_TOKEN=123456:ABC-DEF...
   ```

3. Altere o modo de interface no `application.properties`:

   ```properties
   interface.mode=telegram
   telegram.mode=polling
   ```

4. Reinicie o Vincenzo.

> Use o modo `polling` para testes rápidos e implantações em VPS sem domínio público.  
> Use o modo `webhook` para configurações de produção com um endpoint HTTPS público.
