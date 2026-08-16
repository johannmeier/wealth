# Claude Code Prompt: FinTS-Integration für wealth

## Ziel
Implementiere read-only FinTS/HBCI-Abfragen (Kontosaldo + Depotbestand) für die
Banken **Norisbank**, **DKB** und **Consorsbank** in der bestehenden Spring-Boot-
Anwendung `de.wsc.wealth:wealth`.

## Kontext zur bestehenden Codebase
- Spring-Boot-App mit H2-Datenbank (custom DB-Caching) und WebClient.
- Es existiert bereits eine Broker-Integration für Trade Republic
  (`TradeRepublicService`, `de.wsc.wealth.service`), undokumentierte API,
  funktioniert stabil. **Wichtig: Das ist eine einzelne konkrete `@Service`-Klasse
  ohne gemeinsames Interface** — es gibt in der Codebase kein `AccountProvider`
  o. ä. Bitte **nicht** nach einem Interface suchen oder eins voraussetzen.
  Stattdessen als Vorbild für die **Datenfluss-/Mapping-Konventionen** heranziehen
  (siehe unten). Wenn im Rahmen dieser Implementierung eine gemeinsame Abstraktion
  für Bank-Integrationen sinnvoll erscheint, ist das Neuland und muss als
  eigenständige Design-Entscheidung getroffen (und kurz begründet) werden — nicht
  als "bestehendes Pattern" behandelt werden.

### Konkret aus TradeRepublicService übertragbare Konventionen
- **Zwei-Phasen-Flow für TAN/OTP**: TR trennt `requestOtp(...)` (liefert eine
  `processId`) von `sync(phoneNumber, processId, otp)` (schließt mit dem vom
  Nutzer eingegebenen Code ab). Das ist das direkte Vorbild für den FinTS-TAN-Flow:
  Dialog starten → TAN-Challenge an Nutzer zeigen → zweiter Request mit TAN
  schließt den Dialog ab. Diesen Zwei-Schritt-Aufbau übernehmen, nicht neu
  erfinden.
- **Domain-Mapping**: Bank → Account (Upsert per Datum) → AccountBalance für
  Kontosalden; Depot → Asset (Auflösung per ISIN via `AssetSearchService`,
  Fallback: Asset anlegen) → AssetQuantity (nur schreiben, wenn sich die Menge
  geändert hat) für Depotbestände. HKSAL mappt auf den ersten Pfad, HKWPD auf den
  zweiten — analog zu `TradeRepublicService.persistData`.
- **Kein Scheduler**: TR läuft nicht automatisch (kein `@Scheduled`), rein manuell
  über die UI getriggert. Für FinTS aus denselben Gründen (TAN-Pflicht) ebenso
  vorgehen, siehe Abschnitt "PIN/TAN".
- **Keine Credential-Persistenz**: TR speichert die PIN **nicht** dauerhaft, nur
  transient während des Requests. **Für FinTS gilt dieselbe Regel: Kontonummer/
  Nutzerkennung und PIN werden nicht in der Datenbank gespeichert.** Nur
  unkritische Konfigurationswerte (FinTS-Server-URL, BLZ/BIC, TAN-Verfahren)
  dürfen persistiert werden; Nutzerkennung und PIN werden bei jedem Abruf neu vom
  Nutzer eingegeben.

## Bibliothek
- **HBCI4Java**, aktuell gepflegte Fork unter
  https://github.com/hbci4j/hbci4java (der alte `willuhn/hbci4java`-Fork verweist
  dorthin weiter). Maven-Koordinate (verifiziert, Stand August 2026):
  ```xml
  <dependency>
    <groupId>com.github.hbci4j</groupId>
    <artifactId>hbci4j-core</artifactId>
    <version>3.1.49</version> <!-- aktuelle Version vor Einbindung nochmal prüfen -->
  </dependency>
  ```
  **Nicht** `org.kapott:hbci4j` verwenden — dieser Name ist nicht das aktuelle
  Maven-Central-Artefakt (der Domainname `kapott.org` gehört nicht der
  Organisation, daher `com.github.hbci4j` als groupId).
- Lizenz: **LGPL-2.1** (seit Mai 2016; davor GPLv2 — falls eine ältere Fork-Version
  eingebunden wird, das prüfen). LGPL erlaubt das Einbinden als Dependency, ohne
  dass die eigene Anwendung unter LGPL/GPL gestellt werden muss, solange die
  Bibliothek nicht selbst modifiziert wird. Damit vermutlich unkritisch für die
  PolyForm-Noncommercial-Lizenz des wealth-Projekts (`LICENSE`) — sollte aber vor
  dem Merge kurz gegengeprüft werden (keine Modifikation der Library, korrekte
  Attribution/Lizenzhinweis in `NOTICE`/README falls vom Projekt so gehandhabt).
- Da die Doku dünn ist: bei Unsicherheiten Beispielcode aus der
  hbci4j/Hibiscus-Codebase als Referenz heranziehen, nicht raten.

## Scope (read-only!)
- Nur folgende FinTS-Geschäftsvorfälle:
    - `HKSAL` – Saldenabfrage
    - `HKWPD` – Depotbestandsabfrage
- **Keine** Überweisungen, Daueraufträge, Lastschriften o. ä. — bewusst nur Lesezugriff.

## Bankspezifische Konfiguration (pro Bank in eigener Config/Properties)
Für jede der drei Banken wird benötigt:
- FinTS-Server-URL (Achtung: kann sich ändern, siehe DKB-URL-Wechsel 11/2024 — URL
  konfigurierbar halten, nicht hardcoden)
- BLZ/BIC
- Unterstütztes TAN-Verfahren (variiert je Bank: photoTAN, chipTAN, pushTAN etc.)

Diese Werte dürfen persistiert werden. **Nutzerkennung (bankspezifisches Format
beachten, z. B. bei Consorsbank: Kontonummer + dreistellige Berechtigtennummer
kombiniert) und PIN werden NICHT persistiert** — siehe "Keine Credential-
Persistenz" oben.

### Recherchierte Default-Werte (Stand August 2026)
Diese Werte sollen in der Config-UI als **Vorschlag/Vorbelegung** erscheinen
(Eingabefeld vorausgefüllt, aber änderbar) — nicht als Java-Konstante hart
verdrahtet, da sich URLs/Verfahren ändern können (siehe DKB-Präzedenzfall).
Vor dem ersten Live-Test jeweils gegen die aktuelle Doku der Bank bzw. den
FinTS-Zugang-Antrag gegenprüfen.

| Bank | FinTS-URL | HBCI-Version | Nutzerkennung-Format | TAN-Verfahren | Status |
|---|---|---|---|---|---|
| Norisbank | `https://fints.norisbank.de/` | nicht bestätigt | Filial- + Kontonummer (z. B. `4311234567`) oder selbstgewählte norisbank-ID; User-ID-Feld leer lassen | identisch zum normalen Online-Banking-PIN/TAN — vermutlich photoTAN/BestSign (siehe Recherche), aber abhängig davon, was beim Nutzer aktiv konfiguriert ist | **vom Nutzer bestätigt** (offizielle norisbank-Angaben), BLZ muss gegen Software-Vorschlag geprüft werden |
| DKB | `https://fints.dkb.de/fints` (seit 11/2024) | **FinTS 3.0** | Anmeldename (Kunden-ID leer lassen) | **DKB-App (decoupled)** | **vom Nutzer bestätigt** (offizielle DKB-Angaben) — siehe Blocker unten |
| Consorsbank | `https://brokerage-hbci.consorsbank.de/hbci` | FinTS ≥ 4.0 | Kontonummer + 3-stellige Berechtigtennummer, z. B. `900123456001` | **weiterhin unklar** — Bank-Doku sagt nur "bei jedem Login TAN-Pflicht", ohne Verfahren zu benennen | URL/Format vom Nutzer bestätigt, TAN-Verfahren offen |

BLZ/BIC sind allgemein bekannte, aber weiterhin nicht verifizierte Werte — vor
Verwendung aus einer verlässlichen Quelle (z. B. Kontoauszug, Bank-Website)
bestätigen.

### ⚠️ Bekannter Blocker: DKB "decoupled"-TAN (Stand: Implementierungsstand aus Schritt 3–5)
Der aktuelle Implementierungsstand (`hbci4j-core:4.0.0` von Maven Central) unterstützt
`NEED_PT_DECOUPLED` **nicht** — dieser Callback-Typ existiert nur im GitHub-Master
von hbci4j/hbci4java, nicht im veröffentlichten Release-Jar (per Bytecode-Grep
verifiziert). Da DKB laut offizieller Doku genau dieses Verfahren ("DKB-App
(decoupled)") als Sicherheitsmedium vorschreibt, wird der DKB-Abruf mit dem
aktuellen Stand vermutlich fehlschlagen bzw. auf einen generischen Fehlerpfad
laufen. Vor dem DKB-Live-Test klären: gibt es eine neuere hbci4j-core-Version mit
`NEED_PT_DECOUPLED`-Support, oder muss das selbst nachgezogen werden (z. B. gegen
den GitHub-Master bauen)? Das betrifft nur DKB — Norisbank (photoTAN) und
Consorsbank sind von dieser Lücke nicht betroffen.

## Operative Voraussetzung (Stand August 2026)
**Bei allen drei Banken ist kein separater FinTS-Antrag nötig** — Zugang
funktioniert jeweils direkt mit den bestehenden Online-Banking-Zugangsdaten:
- **Norisbank**: Filial-/Kontonummer (oder norisbank-ID) + normale
  Online-Banking-PIN/TAN, keine separate Freischaltung. BLZ muss gegen den
  Software-Vorschlag geprüft werden (siehe offizielle Angaben).
- **DKB**: normaler Anmeldename + PIN, Sicherheitsmedium = DKB-App (decoupled).
  Zugang ist einrichtbar, aber siehe **DKB-Blocker** oben — der Abruf über die
  aktuelle Implementierung ist vermutlich (noch) nicht funktionsfähig, weil
  `NEED_PT_DECOUPLED` in der eingebundenen Library fehlt.
- **Consorsbank**: Erstmalige Einrichtung über Girokonto oder Verrechnungskonto
  (Kontoart "Girokonto"/"Online Konto"), alle weiteren Konten/Depots werden
  danach automatisch mitübertragen. Nutzerkennung = Kontonummer +
  Berechtigtennummer (siehe Tabelle). TAN-Verfahren bleibt offen, zeigt sich
  vermutlich erst beim ersten Verbindungsaufbau.

Live-Test-Reihenfolge (siehe "Vorgehen" Schritt 6): **Consorsbank oder
Norisbank zuerst**, da dort kein bekannter Blocker vorliegt. DKB erst, sobald
der `NEED_PT_DECOUPLED`-Blocker gelöst ist.

## Bekannte Einschränkung: PIN/TAN bei jedem Login
Viele Banken verlangen auch bei reiner Saldo-/Depotabfrage bei jedem Login eine TAN.
Ein vollautomatischer Cron-Abruf ohne Nutzerinteraktion ist daher möglicherweise
nicht bei allen drei Banken möglich. Bitte:
1. Für jede Bank prüfen/dokumentieren, ob ein TAN-freies Leseprofil existiert
   (manche Banken erlauben das über eine eingeschränkte Berechtigtennummer).
2. Architektur so auslegen, dass sowohl ein manuell getriggerter Abruf (mit
   TAN-Eingabe durch den Nutzer) als auch — wo möglich — ein automatischer Abruf
   unterstützt wird. Kein Hard-Assumption auf vollautomatischen Betrieb treffen.
3. **TAN-Verfahren sind nicht nur Texteingabe.** chipTAN liefert typischerweise
   einen Flicker-Code (Bilddaten), photoTAN ein Bild, pushTAN benötigt eine
   Bestätigung in einer separaten App (kein Code, sondern Warten/Pollen auf
   Bestätigung). Die UI muss mindestens Text-TAN-Eingabe und Bild-Anzeige
   unterstützen; pushTAN ggf. als Warteschleife mit Status-Polling. Scope dafür
   explizit einplanen, nicht als reines Textfeld unterschätzen.
4. **Sessions/State über zwei HTTP-Requests hinweg.** HBCI4Java arbeitet über
   einen `HBCICallback`, der synchron blockiert, bis eine TAN geliefert wird —
   das lässt sich nicht direkt in ein zustandsloses Request/Response-Modell
   pressen. Analog zu TR's `processId`-Ansatz muss eine laufende Dialog-/
   Passport-Instanz serverseitig (z. B. in einer Service-internen Map, keyed auf
   eine Prozess-ID) zwischen "Abruf starten" und "TAN einreichen" gehalten
   werden. Das explizit als Design-Entscheidung treffen und dokumentieren, nicht
   stillschweigend voraussetzen.
5. **HBCI4Java braucht einen zustandsbehafteten Passport** (lokale Keystore-Datei
   mit BPD/UPD/TAN-Medien-Infos pro Bankverbindung) — das ist strukturell anders
   als der zustandslose WebClient-Ansatz bei TR. Ablageort klären (z. B. analog
   zur H2-DB in `~/.config/wealth/` bzw. den plattformspezifischen Pendants) und
   Verhalten bei App-Neustart/Passport-Verlust dokumentieren.

## Testbarkeit
FinTS-Abrufe lassen sich nicht sinnvoll durch `mvn test` automatisieren (echte
Zugangsdaten + TAN-Interaktion nötig, siehe kein öffentlicher FinTS-Sandbox-
Zugang). Manuelles Live-Testen mit echten Zugangsdaten erfolgt durch den Nutzer,
nicht durch den implementierenden Agenten selbst (keine eigenständigen
`spring-boot:run`/`curl`-Testläufe gegen echte Bankzugänge). Unit-Tests sind für
die Mapping-Logik (HKSAL/HKWPD-Antwort → Domain-Entities) sinnvoll und erwartet,
aber die End-to-End-Verbindung zur Bank kann nur manuell verifiziert werden.

## Vorgehen
1. Bestehenden `TradeRepublicService` analysieren — als Vorbild für Datenfluss/
   Mapping-Konventionen und den TAN-Zwei-Phasen-Flow (siehe oben), **nicht** für
   ein (nicht existierendes) gemeinsames Interface.
2. Operative Voraussetzung (FinTS-Zugang je Bank) mit dem Nutzer klären/
   dokumentieren, bevor gegen eine echte Bank getestet wird.
3. Maven-Dependency für HBCI4Java hinzufügen (Koordinate vorher verifizieren,
   Lizenz prüfen).
4. Design-Entscheidung für Session-/State-Handling über den TAN-Flow treffen und
   kurz dokumentieren (siehe Punkt 4 oben).
5. HKSAL- und HKWPD-Abfrage implementieren, Ergebnis in bestehende Domain-Modelle
   mappen (Account/AccountBalance bzw. Depot/Asset/AssetQuantity, siehe
   Mapping-Konventionen oben). Nutzerkennung/PIN nur transient verwenden, nicht
   persistieren.
6. Erste Implementierung/Test gegen eine der drei Banken (Consorsbank oder
   Norisbank zuerst, siehe "Operative Voraussetzung" — kein bekannter Blocker)
   — Live-Test durch den Nutzer.
7. Danach auf die anderen beiden Banken erweitern (DKB erst nach Lösung des
   `NEED_PT_DECOUPLED`-Blockers, siehe oben).