package com.gazapps.services;

import com.gazapps.config.AppConfig;
import com.gazapps.logging.LogService;
import com.microsoft.playwright.*;

import java.util.Map;
import java.util.function.Function;

/**
 * Handles Playwright browser creation and execution with anti-detection
 * measures.
 * Follows KISS & DRY principles by centralizing browser logic.
 */
public class BrowserService {

    private static final LogService LOG = LogService.getInstance();

    /**
     * Executes a browser action within a stealth Playwright context and returns a
     * result.
     * Automatically handles resource cleanup (try-with-resources).
     *
     * @param action The function to execute with the page.
     * @param <T>    The return type.
     * @return The result of the action.
     */
    public static <T> T execute(Function<Page, T> action) {
        try (Playwright playwright = Playwright.create()) {
            BrowserContext context = launchStealthContext(playwright);
            Page page = context.newPage();

            // Setup centralized logging
            page.onRequest(req -> {
                if (req.isNavigationRequest())
                    LOG.debug("BrowserService", "[NAV→] " + req.method() + " " + req.url());
            });
            page.onResponse(res -> {
                if (res.request().isNavigationRequest()) {
                    int status = res.status();
                    if (status >= 400)
                        LOG.warn("BrowserService", "[NAV←] HTTP " + status + " " + res.url());
                    else
                        LOG.debug("BrowserService", "[NAV←] HTTP " + status + " " + res.url());
                }
            });

            T result = action.apply(page);
            context.close();
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Browser execution failed: " + e.getMessage(), e);
        }
    }

    private static BrowserContext launchStealthContext(Playwright playwright) {
        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setArgs(java.util.List.of(
                        "--disable-blink-features=AutomationControlled",
                        "--no-sandbox",
                        "--disable-setuid-sandbox"));

        Browser browser = playwright.chromium().launch(launchOptions);

        Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                .setUserAgent(AppConfig.USER_AGENT)
                .setLocale(AppConfig.LOCALE)
                .setExtraHTTPHeaders(Map.of(
                        "Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8",
                        "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                        "sec-ch-ua", "\"Not(A:Brand\";v=\"99\", \"Google Chrome\";v=\"133\", \"Chromium\";v=\"133\"",
                        "sec-ch-ua-mobile", "?0",
                        "sec-ch-ua-platform", "\"macOS\"",
                        "Upgrade-Insecure-Requests", "1"));

        BrowserContext context = browser.newContext(contextOptions);

        // Stealth script to mask webdriver
        context.addInitScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined});");

        return context;
    }
}
