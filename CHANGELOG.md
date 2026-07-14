# Changelog

All notable changes to this project will be documented in this file.

## [1.0.12] – 2026-07-14

### Added
- Self-managed classification criteria for securities, replacing the fixed Kategorie/Wertpapierart/Allocation/Ausschüttung/Index enums: new criteria can be created and managed via `/criteria` and assigned to any security
- Custom criteria can now also be assigned to accounts
- Generic statistics view (`/statistics/by-criteria`) grouping wealth by any classification criterion (system or custom); the statistics menu now lists one entry per criterion directly, replacing the old fixed report pages
- Pie chart added to all group-based statistics views (overview, by-index, by-type, by-allocation, by-criteria), built with a self-contained SVG (no external chart library)
- Color assigned automatically to each criterion (customizable via a picker), shown consistently on badges across the asset list, dashboard and criteria pages
- Unified "Eigenschaften"/"Properties" badge column on the asset list and dashboard showing all assigned criteria (system and custom) in one place
- Browser now opens automatically when the application starts
- Offline, cryptographically signed license key system: Settings → License to view status and enter a key; gates Coin management and Custom Criteria without ever deleting data on a lapsed/missing license
- "Wittmann" classification criterion (Liquidität/Devisen, Edelmetalle, Unternehmen, Immobilien, Spezialanlagen) as its own licensable feature, independent of the general Custom Criteria license

### Fixed
- Automatic price refresh no longer depends on a security's Kategorie/Wertpapierart classification (which could be hidden depending on license tier); it now depends solely on whether the security has a Yahoo Finance symbol
- License key field on the settings page now shows the currently saved key instead of always appearing blank
- Switching or creating a database no longer discards the saved license key (and other settings)
- Total wealth on the by-criteria statistics page no longer understated when accounts were excluded from the criterion breakdown
- Browser no longer opens duplicate tabs on Spring Boot DevTools hot reload during development

### Changed
- Asset edit form: criteria list wraps after 5 columns instead of expanding indefinitely

## [1.0.11] – 2026-07-04

### Added
- Custom database path: Settings → Switch Database can now set an absolute H2 file path (`db.path`), taking priority over the name-based database; app restarts automatically after saving
- H2 TCP server with a configurable port, replacing `AUTO_SERVER=TRUE` (fixes a WSL2 mirrored-networking port conflict)
- Accounts list, depots list, and expanded bank view: "Last changed" column showing the date of the most recent balance/quantity entry, highlighted with a green badge when it's today
- "Update" mode (navbar): shows a checkbox per position on the accounts and depots lists to track which have been updated; positions already changed today start pre-checked; returns to view mode automatically once everything is checked, or when switched manually

### Fixed
- Trade Republic sync: adapted to Trade Republic's renamed WebSocket subscription topic (`compactPortfolio` → `compactPortfolioByType`) and its changed response shape
- DKB CSV import: adapted to DKB's new semicolon-delimited export format (previously comma-separated)
- Current-value calculations (depot value, depot positions, coin value, wealth statistics) now fall back to the latest price history entry when the current price is unavailable, instead of showing no value
- Physical bullion coins without a manually linked security (e.g. in a private safe) are now priced via the metal's spot-price asset and correctly shown in the wealth overview and dashboard depot list
- Depot list, bank list, and account list are now sorted case-insensitively (previously, lowercase-starting names like "etoro" sorted after all uppercase names)

### Changed
- Depot list sorted by bank, then name
- Depot list: removed the redundant "CSV-Import" menu entry (the import icon next to it already covers this)
- Updated README: documented database management, "Last changed", update mode, and the fixed DKB CSV delimiter/coin pricing behavior; removed the stale reference to the removed depot-list CSV-Import menu entry

## [1.0.10] – 2026-06-07

### Added
- Coins overview: spot price per oz shown in metal summary badges (in selected display currency)
- Depot import icon for DKB and FondsDepotBank in depot list

### Changed
- Replaced version.txt with CHANGELOG.md in Keep a Changelog format
- Added MIT license badge to README

## [1.0.9] – 2026-06-06

### Added
- CSV import for depot positions: DKB (comma-separated, UTF-8) and FondsDepotBank (semicolon-separated, ISO-8859-1)
- Format auto-detected from depot/bank name; blank selector shown when no format matches
- After CSV import and after BullionVault/Trade Republic sync: table of changed positions with old and new quantities
- Depot list: ⬇ import icon next to the actions menu for DKB and FondsDepotBank depots

### Fixed
- Depot positions view now shows only the latest quantity entry per asset (fixes duplicate rows after repeated imports or syncs)

### Changed
- Bank list: ↻ sync icon next to the actions menu for automated banks (BullionVault, Trade Republic)


## [1.0.8] – 2026-06-06

### Added
- BullionVault automatic sync: bank, currency accounts and vault positions (gold, silver, platinum) are created and updated automatically
- Trade Republic automatic sync via unofficial WebSocket API: two-step auth (phone + PIN → SMS OTP), cash balances and portfolio positions synced automatically
- Trade Republic: AWS WAF token support to bypass bot protection (token obtained once from Chrome DevTools, stored in config)
- Trade Republic: new assets enriched via Yahoo Finance ISIN lookup (name, symbol, currency, type, allocation, distribution policy)
- Bank list: ↻ sync icon next to the actions menu for automated banks for quick navigation to their sync page

### Fixed
- BullionVault: correct metal classification (API returns GOLD/SILVER/PLATINUM directly)
- BullionVault: correct weight unit (API returns kg, converted to troy ounces)
- Trade Republic: existing assets (active or archived) reused by ISIN instead of creating duplicates


## [1.0.7] – 2026-06-06

### Changed
- All actions dropdown buttons now show the hamburger icon (☰) instead of the Bootstrap caret; expand/collapse triangles in the bank list remain unchanged


## [1.0.6] – 2026-06-05

### Added
- Bank list: account names link to their balance list, depot names link to their positions page (in collapsible expand rows)
- Navigating back from account balances or depot positions restores the previously expanded bank row and scrolls it into view
- Depot positions, account balances, coin/depot/asset quantities: back button returns to the originating page (bank list, asset list, coins) via a `returnUrl` parameter
- Quantity history page per security per depot (`/depots/{id}/positions/{assetId}/quantities`)
- Runtime database switching: Settings → *Datenbank wechseln* lists existing H2 databases, switches by rewriting the config and restarting; shutdown page auto-redirects when the app is back

### Changed
- All form+table pages (balances, quantities, positions) are now responsive: heading, form and back button sit left of the table on wide screens, stacked on narrow screens
- Asset list: responsive layout — Symbol shown under ISIN; Art, Kategorie, Ausschüttung and Allocation combined into a *Eigenschaften* badge column hidden below xl breakpoint; Thesaurierend badge uses blue instead of grey
- Depot positions select: long security names abbreviated to prevent column width expansion
- Dashboard: Depot/Konto column moved behind Index; account rows link to their balance page
- Dropdown clipping inside `table-responsive` containers fixed globally
- Asset list sorted case-insensitively
- Unassigned securities (no depot positions, no coin link) show Delete instead of Archive on the asset list

### Fixed
- Dashboard: wrong depot links for accounts whose ID coincidentally matched an asset ID
- 404 on saving a new security (empty `returnUrl` in hidden form field)


## [1.0.5] – 2026-06-04

### Added
- Distribution policy (Distributing / Accumulating) added as a new security attribute
- Asset search: ISIN shown in search results dropdown
- Asset search: distribution policy, asset allocation and security type pre-filled automatically from Yahoo Finance result (bond keywords → Risk-Free; Acc/Accumul → Accumulating; Dist/Income → Distributing)
- Asset allocation pre-fill respects the configured base currency: bond ETFs in a foreign currency remain Risky
- Bank entity to group accounts and depots; bank list with combined total and expandable rows
- Bank detail page (`/banks/{id}`) with collapsible account and depot sections, subtotals and grand total
- Accounts and depots can be assigned/removed from the bank detail page
- Deleting a bank is blocked while accounts or depots are assigned to it

### Changed
- Accounts and depots can optionally be linked to a bank
- ISIN shown next to security name in depot positions form and table
- Account list falls back to legacy bank name string for accounts not yet linked to a Bank entity


## [1.0.4] – 2026-06-04

### Fixed
- Wealth history: coin values were always shown as 0 even when coins are present; fixed by replacing entity proxy access with pure ID maps and correcting the quantity fallback when no historical record exists before a given month
- Wealth history: coins without CoinQuantity history (created before v1.0.2) now correctly use their current quantity for all months
- Outdated notice "coin quantities are not tracked historically" removed from the history page


## [1.0.3] – 2026-06-04

### Added
- Wealth history: precious metal coins without a linked security now use historical price data instead of the current spot price
- Price history upsert: at most one entry per security per day (multiple updates on the same day update the existing entry)
- Asset quantity and coin quantity: same upsert logic per date
- Daily price refresh (18:00 scheduler, manual button) now also writes a price history entry

### Changed
- Non-EUR accounts are now properly converted to EUR before being included in total wealth and statistics calculations
- Each statistics page now computes positions once per request instead of twice (halves Yahoo Finance HTTP calls)
- N+1 queries in AccountService, DepotService and AssetService replaced with bulk queries
- `fetchPrice()` now throws when Yahoo Finance returns no valid price (prevents storing 0 as the current price)
- `updatePrices()` no longer holds a database connection open during external HTTP calls
- Failed spot price fetches are logged instead of silently ignored
- Maven Compiler Plugin explicitly configured for Java 21 (fixes IntelliJ "JVM target 5" error)

### Fixed
- 674 duplicate price history rows removed from the database

### Security
- H2 database console disabled (was accessible without authentication)


## [1.0.2] – 2026-06-03

### Added
- Historical wealth view (`/statistics/history`): monthly chart and table showing total wealth over time, broken down into securities, accounts and coins
- Coin quantity history (`CoinQuantity` entity): date-based snapshots per coin; auto-recorded on save; manageable via `/coins/{id}/quantities`
- Monthly price snapshots backfilled from Yahoo Finance historical API on startup for any months missed while the app was not running
- Prices refreshed from Yahoo Finance on every startup (not only at 18:00)

### Changed
- Asset search: ISIN is now filled from the Yahoo Finance search result
- Coin form: asset (price source) is now required; selecting a metal auto-suggests a matching asset from existing coins
- Monthly price history entries marked with a badge in the price history view
- Price history entries are only written by monthly snapshot jobs, not by daily price refreshes

### Fixed
- Dropdown in coin list no longer clipped on odd rows

### Removed
- Standing Orders feature


## [1.0.1] – 2026-06-02

### Added
- Settings menu in navbar: language, color scheme, display currency
- Language switch: German / English
- Color scheme: Light, Dark, System (follows OS preference)
- Configurable display currency: any 3-letter ISO code (EUR, USD, GBP, CHF, …); all monetary values converted from EUR; account balances show both display currency and native account currency
- Locale-aware number formatting (e.g. German decimal comma)
- Accessibility improvements across all pages
- Platform-specific data directory: Windows `%APPDATA%\Wealth\`, macOS `~/Library/Application Support/Wealth/`, Linux `~/.config/wealth/`
- System tray icon with show/hide and quit actions
- Info page showing version, config path and database path
- MIT license
- Unified widescreen table layout across all pages

### Fixed
- Dashboard layout (labels left, values right)


## [1.0.0] – 2026-06-02

### Added
- Securities: exchange-traded (stocks, ETFs, bonds, funds, crypto, currencies, precious metals) and manual (e.g. pension plans without automatic prices)
- Automatic price updates via Yahoo Finance at startup and weekdays at 18:30
- EUR conversion for non-EUR securities using live exchange rates
- Asset archiving and restore (history is retained)
- Asset search via Yahoo Finance (name, ISIN, WKN)
- Bank accounts with individual currencies and date-based balance snapshots
- Depots (portfolios) grouping securities with date-based quantity snapshots
- Coin management for gold, silver and platinum: weight in grams, automatic troy ounce conversion, optional security link as EUR/oz price source, live Yahoo Finance spot prices as fallback
- Statistics: dashboard, by allocation (Risky / Risk-Free), by index, by security type, collapsible detail rows
- Monthly price snapshots for all securities (1st of each month)
- Full balance and quantity history per account and depot
