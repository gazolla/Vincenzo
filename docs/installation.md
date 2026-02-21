# 🚀 Installation Guide

🇺🇸 English · [🇧🇷 Português](installation.pt-BR.md)

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Local Installation](#local-installation)
3. [JAR File Generation](#jar-file-generation)
4. [VPS Deployment (with Docker)](#vps-deployment-with-docker)
5. [VPS Deployment (bare JAR + systemd)](#vps-deployment-bare-jar--systemd)

---

## Prerequisites

| Requirement | Version | Notes |
|---|---|---|
| Java | 21+ | Eclipse Temurin or OpenJDK |
| Maven | 3.9+ | For building |
| Google AI Studio API key | — | [Get yours here](https://aistudio.google.com/app/apikey) |
| Telegram bot token (optional) | — | Only needed for Telegram mode |

---

## Local Installation

### 1. Clone the repository

```bash
git clone https://github.com/your-org/vincenzo.git
cd vincenzo
```

### 2. Configure environment variables

```bash
# Copy the example environment file
cp .env.example .env

# Open and fill in your keys
nano .env
```

The `.env` file should look like:

```env
GOOGLE_API_KEY=AIza...your-key-here...
TELEGRAM_BOT_TOKEN=123456:ABC...  # Only if using Telegram mode
```

> **Important:** Never commit `.env` to Git. It is already listed in `.gitignore`.

### 3. Configure the application

The main configuration file is `src/main/resources/application.properties`.
Edit it to adjust the model, locale, interface mode, and more.
See [configuration.md](configuration.md) for a full reference.

### 4. Install Playwright browsers

Vincenzo uses Chromium to browse the web. Install it once:

```bash
mvn exec:java -e \
  -Dexec.mainClass=com.microsoft.playwright.CLI \
  -Dexec.args=install
```

> This downloads ~150 MB of Chromium browser binaries and stores them in `~/.cache/ms-playwright`.

### 5. Export your API key and run

```bash
# Load your env vars (or export manually)
export GOOGLE_API_KEY="$(grep GOOGLE_API_KEY .env | cut -d= -f2)"

# Run in CLI mode
mvn compile exec:java
```

You should now see the interactive prompt:

```
🤖 Vincenzo: Hello! I'm Vincenzo, your AI assistant with internet access.
   What would you like to know?

You:
```

---

## JAR File Generation

Vincenzo uses the **Maven Shade Plugin** to produce a **fat/uber JAR** — a single JAR that includes all dependencies (Google ADK, Playwright, Telegram API, etc.).

### Build the fat JAR

```bash
mvn clean package -DskipTests
```

The output file will be:

```
target/ai-internet-search-1.0.0-SNAPSHOT.jar
```

### Install Playwright browsers for the JAR

After building the JAR, install the browser binaries using the bundled Playwright CLI:

```bash
java -cp target/ai-internet-search-1.0.0-SNAPSHOT.jar \
  com.microsoft.playwright.CLI install chromium
```

### Run the fat JAR

```bash
# Set your API key
export GOOGLE_API_KEY="your-key-here"

# Run
java -jar target/ai-internet-search-1.0.0-SNAPSHOT.jar
```

### Override application.properties at runtime

Place an `application.properties` file in the **same directory where you run the JAR**.  
It will automatically override the configuration bundled inside the JAR — no recompilation needed.

```bash
# Copy and edit the production config
cp src/main/resources/application.properties ./application.properties
nano application.properties
```

---

## VPS Deployment (with Docker)

This is the **recommended approach** for production VPS deployments.
It bundles Java, Chromium, and the fat JAR into a single container.

### Requirements on the VPS

- Docker Engine 24+
- Docker Compose v2+
- At least **2 GB RAM** (Chromium is memory-hungry)
- Outbound HTTP/HTTPS access

### Step-by-step

#### 1. Upload your project

```bash
# From your local machine
scp -r . user@your-vps:/opt/vincenzo
```

Or clone from Git directly on the VPS:

```bash
ssh user@your-vps
git clone https://github.com/your-org/vincenzo.git /opt/vincenzo
cd /opt/vincenzo
```

#### 2. Create the `.env` file on the VPS

```bash
cp .env.example .env
nano .env
# Fill in: GOOGLE_API_KEY and TELEGRAM_BOT_TOKEN
chmod 600 .env
```

#### 3. Copy and edit `application.properties`

```bash
cp src/main/resources/application.properties application.properties
nano application.properties
```

Set Telegram mode:

```properties
interface.mode=telegram
telegram.mode=polling
```

#### 4. Build and start the container

```bash
docker compose up -d --build
```

#### 5. View logs

```bash
docker compose logs -f
```

#### 6. Stop / restart

```bash
docker compose down        # stop
docker compose up -d       # restart
docker compose up -d --build   # rebuild after code changes
```

### Docker Compose overview

```yaml
services:
  vincenzo:
    build: .
    restart: unless-stopped
    environment:
      - GOOGLE_API_KEY=${GOOGLE_API_KEY}
      - TELEGRAM_BOT_TOKEN=${TELEGRAM_BOT_TOKEN}
    volumes:
      - ./logs:/app/logs                        # persistent logs
      - ./work:/app/work                        # screenshots/downloads
      - ./application.properties:/app/application.properties:ro
    mem_limit: 2g
```

---

## VPS Deployment (bare JAR + systemd)

Use this approach if you prefer not to use Docker and want **lower overhead**.

### 1. Install Java 21 on the VPS

```bash
# Ubuntu / Debian
sudo apt-get update
sudo apt-get install -y openjdk-21-jre-headless
java -version
```

### 2. Install Chromium system dependencies

```bash
sudo apt-get install -y \
  libnss3 libatk1.0-0 libatk-bridge2.0-0 \
  libcups2 libdrm2 libxkbcommon0 libxcomposite1 \
  libxdamage1 libxfixes3 libxrandr2 libgbm1 libasound2
```

### 3. Create the application directory

```bash
sudo useradd -r -s /bin/false vincenzo
sudo mkdir -p /opt/vincenzo
sudo chown vincenzo:vincenzo /opt/vincenzo
```

### 4. Upload the fat JAR

```bash
# Build locally first
mvn clean package -DskipTests

# Upload to VPS
scp target/ai-internet-search-1.0.0-SNAPSHOT.jar \
  user@your-vps:/opt/vincenzo/app.jar
```

### 5. Install Playwright Chromium on the VPS

```bash
sudo -u vincenzo java -cp /opt/vincenzo/app.jar \
  com.microsoft.playwright.CLI install chromium
```

By default, Playwright stores the browser in `~vincenzo/.cache/ms-playwright`.  
Update the service file if you want a different path.

### 6. Create the `.env` file

```bash
sudo nano /opt/vincenzo/.env
```

Content:

```env
GOOGLE_API_KEY=your-key-here
TELEGRAM_BOT_TOKEN=your-token
```

```bash
sudo chmod 600 /opt/vincenzo/.env
sudo chown vincenzo:vincenzo /opt/vincenzo/.env
```

### 7. Upload `application.properties`

```bash
scp src/main/resources/application.properties \
  user@your-vps:/opt/vincenzo/application.properties
```

Set Telegram mode if needed:

```properties
interface.mode=telegram
telegram.mode=polling
```

### 8. Install the systemd service

```bash
scp deploy/vincenzo.service user@your-vps:/tmp/

ssh user@your-vps
sudo cp /tmp/vincenzo.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now vincenzo
```

### 9. Monitor the service

```bash
sudo systemctl status vincenzo
sudo journalctl -u vincenzo -f       # live log stream
sudo systemctl restart vincenzo       # restart
sudo systemctl stop vincenzo          # stop
```
