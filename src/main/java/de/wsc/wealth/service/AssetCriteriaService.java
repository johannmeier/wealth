package de.wsc.wealth.service;

import de.wsc.wealth.domain.*;
import de.wsc.wealth.dto.CriteriaBadge;
import de.wsc.wealth.license.LicenseService;
import de.wsc.wealth.repository.AccountCriteriaValueRepository;
import de.wsc.wealth.repository.AssetCriteriaValueRepository;
import de.wsc.wealth.repository.CriteriaDefinitionRepository;
import de.wsc.wealth.repository.CriteriaOptionRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class AssetCriteriaService {

    private final CriteriaDefinitionRepository definitionRepository;
    private final CriteriaOptionRepository optionRepository;
    private final AssetCriteriaValueRepository valueRepository;
    private final AccountCriteriaValueRepository accountValueRepository;
    private final LicenseService licenseService;

    public AssetCriteriaService(CriteriaDefinitionRepository definitionRepository,
                                CriteriaOptionRepository optionRepository,
                                AssetCriteriaValueRepository valueRepository,
                                AccountCriteriaValueRepository accountValueRepository,
                                LicenseService licenseService) {
        this.definitionRepository = definitionRepository;
        this.optionRepository = optionRepository;
        this.valueRepository = valueRepository;
        this.accountValueRepository = accountValueRepository;
        this.licenseService = licenseService;
    }

    /**
     * Only criteria the current license makes usable (see {@link LicenseService#isCriterionUsable}
     * — e.g. a Wittmann-only license renders just the Wittmann field). Definitions the license
     * doesn't cover are excluded here entirely rather than shown-but-disabled, because
     * {@link #saveAssignments(Asset, HttpServletRequest)} iterates this same list: a field that's
     * never rendered would otherwise read as blank and delete the asset's existing assignment for
     * it, which the license gate must never do.
     */
    @Transactional(readOnly = true)
    public List<CriteriaDefinition> findAllActive() {
        return definitionRepository.findAllByOrderBySortOrderAsc().stream()
            .filter(licenseService::isCriterionUsable)
            .toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, AssetCriteriaValue> getValuesByDefinitionId(Asset asset) {
        return valueRepository.findByAsset(asset).stream()
            .collect(Collectors.toMap(v -> v.getDefinition().getId(), v -> v));
    }

    @Transactional(readOnly = true)
    public Map<Long, AccountCriteriaValue> getValuesByDefinitionId(Account account) {
        return accountValueRepository.findByAccount(account).stream()
            .collect(Collectors.toMap(v -> v.getDefinition().getId(), v -> v));
    }

    @Transactional(readOnly = true)
    public Map<Long, List<CriteriaOption>> getOptionsByDefinitionId() {
        Map<Long, List<CriteriaOption>> result = new java.util.LinkedHashMap<>();
        for (CriteriaDefinition definition : findAllActive()) {
            if (definition.getValueType() == CriteriaValueType.FIXED_LIST) {
                result.put(definition.getId(), optionRepository.findByDefinitionOrderBySortOrderAsc(definition));
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Map<Long, String> getIndexNameByAssetId() {
        Map<Long, String> result = new HashMap<>();
        for (AssetCriteriaValue v : valueRepository.findAllWithAssetAndDefinitionAndOption()) {
            if (SystemCriteria.INDEX_NAME.equals(v.getDefinition().getSystemCode())) {
                result.put(v.getAsset().getId(), v.getFreeTextValue());
            }
        }
        return result;
    }

    // Maps a system criterion to the i18n key family used for its FIXED_LIST option labels
    // (kept separate from SystemCriteria's own naming for backward compatibility with
    // pre-existing message keys shared with other parts of the UI, e.g. Account.assetAllocation).
    private static final Map<String, String> SYSTEM_MESSAGE_KEY_PREFIX = Map.of(
        SystemCriteria.CATEGORY, "assetCategory",
        SystemCriteria.TYPE, "assetType",
        SystemCriteria.ASSET_ALLOCATION, "assetAllocation",
        SystemCriteria.DISTRIBUTION_POLICY, "distributionPolicy"
    );

    /**
     * All criteria values assigned to each asset (system + custom), sorted by the criterion's
     * sort order, as ready-to-render badges. The index criterion is excluded since it already
     * has its own dedicated list column. Per-value licensed via {@link #findAllActive()}'s same
     * rule, so e.g. a Wittmann-only license shows Wittmann badges without custom-criteria ones.
     */
    @Transactional(readOnly = true)
    public Map<Long, List<CriteriaBadge>> getPropertyBadgesByAssetId() {
        Map<Long, List<AssetCriteriaValue>> byAsset = new HashMap<>();
        for (AssetCriteriaValue v : valueRepository.findAllWithAssetAndDefinitionAndOption()) {
            if (SystemCriteria.INDEX_NAME.equals(v.getDefinition().getSystemCode())) continue;
            if (!licenseService.isCriterionUsable(v.getDefinition())) continue;
            String display = v.getOption() != null ? v.getOption().getValue() : v.getFreeTextValue();
            if (display == null || display.isBlank()) continue;
            byAsset.computeIfAbsent(v.getAsset().getId(), k -> new java.util.ArrayList<>()).add(v);
        }
        Map<Long, List<CriteriaBadge>> result = new HashMap<>();
        for (Map.Entry<Long, List<AssetCriteriaValue>> entry : byAsset.entrySet()) {
            List<AssetCriteriaValue> values = entry.getValue();
            values.sort(java.util.Comparator.comparing(v -> v.getDefinition().getSortOrder()));
            result.put(entry.getKey(), values.stream()
                .map(v -> toBadge(v.getDefinition(), v.getOption(), v.getFreeTextValue()))
                .toList());
        }
        return result;
    }

    /**
     * All criteria values assigned to each account, sorted by the criterion's sort order, as
     * ready-to-render badges — same shape and per-value license gate as
     * {@link #getPropertyBadgesByAssetId()}.
     */
    @Transactional(readOnly = true)
    public Map<Long, List<CriteriaBadge>> getPropertyBadgesByAccountId() {
        Map<Long, List<AccountCriteriaValue>> byAccount = new HashMap<>();
        for (AccountCriteriaValue v : accountValueRepository.findAllWithAccountAndDefinitionAndOption()) {
            if (SystemCriteria.INDEX_NAME.equals(v.getDefinition().getSystemCode())) continue;
            if (!licenseService.isCriterionUsable(v.getDefinition())) continue;
            String display = v.getOption() != null ? v.getOption().getValue() : v.getFreeTextValue();
            if (display == null || display.isBlank()) continue;
            byAccount.computeIfAbsent(v.getAccount().getId(), k -> new java.util.ArrayList<>()).add(v);
        }
        Map<Long, List<CriteriaBadge>> result = new HashMap<>();
        for (Map.Entry<Long, List<AccountCriteriaValue>> entry : byAccount.entrySet()) {
            List<AccountCriteriaValue> values = entry.getValue();
            values.sort(java.util.Comparator.comparing(v -> v.getDefinition().getSortOrder()));
            result.put(entry.getKey(), values.stream()
                .map(v -> toBadge(v.getDefinition(), v.getOption(), v.getFreeTextValue()))
                .toList());
        }
        return result;
    }

    private CriteriaBadge toBadge(CriteriaDefinition definition, CriteriaOption option, String freeTextValue) {
        String label = option != null ? option.getValue() : freeTextValue;
        String messageKey = null;
        if (option != null && option.getSystemCode() != null) {
            String prefix = SYSTEM_MESSAGE_KEY_PREFIX.get(definition.getSystemCode());
            if (prefix != null) messageKey = prefix + "." + option.getSystemCode();
        }
        return new CriteriaBadge(label, messageKey, definition.getName(), definition.getColorIndex());
    }

    @Transactional(readOnly = true)
    public Map<Long, String> getValuesByAssetId(Long definitionId) {
        Map<Long, String> result = new HashMap<>();
        for (AssetCriteriaValue v : valueRepository.findAllWithAssetAndDefinitionAndOption()) {
            if (!v.getDefinition().getId().equals(definitionId)) continue;
            String display = v.getOption() != null ? v.getOption().getValue() : v.getFreeTextValue();
            if (display != null && !display.isBlank()) result.put(v.getAsset().getId(), display);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Map<Long, String> getValuesByAccountId(Long definitionId) {
        Map<Long, String> result = new HashMap<>();
        for (AccountCriteriaValue v : accountValueRepository.findAllWithAccountAndDefinitionAndOption()) {
            if (!v.getDefinition().getId().equals(definitionId)) continue;
            String display = v.getOption() != null ? v.getOption().getValue() : v.getFreeTextValue();
            if (display != null && !display.isBlank()) result.put(v.getAccount().getId(), display);
        }
        return result;
    }

    /**
     * Used by import/sync paths: assigns the option matching {@code rawOptionSystemCode} if it
     * exists for the given system criterion, falling back to {@code defaultOptionSystemCode}.
     * If neither is found, the value is left unset (mirrors the previous try/valueOf-with-fallback
     * behavior, including leaving distributionPolicy null when undetected).
     */
    public void assignSystemValueOrDefault(Asset asset, String criteriaSystemCode,
                                           String rawOptionSystemCode, String defaultOptionSystemCode) {
        CriteriaDefinition definition = definitionRepository.findBySystemCode(criteriaSystemCode).orElseThrow();
        CriteriaOption option = findOptionBySystemCode(definition, rawOptionSystemCode)
            .or(() -> findOptionBySystemCode(definition, defaultOptionSystemCode))
            .orElse(null);
        if (option != null) {
            setOptionValue(asset, definition, option);
        }
    }

    public void saveAssignments(Asset asset, HttpServletRequest request) {
        for (CriteriaDefinition definition : findAllActive()) {
            String raw = request.getParameter("crit_" + definition.getId());
            if (raw == null || raw.isBlank()) {
                valueRepository.findByAssetAndDefinition(asset, definition).ifPresent(valueRepository::delete);
                continue;
            }
            if (definition.getValueType() == CriteriaValueType.FIXED_LIST) {
                optionRepository.findById(Long.valueOf(raw))
                    .ifPresent(option -> setOptionValue(asset, definition, option));
            } else {
                setFreeTextValue(asset, definition, raw);
            }
        }
    }

    public void saveAssignments(Account account, HttpServletRequest request) {
        for (CriteriaDefinition definition : findAllActive()) {
            String raw = request.getParameter("crit_" + definition.getId());
            if (raw == null || raw.isBlank()) {
                accountValueRepository.findByAccountAndDefinition(account, definition)
                    .ifPresent(accountValueRepository::delete);
                continue;
            }
            if (definition.getValueType() == CriteriaValueType.FIXED_LIST) {
                optionRepository.findById(Long.valueOf(raw))
                    .ifPresent(option -> setOptionValue(account, definition, option));
            } else {
                setFreeTextValue(account, definition, raw);
            }
        }
    }

    private java.util.Optional<CriteriaOption> findOptionBySystemCode(CriteriaDefinition definition, String systemCode) {
        if (systemCode == null) return java.util.Optional.empty();
        return optionRepository.findByDefinitionAndSystemCode(definition, systemCode);
    }

    private void setOptionValue(Asset asset, CriteriaDefinition definition, CriteriaOption option) {
        AssetCriteriaValue value = valueRepository.findByAssetAndDefinition(asset, definition)
            .orElseGet(() -> newValue(asset, definition));
        value.setOption(option);
        value.setFreeTextValue(null);
        valueRepository.save(value);
    }

    private void setFreeTextValue(Asset asset, CriteriaDefinition definition, String text) {
        AssetCriteriaValue value = valueRepository.findByAssetAndDefinition(asset, definition)
            .orElseGet(() -> newValue(asset, definition));
        value.setOption(null);
        value.setFreeTextValue(text);
        valueRepository.save(value);
    }

    private AssetCriteriaValue newValue(Asset asset, CriteriaDefinition definition) {
        AssetCriteriaValue value = new AssetCriteriaValue();
        value.setAsset(asset);
        value.setDefinition(definition);
        return value;
    }

    private void setOptionValue(Account account, CriteriaDefinition definition, CriteriaOption option) {
        AccountCriteriaValue value = accountValueRepository.findByAccountAndDefinition(account, definition)
            .orElseGet(() -> newValue(account, definition));
        value.setOption(option);
        value.setFreeTextValue(null);
        accountValueRepository.save(value);
    }

    private void setFreeTextValue(Account account, CriteriaDefinition definition, String text) {
        AccountCriteriaValue value = accountValueRepository.findByAccountAndDefinition(account, definition)
            .orElseGet(() -> newValue(account, definition));
        value.setOption(null);
        value.setFreeTextValue(text);
        accountValueRepository.save(value);
    }

    private AccountCriteriaValue newValue(Account account, CriteriaDefinition definition) {
        AccountCriteriaValue value = new AccountCriteriaValue();
        value.setAccount(account);
        value.setDefinition(definition);
        return value;
    }
}
