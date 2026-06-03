# Code Review – wealth 1.0.2
_Datum: 2026-06-03_

---

## 🔴 Kritisch (Bugs mit konkretem Schadenspotenzial)

### 1. `CoinService.delete()` löscht keine `CoinQuantity`-Einträge
**Datei:** `service/CoinService.java:138`

```java
public void delete(Long id) { coinRepository.deleteById(id); }
```

`CoinQuantity` hat eine `NOT NULL` Foreign-Key-Spalte `coin_id`. Beim Löschen einer Münze bleiben alle zugehörigen `CoinQuantity`-Einträge in der DB — in H2 mit aktivierter FK-Prüfung führt das zu einem Constraint-Fehler, andernfalls zu Datenmüll.

**Fix:** `coinQuantityRepository` in `delete()` einbinden, alle Einträge vorher löschen (analog zu `AssetService.hardDelete()` mit `priceHistoryRepository.deleteByAsset()`).

---

### 2. Controller liefern bei unbekannter ID eine leere Seite (HTTP 200 statt 404)
**Dateien:** `controller/AccountController.java:72,93`, `controller/AssetController.java:61,103,126`, `controller/DepotController.java:61,81`, `controller/CoinController.java:102,142`

Alle Edit-/Detail-Endpunkte verwenden `ifPresent()`:
```java
accountService.findById(id).ifPresent(a -> model.addAttribute("account", a));
```

Existiert die ID nicht, ist das Model-Attribut einfach leer. Das Template rendert dann mit `null`-Werten, was zu NPEs im Template oder — schlimmer — zum stillen Speichern eines leeren/falschen Datensatzes führt, wenn der Nutzer das Formular absetzt.

**Fix:** Statt `ifPresent()` → `orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND))`.

---

## 🟠 Hoch (Logikfehler / Datenverlust-Risiko)

### 3. `saveMonthlyHistory()` speichert veralteten Kurs
**Datei:** `service/PriceService.java:133`

`saveMonthlyHistory()` läuft am 1. des Monats um **8:00 Uhr** und speichert `Asset.currentPrice`. Dieser Wert stammt aber von der letzten `updatePrices()`-Ausführung — die täglich um **18:00 Uhr** läuft. Am Morgen des 1. ist `currentPrice` also der Schlusskurs vom Vortag, nicht vom Monatsersten selbst.

**Fix:** `saveMonthlyHistory()` so anpassen, dass es den historischen Kurs für den 1. des Monats per `fetchHistoricalPrice()` holt, statt `currentPrice` zu nehmen — genauso wie es `backfillMissingMonthlyHistory()` macht.

---

### 4. Keine Reihenfolge-Garantie zwischen den zwei `@EventListener`-Methoden
**Datei:** `service/PriceService.java:50,107`

Beide `updatePrices()` und `backfillMissingMonthlyHistory()` sind mit `@EventListener(ApplicationReadyEvent.class)` annotiert. Spring garantiert keine Ausführungsreihenfolge. Läuft `backfillMissingMonthlyHistory()` zuerst, fehlt der aktuelle Monatskurs noch in der PriceHistory und wird ggf. nicht korrekt bewertet.

**Fix:** `@Order`-Annotation verwenden oder `backfillMissingMonthlyHistory()` am Ende von `updatePrices()` aufrufen.

---

### 5. `PriceHistory` hat keinen Unique-Constraint auf `(asset_id, date)`
**Datei:** `domain/PriceHistory.java`

`backfillMissingMonthlyHistory()` verhindert Duplikate durch die `YearMonth`-Set-Prüfung — aber nur innerhalb eines Laufs. Läuft die Methode gleichzeitig zweimal (z. B. durch einen devtools-Neustart während des Starts), entstehen doppelte Einträge für denselben Monat. Das verzerrt den Vermögensverlauf, weil `floorEntry()` dann zufällig einen der beiden Einträge liefert.

**Fix:** `@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"asset_id", "date"}))` auf `PriceHistory` setzen.

---

## 🟡 Mittel (Qualität / Stabilität)

### 6. N+1-Queries in `StatisticsService.getAllPositions()`
**Datei:** `service/StatisticsService.java:56`

Die verschachtelte Schleife `for (Asset) → for (Depot) → quantityRepository.findFirst...()` erzeugt `Anzahl Assets × Anzahl Depots` einzelne DB-Queries bei jedem Dashboard-Aufruf. Mit 50 Wertpapieren und 3 Depots sind das 150 Queries pro Request.

`getWealthHistory()` lädt bereits alle Daten einmalig mit `findAllWithAssetAndDepot()`. Dasselbe Muster sollte auch `getAllPositions()` verwenden.

---

### 7. Fehlende `nullable = false` Constraints auf Kern-Feldern
**Dateien:** `domain/PriceHistory.java:19-20`, `domain/AssetQuantity.java:23`, `domain/CoinQuantity.java:18`, `domain/AccountBalance.java:19`

Die Felder `date`, `price`, `quantity` und `balance` fehlen `@Column(nullable = false)`. H2 erlaubt damit das Persistieren von `null`-Werten in Pflichtfeldern, was zu stillen Rechenfehlern oder NPEs bei der Verarbeitung führt.

---

### 8. `CoinQuantity`-Einträge werden beim Asset-Löschen nicht berücksichtigt
**Datei:** `service/AssetService.java` / `domain/Coin.java`

Wird ein `Asset` gelöscht, das mit Coins verknüpft ist (`Coin.asset`), werden die Coins auf `asset = null` gesetzt (oder ein FK-Fehler ausgelöst). Die `CoinQuantity`-Einträge dieser Coins bleiben aber unkritisch erhalten, da sie an `Coin` hängen. Kritisch wird es, wenn Coins bei Asset-Löschung auch gelöscht werden sollen — dann fehlt die Cascade-Kette bis zu `CoinQuantity`.

---

### 9. Kein explizites Fehler-Handling bei ungültigem `depotId`/`assetId` in `CoinController.save()`
**Datei:** `controller/CoinController.java:122`

```java
public String save(@RequestParam Long depotId, @RequestParam(required = false) Long assetId, ...)
```

`CoinService.save()` ruft `depotRepository.findById(depotId).orElseThrow()` auf — bei ungültiger ID fliegt ein `NoSuchElementException` ohne HTTP-Status-Mapping. Der Nutzer sieht einen unformatierten 500-Fehler.

---

## 🔵 Niedrig (Code-Qualität / Minor)

### 10. `fetchPrice()` und `fetchHistoricalPrice()` nicht gegen leere Yahoo-Finance-Antworten abgesichert
**Datei:** `service/PriceService.java:79,94`

```java
JsonNode meta = root.path("chart").path("result").get(0).path("meta");
```

`.get(0)` wirft `IndexOutOfBoundsException` wenn `result` ein leeres Array ist. Die Aufrufer haben zwar `try-catch (Exception e)`, aber die Ausnahme wird nur geloggt — ein strukturierteres `isArray() && size() > 0`-Check würde die Ursache klarer machen.

---

### 11. `messages.properties` (Fallback) nicht synchron mit `messages_de.properties`
**Datei:** `src/main/resources/messages.properties`

Die Fallback-Datei enthält noch den Kommentar `# Standing orders` (Zeile 117) als Überbleibsel nach der Bereinigung. Außerdem fehlen neuere Schlüssel wie `page.statistics.history.title`, `page.coins.quantities.*`, `col.month` usw.

---

## Zusammenfassung

| # | Schwere | Bereich | Problem |
|---|---------|---------|---------|
| 1 | 🔴 Kritisch | CoinService | delete() lässt CoinQuantity-FK-Einträge zurück |
| 2 | 🔴 Kritisch | Controller (8×) | ifPresent statt 404 bei unbekannter ID |
| 3 | 🟠 Hoch | PriceService | saveMonthlyHistory() speichert Vortages-Kurs |
| 4 | 🟠 Hoch | PriceService | Keine Ausführungsreihenfolge der @EventListener |
| 5 | 🟠 Hoch | PriceHistory | Kein Unique-Constraint auf (asset_id, date) |
| 6 | 🟡 Mittel | StatisticsService | N+1-Queries in getAllPositions() |
| 7 | 🟡 Mittel | Domain-Entities | Fehlende nullable=false Constraints |
| 8 | 🟡 Mittel | CoinService/Asset | Cascade-Kette bei Asset-Löschung unvollständig |
| 9 | 🟡 Mittel | CoinController | NoSuchElementException ohne HTTP-Status |
| 10 | 🔵 Niedrig | PriceService | .get(0) ohne Array-Leer-Prüfung |
| 11 | 🔵 Niedrig | messages.properties | Fallback-Datei nicht synchron |
