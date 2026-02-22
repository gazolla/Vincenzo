<div align="center">

<img src="docs/vincenzo-robot.png" alt="Vincenzo Robot" width="200"/>

<!-- Salve a imagem do robô Vincenzo como docs/vincenzo-robot.png para exibi-la aqui -->

# Vincenzo

### Assistente de Busca com IA na Internet

**Um agente de IA autônomo movido pelo [Google ADK](https://google.github.io/adk-docs/) com capacidade de busca na web em tempo real**

[🇺🇸 English](README.md) · 🇧🇷 Português

</div>

---

## Sobre o Nome

**Vincenzo** foi inspirado pelo **V.I.N.CENT** (*Vital Information Necessary CENTralized*), o icônico robô do filme de ficção científica da Disney de 1979, **"The Black Hole" (O Buraco Negro)**. V.I.N.CENT encantou o público com sua inteligência, lealdade e bom humor — o tipo de robô que você sempre quis ter ao seu lado. Desde criança, sempre quis um robô assim. Bem… agora eu tenho. Conheça o **Vincenzo** 🤖

---

## O que é o Framework Vincenzo?

O Vincenzo é um **framework de IA conversacional em Java 21** construído sobre o [Google ADK (Agent Development Kit)](https://google.github.io/adk-docs/) que dá a um agente LLM alimentado pelo Gemini acesso a um **navegador web real** via [Microsoft Playwright](https://playwright.dev/java/).

Diferente de chatbots simples que respondem apenas a partir de dados de treinamento, o agente do Vincenzo:

- **Pesquisa na web** em tempo real via DuckDuckGo e Bing
- **Lê qualquer página web** completamente, extraindo texto limpo
- **Tira screenshots** de páginas
- **Extrai dados estruturados** de páginas usando seletores CSS (incluindo atributos HTML via sintaxe `|attr`)
- **Resume** qualquer artigo ou URL
- **Preenche e envia formulários HTML** em sites
- **Lê arquivos PDF** baixados da web
- **Lê feeds RSS/Atom** e os pesquisa por palavra-chave
- **Agenda monitores** que vigiam feeds ou páginas e notificam ao encontrar correspondências
- **Envia e gerencia notificações** (fila no CLI ou Telegram)
- **Armazena e recupera memória de longo prazo** entre sessões

O framework é construído em torno do conceito de **Skills** (Habilidades) — classes Java modulares que expõem ferramentas ao agente LLM via o mecanismo `@FunctionTool` do Google ADK. Cada nova capacidade que você dá ao Vincenzo é uma nova Skill.

## Documentação

| Seção | Link |
|---|---|
| 🚀 Instalação (Local e VPS) | [docs/installation.pt-BR.md](docs/installation.pt-BR.md) |
| ⚙️ Referência de Configuração | [docs/configuration.pt-BR.md](docs/configuration.pt-BR.md) |
| 🛠️ Criando Novas Skills | [docs/creating-skills.pt-BR.md](docs/creating-skills.pt-BR.md) |
| 🧪 Guia de Testes | [docs/testing.md](docs/testing.md) |

---

## ⚡ Início Rápido

```bash
# 1. Clone o projeto
git clone https://github.com/your-org/vincenzo.git
cd vincenzo

# 2. Defina sua chave de API do Google AI
export GOOGLE_API_KEY="sua-chave-aqui"

# 3. Instale os navegadores do Playwright
mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args=install

# 4. Execute
mvn compile exec:java
```

---

## Skills Integradas

| Método da Skill | Descrição |
|---|---|
| `searchWeb(query)` | Busca no DuckDuckGo (JSON + HTML) com fallback para o Bing |
| `fetchPageContent(url)` | Lê o conteúdo completo de qualquer página web |
| `screenshotPage(url, filename)` | Tira um screenshot de uma página web |
| `summarizeUrl(url)` | Busca e limpa uma página para resumo pelo LLM |
| `extractStructuredData(url, selectors)` | Extrai dados estruturados usando seletores CSS; use `selector\|attr` para atributos HTML |
| `fillFormAndSubmit(url, fields, selector)` | Preenche e envia formulários HTML |
| `readPdf(url)` | Baixa e extrai texto de arquivos PDF |
| `discoverFeed(url)` | Detecta automaticamente a URL do feed RSS/Atom de um site |
| `readFeed(url)` | Lê e retorna itens de um feed RSS/Atom |
| `searchInFeed(url, keyword)` | Pesquisa itens do feed por palavra-chave |
| `scheduleMonitor(url, keyword, interval)` | Agenda um monitor recorrente que notifica ao encontrar palavras-chave |
| `listMonitors()` | Lista todos os jobs de monitor agendados |
| `cancelMonitor(jobId)` | Cancela e remove um monitor agendado |
| `sendNotification(message)` | Envia uma notificação (fila CLI ou Telegram) |
| `listPendingNotifications()` | Lista notificações não lidas |
| `markAsRead(notifId)` | Marca uma notificação como lida |
| `saveMemory(content, tags)` | Salva uma entrada de memória para recuperação futura |
| `retrieveMemory(query)` | Pesquisa memórias armazenadas por consulta semântica |
| `listMemories()` | Lista todas as entradas de memória armazenadas |
| `updateMemory(id, content)` | Atualiza uma entrada de memória existente |
| `deleteMemory(id)` | Exclui uma entrada de memória |

---

## Modos de Interface

O Vincenzo suporta dois modos de interface configurados pelo `application.properties`:

| Modo | Descrição |
|---|---|
| `cli` (padrão) | Sessão de chat interativo no terminal |
| `telegram` | Bot do Telegram (polling ou webhook) |

---

## Pré-requisitos

- Java 21+
- Maven 3.9+
- Uma [chave de API do Google AI Studio](https://aistudio.google.com/app/apikey)
- (Opcional) Um token de bot do Telegram via [@BotFather](https://t.me/botfather) para o modo Telegram

---

## Licença

Licença MIT — veja [LICENSE](LICENSE) para detalhes.
