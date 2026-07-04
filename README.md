# Wealth

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A personal wealth tracking application to manage and monitor your entire financial portfolio in one place.

## Features

### Securities
- Track exchange-traded securities (stocks, ETFs, bonds, funds, crypto, currencies)
- Record precious metal securities (e.g. gold ETCs with ISIN)
- Track other assets without automatic price updates (e.g. pension plans, employer contributions)
- Automatic price updates via Yahoo Finance at startup and daily on weekdays at 18:00
- Record date-based quantity snapshots per security and depot (e.g. when you buy or sell)
- Assign index name (e.g. MSCI World, S&P 500)
- Assign security type: Stock, Equity Fund, ETF, Bond, Currency, Precious Metal, Crypto, Other
- Assign asset allocation: Risky or Risk-Free
- Assign distribution policy: Distributing or Accumulating
- Archive securities to hide them without losing history

**Security search (create form):**
- Search by name, ISIN or WKN — results are fetched live from Yahoo Finance
- ISIN is shown in the search results and auto-filled into the form
- Security type, asset allocation and distribution policy are pre-filled automatically:
  - Bond-related keywords (Bond, Anleihe, Aggregate, Overnight, Money Market, …) with matching base currency → Risk-Free
  - "Acc" / "Accumul" → Accumulating; "Dist" / "Income" → Distributing

### Banks
- Manage banks as a central entity that groups accounts and depots
- View combined total (accounts + depots) per bank
- Expand each bank row to see its accounts and depots with individual values
- Assign or remove accounts and depots from the bank detail page
- Deleting a bank is blocked while accounts or depots are still assigned to it
- Automated banks (BullionVault, Trade Republic) show a ↻ sync icon in the bank list for quick navigation to their sync page

### Accounts
- Track bank accounts with individual currencies
- Optionally assign an account to a bank
- Record date-based balance snapshots
- Balances are displayed in your configured display currency with the native account currency shown below for reference
- Sorted alphabetically (case-insensitive) by bank, then account number
- "Letzte Änderung" column shows the date of the most recent balance entry, highlighted in green when it's today

### Depots (Portfolios)
- Group securities into depots
- Optionally assign a depot to a bank
- View current holding value per depot
- Record date-based quantity entries per security and depot
- ISIN is shown next to the security name in the position form and table
- DKB and FondsDepotBank depots show a ⬇ import icon in the depot list for quick navigation to CSV import
- Sorted alphabetically (case-insensitive) by bank, then depot name
- "Letzte Änderung" column shows the date of the most recent quantity entry, highlighted in green when it's today

### Update Mode
An "Aktualisieren" / "Ansicht" mode switch in the navbar helps track your daily update routine:
- In "Aktualisieren" mode, a checkbox appears next to every account and depot
- Positions already updated today are pre-checked automatically
- Checked state is kept while you navigate (e.g. to edit a balance and back)
- Once everything is checked — or the mode is switched back manually — it returns to "Ansicht" mode and the checkmarks reset

### Physical Coins & Precious Metals
- Track physical gold, silver and platinum coins separately from securities
- Enter weight in grams per coin — the app converts to troy ounces automatically
- Record date-based quantity history per coin (e.g. when you buy or sell physical coins)
- Optionally link a coin to a security to use that security's price as the EUR/oz rate
- Without a linked security, the coin is priced via a shared spot-price security for its metal (the same one automatic BullionVault sync creates) if one exists in your data
- Group coins by name and metal for a clear overview
- Assign coins to depots

### Statistics
Four current-value views, each showing total wealth and individual position percentages:

| View | Groups positions by |
|------|---------------------|
| Overview | Asset allocation (Risky / Risk-Free) |
| By Index | Index name (MSCI World, S&P 500, …) |
| By Security Type | ETF, Stock, Bond, Account, … |
| By Allocation | Same as overview, with collapsible detail rows |

### Wealth History
Monthly chart and table showing how total wealth developed over time, broken down into securities, accounts and coins. Values are calculated using recorded historical prices and quantities for each month-end date.

### Price History
The price of each security on the 1st of every month is saved automatically. The price history is also viewable per security.

If a live price cannot be fetched (e.g. Yahoo Finance is temporarily unavailable), current-value calculations fall back to the most recently saved historical price instead of treating the position as valueless.

### Currency Conversion
All values are stored and calculated in EUR internally. Exchange rates for non-EUR securities are fetched automatically from Yahoo Finance. The display currency can be set freely — see [Settings](#settings) below.

---

## Automatic Sync

### BullionVault
Navigate to **Settings → BullionVault** to set up the automatic sync. Enter your BullionVault username and password (the password is never stored). On each sync the app:
- Creates a bank, currency accounts and vault depots (one per location) on first run
- Updates cash balances and precious metal holdings in troy ounces
- Shows a table of changed positions with old and new quantities

Metal classification (Gold, Silver, Platinum) and vault location (e.g. Zurich, London) are read directly from the BullionVault API. Weights are returned in kilograms and converted to troy ounces automatically.

### Trade Republic
Navigate to **Settings → Trade Republic** to set up the automatic sync. The integration uses Trade Republic's unofficial API and requires a two-step authentication (phone number + PIN → SMS OTP).

**AWS WAF token (one-time setup):**  
Trade Republic blocks automated logins without a valid browser cookie. You need to obtain the `aws-waf-token` cookie once from Chrome DevTools:
1. Open Chrome → go to `https://app.traderepublic.com/login`
2. Open DevTools (F12) → Application → Cookies → `https://app.traderepublic.com`
3. Copy the value of `aws-waf-token` and paste it into the WAF token field in the app

The token is valid for days to weeks and only needs to be refreshed when logins start failing.

**Sync process:**
1. Enter your phone number and PIN → click *OTP anfordern*
2. Enter the 6-digit code from the SMS → click *Abrufen*

On each sync the app:
- Creates a bank, cash account and securities depot on first run
- Updates the EUR cash balance
- Updates depot positions by ISIN; new securities are enriched via Yahoo Finance (name, symbol, type, allocation, distribution policy)
- If a security with the same ISIN already exists (active or archived), it is reused rather than duplicated
- Shows a table of changed positions with old and new quantities

The PIN is never stored. The session token is not stored.

---

## CSV Import

### Supported formats
| Bank | Format | Encoding | Delimiter |
|------|--------|----------|-----------|
| DKB | Depot export (Wertpapierbestand) | UTF-8 | Semicolon |
| FondsDepotBank | Depot export | ISO-8859-1 | Semicolon |

### How to import
1. Export your depot positions as CSV from the bank's web interface
2. In the app, navigate to **Depots** and click the ⬇ import icon next to the actions menu for the target depot (shown for DKB and FondsDepotBank depots)
3. Select the format and upload the file

The format is pre-selected automatically based on the depot or bank name. After the import a table shows which positions changed and by how much. Existing securities are matched by ISIN; new securities are enriched via Yahoo Finance. Only quantities that actually changed are written to the database.

---

## Settings
All preferences are stored as cookies and persist across sessions.

| Setting | Options |
|---------|---------|
| Language | German, English |
| Color Scheme | Light, Dark, System |
| Display Currency | Any 3-letter ISO currency code (e.g. EUR, USD, GBP, CHF) |

When a non-EUR display currency is selected, the exchange rate is fetched on demand and all monetary values are converted automatically. Account balances always show both the display currency amount and the native account currency below it.

---

## Requirements

- Java 21 or later
- Maven 3.x (only needed to build from source)

---

## Build & Run

**Run directly with Maven:**
```bash
mvn spring-boot:run
```

**Build a standalone JAR:**
```bash
mvn package
java -jar target/wealth-*.jar
```

The application starts on **http://localhost:8080**.

**First launch:** A dialog (or console prompt in headless mode) asks for a name to identify the database file. This name is saved and reused on subsequent starts.

---

## Data Storage

The database and configuration file are stored in a platform-specific directory:

| Platform | Location |
|----------|----------|
| Windows | `%APPDATA%\Wealth\` |
| macOS | `~/Library/Application Support/Wealth/` |
| Linux | `$XDG_CONFIG_HOME/wealth/` or `~/.config/wealth/` |

### Database Management
**Settings → Datenbank wechseln** lets you:
- See all databases found in the data directory and switch between them
- Create a new named database
- Set a custom absolute path to an H2 database file outside the standard directory, taking priority over the name-based database; leave it empty to revert

Switching, creating, or setting a custom path shuts down the application — restart it to continue with the new database. The shutdown page automatically returns to the app once it's back up.

---

## Precious Metals — Yahoo Finance Symbols

For exchange-traded precious metal securities, use the following Yahoo Finance symbols:

| Metal    | Symbol | Unit         | Currency |
|----------|--------|--------------|----------|
| Gold     | GC=F   | Troy oz (31.1035 g) | USD |
| Silver   | SI=F   | Troy oz      | USD      |
| Platinum | PL=F   | Troy oz      | USD      |

**How to set up a precious metal security:**
1. Create a new security manually (without search)
2. Enter name (e.g. *Gold*), symbol `GC=F`, currency `USD`
3. Set category to *Exchange-Traded* — this enables automatic price updates
4. Record holdings in troy ounces (`1 g = 0.03215 oz`)

The app converts the USD spot price to EUR automatically using the current exchange rate.

For **physical coins**, use the dedicated Coins section instead — weight is entered in grams per coin and the app handles the troy ounce conversion internally.

---

## License

MIT — free to use, distribute and modify as long as this notice is retained.

**Author:** Wolfgang Schneider
