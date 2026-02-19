# =============================================================================
#  Vincenzo — AI Internet Search Assistant
#  Multi-stage build: fat JAR + Chromium browser → minimal runtime image
#
#  Build:  docker compose up -d   (or: docker build -t vincenzo .)
#  Run:    docker compose up -d
#
#  Required environment variables (set in .env or docker-compose.yml):
#    GOOGLE_API_KEY       — Google AI Studio API key
#    TELEGRAM_BOT_TOKEN   — Telegram bot token from @BotFather (if using Telegram mode)
# =============================================================================

# ── Stage 1: build fat JAR + install Chromium browser ────────────────────────
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /build

# Install Maven
RUN apt-get update && apt-get install -y --no-install-recommends maven \
    && rm -rf /var/lib/apt/lists/*

# Download Maven dependencies first — this layer is cached unless pom.xml changes
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Build the fat JAR (maven-shade-plugin bundles all dependencies)
COPY src ./src
RUN mvn clean package -DskipTests -q

# Install the Playwright Chromium browser into /root/.cache/ms-playwright
# Using the fat JAR's bundled Playwright CLI
RUN java -cp target/ai-internet-search-1.0.0-SNAPSHOT.jar \
    com.microsoft.playwright.CLI install chromium

# ── Stage 2: minimal runtime image ───────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# System libraries required by Chromium headless on Linux
# (Playwright will not start without these)
RUN apt-get update && apt-get install -y --no-install-recommends \
    libnss3 \
    libatk1.0-0 \
    libatk-bridge2.0-0 \
    libcups2 \
    libdrm2 \
    libxkbcommon0 \
    libxcomposite1 \
    libxdamage1 \
    libxfixes3 \
    libxrandr2 \
    libgbm1 \
    libasound2 \
    && rm -rf /var/lib/apt/lists/*

# Fat JAR — contains all Java dependencies (~100-150 MB)
COPY --from=builder /build/target/ai-internet-search-1.0.0-SNAPSHOT.jar app.jar

# Chromium browser binary installed in the builder stage
COPY --from=builder /root/.cache/ms-playwright /root/.cache/ms-playwright

# Runtime directories (logs and work are mounted as volumes via docker-compose)
RUN mkdir -p logs work/screenshots work/downloads

ENTRYPOINT ["java", "-jar", "app.jar"]
