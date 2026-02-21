# 🚀 Guia de Instalação

[🇺🇸 English](installation.md) · 🇧🇷 Português

---

## Índice

1. [Pré-requisitos](#pré-requisitos)
2. [Instalação Local](#instalação-local)
3. [Geração do Arquivo JAR](#geração-do-arquivo-jar)
4. [Implantação em VPS (com Docker)](#implantação-em-vps-com-docker)
5. [Implantação em VPS (JAR + systemd)](#implantação-em-vps-jar--systemd)

---

## Pré-requisitos

| Requisito | Versão | Observações |
|---|---|---|
| Java | 21+ | Eclipse Temurin ou OpenJDK |
| Maven | 3.9+ | Para compilar |
| Chave de API do Google AI Studio | — | [Obtenha a sua aqui](https://aistudio.google.com/app/apikey) |
| Token do bot Telegram (opcional) | — | Necessário apenas no modo Telegram |

---

## Instalação Local

### 1. Clone o repositório

```bash
git clone https://github.com/your-org/vincenzo.git
cd vincenzo
```

### 2. Configure as variáveis de ambiente

```bash
# Copie o arquivo de exemplo
cp .env.example .env

# Abra e preencha suas chaves
nano .env
```

O arquivo `.env` deve ter este formato:

```env
GOOGLE_API_KEY=AIza...sua-chave-aqui...
TELEGRAM_BOT_TOKEN=123456:ABC...  # Apenas se usar o modo Telegram
```

> **Importante:** Nunca faça commit do arquivo `.env` no Git. Ele já está listado no `.gitignore`.

### 3. Configure a aplicação

O arquivo principal de configuração é `src/main/resources/application.properties`.
Edite-o para ajustar o modelo, o locale, o modo de interface e mais.
Consulte [configuration.pt-BR.md](configuration.pt-BR.md) para a referência completa.

### 4. Instale os navegadores do Playwright

O Vincenzo usa o Chromium para navegar na web. Instale-o uma única vez:

```bash
mvn exec:java -e \
  -Dexec.mainClass=com.microsoft.playwright.CLI \
  -Dexec.args=install
```

> Isso baixa ~150 MB de binários do navegador Chromium e os armazena em `~/.cache/ms-playwright`.

### 5. Exporte sua chave de API e execute

```bash
# Carregue as variáveis de ambiente (ou exporte manualmente)
export GOOGLE_API_KEY="$(grep GOOGLE_API_KEY .env | cut -d= -f2)"

# Execute em modo CLI
mvn compile exec:java
```

Você verá o prompt interativo:

```
🤖 Vincenzo: Olá! Sou o Vincenzo, seu assistente de IA com acesso à internet.
   O que você quer saber?

Você:
```

---

## Geração do Arquivo JAR

O Vincenzo usa o **Maven Shade Plugin** para gerar um **fat/uber JAR** — um único JAR que inclui todas as dependências (Google ADK, Playwright, API do Telegram, etc.).

### Compilar o fat JAR

```bash
mvn clean package -DskipTests
```

O arquivo de saída será:

```
target/ai-internet-search-1.0.0-SNAPSHOT.jar
```

### Instalar os navegadores para o JAR

Após compilar, instale os binários do Playwright usando a CLI embutida no JAR:

```bash
java -cp target/ai-internet-search-1.0.0-SNAPSHOT.jar \
  com.microsoft.playwright.CLI install chromium
```

### Executar o fat JAR

```bash
# Defina sua chave de API
export GOOGLE_API_KEY="sua-chave-aqui"

# Execute
java -jar target/ai-internet-search-1.0.0-SNAPSHOT.jar
```

### Sobrescrever o application.properties em tempo de execução

Coloque um arquivo `application.properties` no **mesmo diretório de onde você executa o JAR**.  
Ele substituirá automaticamente a configuração embutida — sem necessidade de recompilar.

```bash
# Copie e edite a configuração de produção
cp src/main/resources/application.properties ./application.properties
nano application.properties
```

---

## Implantação em VPS (com Docker)

Esta é a **abordagem recomendada** para implantação em produção.
Ela empacota Java, Chromium e o fat JAR em um único container.

### Requisitos no VPS

- Docker Engine 24+
- Docker Compose v2+
- Pelo menos **2 GB de RAM** (o Chromium consome bastante memória)
- Acesso de saída HTTP/HTTPS

### Passo a Passo

#### 1. Envie o projeto para o VPS

```bash
# Da sua máquina local
scp -r . usuario@seu-vps:/opt/vincenzo
```

Ou clone diretamente no VPS:

```bash
ssh usuario@seu-vps
git clone https://github.com/your-org/vincenzo.git /opt/vincenzo
cd /opt/vincenzo
```

#### 2. Crie o arquivo `.env` no VPS

```bash
cp .env.example .env
nano .env
# Preencha: GOOGLE_API_KEY e TELEGRAM_BOT_TOKEN
chmod 600 .env
```

#### 3. Copie e edite o `application.properties`

```bash
cp src/main/resources/application.properties application.properties
nano application.properties
```

Defina o modo Telegram:

```properties
interface.mode=telegram
telegram.mode=polling
```

#### 4. Compile e inicie o container

```bash
docker compose up -d --build
```

#### 5. Visualize os logs

```bash
docker compose logs -f
```

#### 6. Parar / Reiniciar

```bash
docker compose down        # parar
docker compose up -d       # reiniciar
docker compose up -d --build   # recompilar após mudanças no código
```

### Visão geral do Docker Compose

```yaml
services:
  vincenzo:
    build: .
    restart: unless-stopped
    environment:
      - GOOGLE_API_KEY=${GOOGLE_API_KEY}
      - TELEGRAM_BOT_TOKEN=${TELEGRAM_BOT_TOKEN}
    volumes:
      - ./logs:/app/logs                        # logs persistentes
      - ./work:/app/work                        # screenshots/downloads
      - ./application.properties:/app/application.properties:ro
    mem_limit: 2g
```

---

## Implantação em VPS (JAR + systemd)

Use esta abordagem se preferir não usar Docker e quiser **menor sobrecarga de recursos**.

### 1. Instale o Java 21 no VPS

```bash
# Ubuntu / Debian
sudo apt-get update
sudo apt-get install -y openjdk-21-jre-headless
java -version
```

### 2. Instale as dependências do sistema Chromium

```bash
sudo apt-get install -y \
  libnss3 libatk1.0-0 libatk-bridge2.0-0 \
  libcups2 libdrm2 libxkbcommon0 libxcomposite1 \
  libxdamage1 libxfixes3 libxrandr2 libgbm1 libasound2
```

### 3. Crie o diretório da aplicação

```bash
sudo useradd -r -s /bin/false vincenzo
sudo mkdir -p /opt/vincenzo
sudo chown vincenzo:vincenzo /opt/vincenzo
```

### 4. Envie o fat JAR

```bash
# Compile localmente primeiro
mvn clean package -DskipTests

# Envie para o VPS
scp target/ai-internet-search-1.0.0-SNAPSHOT.jar \
  usuario@seu-vps:/opt/vincenzo/app.jar
```

### 5. Instale o Chromium via Playwright no VPS

```bash
sudo -u vincenzo java -cp /opt/vincenzo/app.jar \
  com.microsoft.playwright.CLI install chromium
```

### 6. Crie o arquivo `.env`

```bash
sudo nano /opt/vincenzo/.env
```

Conteúdo:

```env
GOOGLE_API_KEY=sua-chave-aqui
TELEGRAM_BOT_TOKEN=seu-token
```

```bash
sudo chmod 600 /opt/vincenzo/.env
sudo chown vincenzo:vincenzo /opt/vincenzo/.env
```

### 7. Envie o `application.properties`

```bash
scp src/main/resources/application.properties \
  usuario@seu-vps:/opt/vincenzo/application.properties
```

Defina o modo Telegram se necessário:

```properties
interface.mode=telegram
telegram.mode=polling
```

### 8. Instale o serviço systemd

```bash
scp deploy/vincenzo.service usuario@seu-vps:/tmp/

ssh usuario@seu-vps
sudo cp /tmp/vincenzo.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now vincenzo
```

### 9. Monitore o serviço

```bash
sudo systemctl status vincenzo
sudo journalctl -u vincenzo -f       # stream de log em tempo real
sudo systemctl restart vincenzo       # reiniciar
sudo systemctl stop vincenzo          # parar
```
