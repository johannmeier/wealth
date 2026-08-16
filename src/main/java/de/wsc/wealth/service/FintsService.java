package de.wsc.wealth.service;

import de.wsc.wealth.domain.*;
import de.wsc.wealth.dto.ChangedPosition;
import de.wsc.wealth.repository.*;
import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV_Result.GVRSaldoReq;
import org.kapott.hbci.GV_Result.GVRWPDepotList;
import org.kapott.hbci.callback.AbstractHBCICallback;
import org.kapott.hbci.callback.HBCICallback;
import org.kapott.hbci.exceptions.HBCI_Exception;
import org.kapott.hbci.manager.BankInfo;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.manager.HBCIUtils;
import org.kapott.hbci.manager.HBCIVersion;
import org.kapott.hbci.manager.MatrixCode;
import org.kapott.hbci.manager.QRCode;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.passport.HBCIPassportPinTanMemory;
import org.kapott.hbci.status.HBCIExecStatus;
import org.kapott.hbci.structures.Konto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Read-only FinTS (HKSAL/HKWPD) integration.
 *
 * Unlike {@link TradeRepublicService} — where the remote server tracks the OTP dialog itself —
 * HBCI4Java blocks synchronously inside {@link HBCIHandler#execute()} and asks for the TAN via a
 * callback. To offer the same two-phase HTTP flow (start → show TAN challenge → submit TAN), the
 * whole HBCI dialog runs on a background thread per process id, and the callback blocks on a
 * future that {@link #submitTan(String, String)} completes.
 *
 * Credentials are never persisted: userId and PIN only ever exist as method parameters / in the
 * in-memory {@link DialogState} for the duration of one dialog, matching {@link TradeRepublicService}
 * (PIN never written to an entity).
 */
@Service
public class FintsService {

    private static final Logger log = LoggerFactory.getLogger(FintsService.class);

    private static final HBCIVersion HBCI_VERSION = HBCIVersion.HBCI_300;
    private static final Duration CHALLENGE_WAIT = Duration.ofSeconds(30);
    private static final Duration TAN_WAIT = Duration.ofMinutes(10);

    /**
     * HBCIUtils.init()/setParam() are process-wide static state, and it's not documented
     * whether HBCI4Java tolerates concurrent dialogs from different threads. Serializing all
     * dialogs through this lock trades away cross-bank concurrency for correctness; acceptable
     * for a single-user desktop app. Uses a ReentrantLock (not `synchronized`) specifically so a
     * thread waiting for its turn can still be cancelled via Future#cancel(true) — see
     * runDialog()/startDialog()'s CHALLENGE_WAIT timeout handling.
     */
    private static final ReentrantLock HBCI_LOCK = new ReentrantLock();

    /** Suggested defaults for the config form — researched, not guaranteed current, always overridable. See FinTS.md. */
    public record BankDefault(String bankName, String blz, String fintsUrl, String tanVerfahren) {}

    public static final List<BankDefault> SUGGESTED_DEFAULTS = Collections.unmodifiableList(Arrays.asList(
        new BankDefault("Norisbank", "76020070", "https://fints.norisbank.de/", "photoTAN"),
        new BankDefault("DKB", "12030000", "https://fints.dkb.de/fints", "pushTAN"),
        new BankDefault("Consorsbank", "76330000", "https://brokerage-hbci.consorsbank.de/hbci", "")
    ));

    private final FintsConfigRepository configRepository;
    private final BankRepository bankRepository;
    private final AccountRepository accountRepository;
    private final AccountBalanceRepository balanceRepository;
    private final DepotRepository depotRepository;
    private final AssetRepository assetRepository;
    private final AssetQuantityRepository quantityRepository;
    private final AssetSearchService assetSearchService;
    private final TransactionTemplate transactionTemplate;

    private final Map<String, DialogState> pendingDialogs = new ConcurrentHashMap<>();
    private final ExecutorService dialogExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "fints-dialog");
        t.setDaemon(true);
        return t;
    });

    public FintsService(FintsConfigRepository configRepository,
                        BankRepository bankRepository,
                        AccountRepository accountRepository,
                        AccountBalanceRepository balanceRepository,
                        DepotRepository depotRepository,
                        AssetRepository assetRepository,
                        AssetQuantityRepository quantityRepository,
                        AssetSearchService assetSearchService,
                        PlatformTransactionManager transactionManager) {
        this.configRepository = configRepository;
        this.bankRepository = bankRepository;
        this.accountRepository = accountRepository;
        this.balanceRepository = balanceRepository;
        this.depotRepository = depotRepository;
        this.assetRepository = assetRepository;
        this.quantityRepository = quantityRepository;
        this.assetSearchService = assetSearchService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public record SyncResult(
        int balancesUpdated,
        List<String> newAccounts,
        List<ChangedPosition> changedPositions,
        List<String> newAssets,
        Long bankId
    ) {
        public int positionsUpdated() { return changedPositions.size(); }
    }

    /** Either {@code challenge} (TAN needed, call {@link #submitTan}) or {@code result} (bank allowed a TAN-free read) is set. */
    public record DialogStartResult(TanChallenge challenge, SyncResult result) {}

    public record TanChallenge(String processId, String message, String imageBase64, String imageMimeType) {}

    @Transactional(readOnly = true)
    public List<FintsConfig> findAll() {
        return configRepository.findAll();
    }

    @Transactional
    public FintsConfig saveConfig(Long id, String blz, String fintsUrl, String tanVerfahren) {
        FintsConfig config = id != null
            ? configRepository.findById(id).orElseGet(FintsConfig::new)
            : new FintsConfig();
        config.setBlz(blz.strip());
        config.setFintsUrl(fintsUrl.strip());
        config.setTanVerfahren(tanVerfahren == null ? null : tanVerfahren.strip());
        return configRepository.save(config);
    }

    // -------------------------------------------------------------------------
    // Phase 1: start dialog, run HBCI4Java on a background thread until a TAN
    // challenge is available (or the bank didn't require one at all).
    // -------------------------------------------------------------------------

    public DialogStartResult startDialog(Long configId, String userId, String pin) {
        FintsConfig config = configRepository.findById(configId)
            .orElseThrow(() -> new IllegalStateException("FinTS-Konfiguration nicht gefunden."));

        String processId = UUID.randomUUID().toString();
        DialogState state = new DialogState(config, userId.strip(), pin.strip());
        pendingDialogs.put(processId, state);

        state.executorFuture = dialogExecutor.submit(() -> runDialog(processId, state));

        try {
            CompletableFuture.anyOf(state.challengeReady, state.rawResultFuture)
                .get(CHALLENGE_WAIT.toSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            pendingDialogs.remove(processId);
            // Best-effort: frees the thread if it's still waiting for the HBCI_LOCK or blocked in
            // an interruptible wait; a thread already stuck in blocking bank I/O may still run on
            // until its own internal timeout (sweepStaleDialogs() bounds the map-entry lifetime).
            state.executorFuture.cancel(true);
            throw new IllegalStateException("Zeitüberschreitung beim Verbindungsaufbau zur Bank.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Verbindung unterbrochen.");
        } catch (ExecutionException e) {
            pendingDialogs.remove(processId);
            throw unwrap(e);
        }

        if (state.rawResultFuture.isDone() && !state.rawResultFuture.isCompletedExceptionally()) {
            // Bank didn't require a TAN for this read-only request — finish immediately.
            pendingDialogs.remove(processId);
            RawFintsResult raw = join(state.rawResultFuture);
            SyncResult result = transactionTemplate.execute(status -> persistData(config, raw));
            return new DialogStartResult(null, result);
        }
        if (state.rawResultFuture.isCompletedExceptionally()) {
            pendingDialogs.remove(processId);
            join(state.rawResultFuture);
        }

        TanChallenge challenge = state.challenge;
        if (challenge == null) {
            pendingDialogs.remove(processId);
            throw new IllegalStateException("Unerwarteter Zustand: keine TAN-Abfrage und kein Ergebnis.");
        }
        return new DialogStartResult(challenge, null);
    }

    // -------------------------------------------------------------------------
    // Phase 2: deliver the user-entered TAN, wait for the dialog thread to
    // finish the HBCI jobs, then persist the result.
    // -------------------------------------------------------------------------

    public SyncResult submitTan(String processId, String tan) {
        DialogState state = pendingDialogs.remove(processId);
        if (state == null) {
            throw new IllegalStateException("Kein offener FinTS-Vorgang mit dieser Prozess-ID (evtl. abgelaufen).");
        }
        state.tanFuture.complete(tan.strip());

        // Deliberately not @Transactional: this wait is network I/O against the bank (up to
        // TAN_WAIT), not a DB operation — wrapping it in a transaction would hold a pooled DB
        // connection idle for the whole wait. Only the actual persistData() call below runs
        // inside a (short-lived) transaction.
        RawFintsResult raw;
        try {
            raw = state.rawResultFuture.get(TAN_WAIT.toSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Zeitüberschreitung beim Abschluss der FinTS-Abfrage.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Verbindung unterbrochen.");
        } catch (ExecutionException e) {
            throw unwrap(e);
        }
        return transactionTemplate.execute(status -> persistData(state.config, raw));
    }

    // -------------------------------------------------------------------------
    // Background dialog execution (HBCI4Java) — runs on dialogExecutor.
    // -------------------------------------------------------------------------

    private void runDialog(String processId, DialogState state) {
        HBCICallback callback = new FintsCallback(processId, state);
        HBCIPassport passport = null;
        HBCIHandler handle = null;
        boolean locked = false;
        try {
            HBCI_LOCK.lockInterruptibly();
            locked = true;

            Properties props = new Properties();
            HBCIUtils.init(props, callback);
            HBCIUtils.setParam("client.passport.default", "PinTan");
            HBCIUtils.setParam("client.passport.PinTan.init", "1");

            // In-memory passport: no passport file is written to disk, nothing survives the dialog.
            passport = new HBCIPassportPinTanMemory(null);
            passport.setCountry("DE");
            passport.setFilterType("Base64");

            String host;
            int port;
            HostPort hostPort = extractHostPort(state.config.getFintsUrl());
            if (hostPort != null && !hostPort.host().isBlank()) {
                host = hostPort.host();
                port = hostPort.port();
            } else {
                BankInfo info = HBCIUtils.getBankInfo(state.config.getBlz());
                host = info != null ? info.getPinTanAddress() : null;
                port = 443;
            }
            if (host == null || host.isBlank()) {
                throw new IllegalStateException("Keine FinTS-Server-Adresse ermittelbar (BLZ/URL prüfen).");
            }
            passport.setHost(host);
            passport.setPort(port);

            handle = new HBCIHandler(HBCI_VERSION.getId(), passport);

            Konto[] konten = passport.getAccounts();
            if (konten == null || konten.length == 0) {
                throw new IllegalStateException("Keine Konten für diese Zugangsdaten ermittelbar.");
            }

            // Konto.allowedGVs (per hbci4j's own Javadoc) is an optional hint, not the authority on
            // what a job supports — the binding check happens inside newJob()/addToQueue() itself,
            // which tryAddJob() already guards. Pre-filtering on allowedGVs risked silently
            // skipping jobs the bank does support if its string format doesn't match what we'd
            // guess, so every job is attempted against every account and let HBCI4Java decide.
            List<HBCIJob> saldoJobs = new ArrayList<>();
            List<HBCIJob> depotJobs = new ArrayList<>();
            for (Konto k : konten) {
                tryAddJob(handle, "SaldoReq", k, saldoJobs);
                tryAddJob(handle, "WPDepotList", k, depotJobs);
            }
            if (saldoJobs.isEmpty() && depotJobs.isEmpty()) {
                throw new IllegalStateException("Weder Saldo- noch Depotabfrage von der Bank unterstützt.");
            }

            HBCIExecStatus status = handle.execute();
            if (!status.isOK()) {
                throw new IllegalStateException("FinTS-Abfrage fehlgeschlagen: " + status.getErrorString());
            }

            RawFintsResult raw = extractResult(saldoJobs, depotJobs);
            state.rawResultFuture.complete(raw);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("FinTS-Dialog {} abgebrochen (Timeout/Interrupt)", processId);
            // rawResultFuture must settle before challengeReady: startDialog() races both futures
            // via CompletableFuture.anyOf(...), so completing rawResultFuture first guarantees the
            // waiting thread observes the real (exceptional) outcome instead of a still-pending
            // challenge with no result — see fixed startDialog() error handling.
            state.rawResultFuture.completeExceptionally(new IllegalStateException("FinTS-Vorgang abgebrochen."));
            state.challengeReady.complete(null);
        } catch (Exception e) {
            log.warn("FinTS-Dialog {} fehlgeschlagen: {}", processId, e.getMessage());
            state.rawResultFuture.completeExceptionally(
                e instanceof IllegalStateException ? e : new IllegalStateException("FinTS-Fehler: " + e.getMessage(), e));
            state.challengeReady.complete(null);
        } finally {
            if (handle != null) handle.close();
            if (passport != null) passport.close();
            if (locked) HBCI_LOCK.unlock();
        }
    }

    /**
     * Not every account supports every job (e.g. a plain Girokonto has no Wertpapierdepot, so
     * WPDepotList genuinely doesn't apply) — a missing GV must skip that job for that account,
     * not abort the whole sync. HBCI4Java throws on newJob()/addToQueue() when a GV isn't
     * actually available, which is the expected, common case here.
     */
    private void tryAddJob(HBCIHandler handle, String jobName, Konto k, List<HBCIJob> jobs) {
        try {
            HBCIJob job = handle.newJob(jobName);
            job.setParam("my", k);
            job.addToQueue();
            jobs.add(job);
        } catch (Exception e) {
            log.info("{} für Konto {} nicht verfügbar: {}", jobName, k.number, e.getMessage());
        }
    }

    /**
     * Purges dialogs the user abandoned after seeing a TAN challenge (closed the tab, never
     * submitted). Purely in-memory bookkeeping — never touches the DB or the bank, so it doesn't
     * conflict with the "no automatic FinTS sync" design decision in FinTS.md.
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000)
    void sweepStaleDialogs() {
        Instant cutoff = Instant.now().minus(TAN_WAIT).minusSeconds(60);
        pendingDialogs.entrySet().removeIf(e -> e.getValue().createdAt.isBefore(cutoff));
    }

    private record HostPort(String host, int port) {}

    /** Very small URL→host[:port] extraction; FinTS URLs are plain https://host[:port][/path]. */
    private HostPort extractHostPort(String url) {
        if (url == null || url.isBlank()) return null;
        String stripped = url.strip().replaceFirst("^https?://", "");
        int slash = stripped.indexOf('/');
        String hostPort = slash >= 0 ? stripped.substring(0, slash) : stripped;
        int colon = hostPort.indexOf(':');
        if (colon < 0) return new HostPort(hostPort, 443);
        try {
            return new HostPort(hostPort.substring(0, colon), Integer.parseInt(hostPort.substring(colon + 1)));
        } catch (NumberFormatException e) {
            return new HostPort(hostPort.substring(0, colon), 443);
        }
    }

    // Package-private (not private) so FintsServiceTest can exercise persistData() directly
    // without going through a live HBCI4Java dialog.
    record RawFintsResult(List<RawBalance> balances, List<RawPosition> positions) {}
    record RawBalance(String accountNumber, String iban, String currency, BigDecimal amount) {}
    record RawPosition(String depotAccountNumber, String isin, String name, BigDecimal quantity) {}

    private RawFintsResult extractResult(List<HBCIJob> saldoJobs, List<HBCIJob> depotJobs) {
        List<RawBalance> balances = new ArrayList<>();
        for (HBCIJob job : saldoJobs) {
            GVRSaldoReq result = (GVRSaldoReq) job.getJobResult();
            if (!result.isOK()) {
                log.warn("HKSAL fehlgeschlagen: {}", result);
                continue;
            }
            for (GVRSaldoReq.Info info : result.getEntries()) {
                if (info.ready == null || info.ready.value == null) continue;
                balances.add(new RawBalance(
                    info.konto != null ? info.konto.number : null,
                    info.konto != null ? info.konto.iban : null,
                    info.ready.value.getCurr(),
                    info.ready.value.getBigDecimalValue()));
            }
        }

        List<RawPosition> positions = new ArrayList<>();
        for (HBCIJob job : depotJobs) {
            GVRWPDepotList result = (GVRWPDepotList) job.getJobResult();
            if (!result.isOK()) {
                log.warn("HKWPD fehlgeschlagen: {}", result);
                continue;
            }
            for (GVRWPDepotList.Entry entry : result.getEntries()) {
                String depotAccountNumber = entry.depot != null ? entry.depot.number : null;
                for (GVRWPDepotList.Entry.Gattung g : entry.getEntries()) {
                    if (g.saldo == null || g.saldo_type != GVRWPDepotList.Entry.SALDO_TYPE_STCK) continue;
                    positions.add(new RawPosition(depotAccountNumber, g.isin, g.name, g.saldo.getValue()));
                }
            }
        }
        return new RawFintsResult(balances, positions);
    }

    // -------------------------------------------------------------------------
    // Database persistence — mirrors TradeRepublicService.persistData.
    // -------------------------------------------------------------------------

    /**
     * The BLZ's official Bundesbank-registered institute name (from HBCIUtils) is often not the
     * consumer-facing brand — e.g. BLZ 76020070 resolves to "UniCredit Bank - HypoVereinsbank"
     * even though the bank is marketed as "norisbank". Prefer the known brand name for the banks
     * this app ships defaults for; fall back to the BLZ registry, then a plain placeholder.
     */
    private String resolveBankName(String blz) {
        for (BankDefault d : SUGGESTED_DEFAULTS) {
            if (d.blz().equals(blz)) return d.bankName();
        }
        String name = HBCIUtils.getNameForBLZ(blz);
        return name != null && !name.isBlank() ? name : ("FinTS " + blz);
    }

    SyncResult persistData(FintsConfig config, RawFintsResult raw) {
        if (config.getBank() == null) {
            Bank bank = new Bank();
            bank.setName(resolveBankName(config.getBlz()));
            config.setBank(bankRepository.save(bank));
        }
        Bank bank = config.getBank();
        configRepository.save(config);

        LocalDate today = LocalDate.now();
        int balancesUpdated = 0;
        List<String> newAccounts = new ArrayList<>();

        for (RawBalance rb : raw.balances()) {
            String accountNumber = rb.accountNumber() != null ? rb.accountNumber() : rb.iban();
            if (accountNumber == null) continue;
            String currency = rb.currency() != null ? rb.currency() : "EUR";

            String finalAccountNumber = accountNumber;
            Account account = accountRepository.findByBankIdOrderByAccountNumberAsc(bank.getId())
                .stream()
                .filter(a -> finalAccountNumber.equals(a.getAccountNumber()))
                .findFirst()
                .orElseGet(() -> {
                    Account a = new Account();
                    a.setBank(bank);
                    a.setAccountNumber(finalAccountNumber);
                    a.setIban(rb.iban());
                    a.setCurrency(currency);
                    a.setAssetAllocation(AssetAllocation.RISIKOFREI);
                    newAccounts.add(finalAccountNumber);
                    return accountRepository.save(a);
                });

            AccountBalance bal = balanceRepository.findByAccountAndDate(account, today)
                .orElseGet(() -> {
                    AccountBalance b = new AccountBalance();
                    b.setAccount(account);
                    b.setDate(today);
                    return b;
                });
            bal.setBalance(rb.amount());
            balanceRepository.save(bal);
            balancesUpdated++;
        }

        Depot depot = depotRepository.findByBankIdAndName(bank.getId(), bank.getName())
            .orElseGet(() -> {
                Depot d = new Depot();
                d.setName(bank.getName());
                d.setBank(bank);
                return depotRepository.save(d);
            });

        List<ChangedPosition> changedPositions = new ArrayList<>();
        List<String> newAssets = new ArrayList<>();

        for (RawPosition rp : raw.positions()) {
            if (rp.isin() == null || rp.isin().isBlank()) continue;
            String isin = rp.isin();
            BigDecimal newQty = rp.quantity();

            Asset asset = assetRepository.findFirstByIsinAndArchivedFalse(isin)
                .or(() -> assetRepository.findFirstByArchivedTrueAndIsin(isin))
                .orElseGet(() -> createAssetFromIsin(isin, rp.name(), newAssets));

            BigDecimal oldQty = quantityRepository
                .findFirstByAssetAndDepotOrderByDateDesc(asset, depot)
                .map(AssetQuantity::getQuantity)
                .orElse(null);

            if (oldQty == null || oldQty.compareTo(newQty) != 0) {
                AssetQuantity q = quantityRepository.findByAssetAndDepotAndDate(asset, depot, today)
                    .orElseGet(() -> {
                        AssetQuantity aq = new AssetQuantity();
                        aq.setAsset(asset);
                        aq.setDepot(depot);
                        aq.setDate(today);
                        return aq;
                    });
                q.setQuantity(newQty);
                quantityRepository.save(q);
                changedPositions.add(new ChangedPosition(asset.getName(), isin, oldQty, newQty));
            }
        }

        return new SyncResult(balancesUpdated, newAccounts, changedPositions, newAssets, bank.getId());
    }

    private Asset createAssetFromIsin(String isin, String fallbackName, List<String> newAssets) {
        Asset a = new Asset();
        a.setIsin(isin);
        try {
            var results = assetSearchService.search(isin, "EUR");
            if (!results.isEmpty()) {
                var r = results.get(0);
                a.setName(r.getOrDefault("name", fallbackName != null ? fallbackName : isin));
                String sym = r.get("symbol");
                if (sym != null && !sym.isBlank()) a.setSymbol(sym);
                a.setCurrency(r.getOrDefault("currency", "EUR"));
                log.info("Asset via Yahoo Finance aufgelöst: {} → {}", isin, a.getName());
            } else {
                a.setName(fallbackName != null && !fallbackName.isBlank() ? fallbackName : isin);
                a.setCurrency("EUR");
                log.warn("Kein Yahoo-Finance-Treffer für ISIN {}, verwende Bank-Namen/ISIN", isin);
            }
        } catch (Exception e) {
            a.setName(fallbackName != null && !fallbackName.isBlank() ? fallbackName : isin);
            a.setCurrency("EUR");
            log.warn("Yahoo-Finance-Suche für {} fehlgeschlagen: {}", isin, e.getMessage());
        }
        Asset saved = assetRepository.save(a);
        newAssets.add(saved.getName());
        return saved;
    }

    private static RuntimeException unwrap(ExecutionException e) {
        Throwable cause = e.getCause();
        return cause instanceof RuntimeException re ? re : new IllegalStateException(cause);
    }

    private static <T> T join(CompletableFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Verbindung unterbrochen.");
        } catch (ExecutionException e) {
            throw unwrap(e);
        }
    }

    // -------------------------------------------------------------------------
    // Per-dialog state shared between the background HBCI thread and the two
    // HTTP-facing methods above.
    // -------------------------------------------------------------------------

    private static final class DialogState {
        final FintsConfig config;
        final String userId;
        final String pin;
        final Instant createdAt = Instant.now();
        final CompletableFuture<Void> challengeReady = new CompletableFuture<>();
        final CompletableFuture<String> tanFuture = new CompletableFuture<>();
        final CompletableFuture<RawFintsResult> rawResultFuture = new CompletableFuture<>();
        volatile TanChallenge challenge;
        volatile Future<?> executorFuture;

        DialogState(FintsConfig config, String userId, String pin) {
            this.config = config;
            this.userId = userId;
            this.pin = pin;
        }
    }

    /**
     * Bridges HBCI4Java's synchronous, blocking callback to the two-phase HTTP flow: publishes
     * the TAN challenge (text or, for photoTAN/QR-TAN, an image) and blocks on {@link DialogState#tanFuture}
     * until {@link #submitTan(String, String)} completes it.
     */
    private final class FintsCallback extends AbstractHBCICallback {

        private final String processId;
        private final DialogState state;

        FintsCallback(String processId, DialogState state) {
            this.processId = processId;
            this.state = state;
        }

        @Override
        public void log(String msg, int level, Date date, StackTraceElement trace) {
            // Intentionally quiet; HBCIExecStatus already surfaces errors to the caller.
        }

        @Override
        public void status(HBCIPassport passport, int statusTag, Object[] o) {
        }

        @Override
        public void callback(HBCIPassport passport, int reason, String msg, int datatype, StringBuffer retData) {
            switch (reason) {
                case NEED_PASSPHRASE_LOAD:
                case NEED_PASSPHRASE_SAVE:
                    // In-memory passport isn't written to disk; any value satisfies the API.
                    retData.replace(0, retData.length(), state.pin);
                    break;

                case NEED_PT_PIN:
                    retData.replace(0, retData.length(), state.pin);
                    break;

                case NEED_BLZ:
                    retData.replace(0, retData.length(), state.config.getBlz());
                    break;

                case NEED_USERID:
                case NEED_CUSTOMERID:
                    retData.replace(0, retData.length(), state.userId);
                    break;

                case NEED_PT_SECMECH: {
                    String selected = selectSecMech(retData.toString(), state.config.getTanVerfahren());
                    retData.replace(0, retData.length(), selected);
                    break;
                }

                case NEED_PT_TANMEDIA: {
                    // split() on a string with no "|" returns the whole string as its one
                    // element, so this also covers the (common) single-registered-medium case.
                    String selected = retData.toString().split("\\|")[0];
                    retData.replace(0, retData.length(), selected);
                    break;
                }

                case NEED_PT_TAN: {
                    String flicker = retData.toString();
                    if (flicker != null && !flicker.isBlank()) {
                        // chipTAN optical (flicker code): rendering this as a static/animated image
                        // for the web UI is not implemented yet (FlickerRenderer is paint()-based,
                        // not a plain byte[] image) — see FinTS.md / implementation notes.
                        throw new HBCI_Exception("chipTAN optisch wird aktuell nicht unterstützt (nur Text-TAN, photoTAN, QR-TAN).");
                    }
                    String tan = waitForTan(new TanChallenge(processId, msg, null, null));
                    retData.replace(0, retData.length(), tan);
                    break;
                }

                case NEED_PT_PHOTOTAN: {
                    String tan = requestImageTan(retData.toString(), msg, true);
                    retData.replace(0, retData.length(), tan);
                    break;
                }

                case NEED_PT_QRTAN: {
                    String tan = requestImageTan(retData.toString(), msg, false);
                    retData.replace(0, retData.length(), tan);
                    break;
                }

                case HAVE_ERROR:
                    log.warn("FinTS-Fehler ({}): {}", processId, msg);
                    break;

                default:
                    // Not needed for a read-only HKSAL/HKWPD flow.
                    break;
            }
        }

        private String requestImageTan(String rawPayload, String msg, boolean photoTan) {
            try {
                byte[] image;
                String mimeType;
                if (photoTan) {
                    MatrixCode code = MatrixCode.tryParse(rawPayload);
                    if (code == null) return waitForTan(new TanChallenge(processId, msg, null, null));
                    image = code.getImage();
                    mimeType = code.getMimetype();
                } else {
                    QRCode code = QRCode.tryParse(rawPayload, msg);
                    if (code == null) return waitForTan(new TanChallenge(processId, msg, null, null));
                    image = code.getImage();
                    mimeType = code.getMimetype();
                }
                String base64 = image != null ? Base64.getEncoder().encodeToString(image) : null;
                return waitForTan(new TanChallenge(processId, msg, base64, mimeType));
            } catch (Exception e) {
                throw new HBCI_Exception("Fehler beim Aufbereiten der TAN-Grafik: " + e.getMessage(), e);
            }
        }

        private String waitForTan(TanChallenge challenge) {
            state.challenge = challenge;
            state.challengeReady.complete(null);
            try {
                return state.tanFuture.get(TAN_WAIT.toSeconds(), TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new HBCI_Exception("Verbindung unterbrochen.");
            } catch (ExecutionException | TimeoutException e) {
                throw new HBCI_Exception("Keine TAN erhalten: " + e.getMessage());
            }
        }

        private String selectSecMech(String options, String preferred) {
            String[] entries = options.split("\\|");
            if (preferred != null && !preferred.isBlank()) {
                for (String entry : entries) {
                    if (entry.toLowerCase(Locale.ROOT).contains(preferred.toLowerCase(Locale.ROOT))) {
                        return entry.split(":")[0];
                    }
                }
                log.warn("Konfiguriertes TAN-Verfahren '{}' nicht in Bank-Angebot {} gefunden, verwende erstes Verfahren.",
                    preferred, options);
            }
            return entries.length > 0 ? entries[0].split(":")[0] : "";
        }
    }
}
