# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

No Maven wrapper is present — use the system `mvn` directly.

```bash
# Run the application
mvn spring-boot:run

# Build a standalone JAR
mvn package

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=StatisticsServiceTest

# Run a single test method
mvn test -Dtest=StatisticsServiceTest#getTotalWealth_withNoData_returnsZero

# Compile only (fast syntax check)
mvn compile
```

The app starts on **http://localhost:8080**. On first launch it prompts for a name that becomes part of the H2 database filename. The database lives in `%APPDATA%\Wealth\` (Windows), `~/Library/Application Support/Wealth/` (macOS), or `~/.config/wealth/` (Linux).

## Architecture Overview

**Stack:** Spring Boot 4.0.6, Java 21, Thymeleaf, Spring Data JPA, H2 (file-based), no JavaScript framework.

**Package layout:**
- `domain/` — JPA entities
- `repository/` — Spring Data JPA interfaces
- `service/` — business logic
- `controller/` — Spring MVC controllers
- `dto/` — view data transfer objects (`WealthPosition`, `StatisticsGroup`, `MonthlyWealth`)
- `templates/` — Thymeleaf templates, one subdirectory per domain area

**Cross-cutting concerns in every request:**
`CurrencyAdvice` (`@ControllerAdvice`) injects three model attributes into every view: `eurToDisplayRate` (BigDecimal multiplier), `displayCurrency` (3-letter ISO code), and `currentUri`. Both the currency and locale are stored as cookies (`wealth-currency`, `wealth-lang`). Language is switched via `?lang=de|en`.

## Data Model and Key Patterns

**All monetary values are stored and calculated in EUR.** `ExchangeRateService.toEur()` converts any non-EUR value; `eurToDisplayRate` converts EUR to the user's display currency in templates.

**Historized entities use latest-before-date semantics:**
- `AssetQuantity` — quantity of an asset in a depot on a given date; `findFirstByAssetAndDepotOrderByDateDesc` returns current value
- `AccountBalance` — balance of an account on a given date; `findFirstByAccountOrderByDateDesc` returns current value
- `PriceHistory` — price of an asset on a given date; auto-written by `PriceService` on every price refresh and on the 1st of each month via scheduler

**Coin quantity IS historized** via `CoinQuantity` (coin, date, quantity). `Coin.quantity` remains the current value for display; saving a coin auto-records a `CoinQuantity` entry if the quantity changed. The quantity history page lives at `/coins/{id}/quantities`. `getWealthHistory()` uses `CoinQuantity.floorEntry(date)` with fallback to `Coin.quantity` for coins not yet migrated. For historical calculations across all entities, load all records once and use `TreeMap.floorEntry(date)` — see `getWealthHistory()` for the pattern.

**Scheduled jobs in `PriceService`:**
- Daily at 18:00: refresh all asset prices from Yahoo Finance (`updatePrices`)
- 1st of each month at 08:00: snapshot current prices into `PriceHistory` (`saveMonthlyHistory`)
- Exchange rates refreshed at startup and weekdays 18:30 (`ExchangeRateService.refresh`)

## Controller / Service / Template Conventions

Controllers are plain `@Controller` (not REST), POST actions redirect with `RedirectAttributes` flash attributes for success/error messages displayed once. Destructive operations use HTML `<form method="post">` with `onsubmit=return confirm(...)`.

Every list page table has an **Actions dropdown** (`<div class="dropdown">` + Bootstrap `data-bs-toggle="dropdown"`) for per-row actions. Follow this pattern for consistency.

i18n keys live in `messages_de.properties` and `messages_en.properties`. New keys must be added to both files. Templates use `th:text="#{key}"` and `th:attr="aria-label=#{key}"`.

**Asset search** (`AssetSearchService`) calls the Yahoo Finance search API and is used on the asset create form.

**Coin price logic** (`CoinService.valueEur`): if a coin has a linked `Asset`, uses that asset's price (EUR/oz); otherwise uses live spot prices fetched from Yahoo Finance (`GC=F`, `SI=F`, `PL=F`).
