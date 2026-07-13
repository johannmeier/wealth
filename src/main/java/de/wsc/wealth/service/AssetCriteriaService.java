package de.wsc.wealth.service;

import de.wsc.wealth.domain.*;
import de.wsc.wealth.dto.AssetCriteriaSnapshot;
import de.wsc.wealth.repository.AssetCriteriaValueRepository;
import de.wsc.wealth.repository.CriteriaDefinitionRepository;
import de.wsc.wealth.repository.CriteriaOptionRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
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

    public AssetCriteriaService(CriteriaDefinitionRepository definitionRepository,
                                CriteriaOptionRepository optionRepository,
                                AssetCriteriaValueRepository valueRepository) {
        this.definitionRepository = definitionRepository;
        this.optionRepository = optionRepository;
        this.valueRepository = valueRepository;
    }

    @Transactional(readOnly = true)
    public List<CriteriaDefinition> findAllActive() {
        return definitionRepository.findAllByOrderBySortOrderAsc();
    }

    @Transactional(readOnly = true)
    public Map<Long, AssetCriteriaValue> getValuesByDefinitionId(Asset asset) {
        return valueRepository.findByAsset(asset).stream()
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
    public Map<Long, AssetCriteriaSnapshot> getSnapshotsByAssetId() {
        Map<Long, AssetCriteriaSnapshot> result = new HashMap<>();
        for (AssetCriteriaValue v : valueRepository.findAllWithAssetAndDefinitionAndOption()) {
            String systemCode = v.getDefinition().getSystemCode();
            if (systemCode == null) continue;
            AssetCriteriaSnapshot snapshot = result.computeIfAbsent(v.getAsset().getId(), k -> new AssetCriteriaSnapshot());
            String label = v.getOption() != null ? v.getOption().getValue() : v.getFreeTextValue();
            String code = v.getOption() != null ? v.getOption().getSystemCode() : null;
            switch (systemCode) {
                case SystemCriteria.CATEGORY -> {
                    snapshot.setCategoryLabel(label);
                    snapshot.setCategoryCode(code);
                }
                case SystemCriteria.TYPE -> {
                    snapshot.setTypeLabel(label);
                    snapshot.setTypeCode(code);
                }
                case SystemCriteria.ASSET_ALLOCATION -> {
                    snapshot.setAllocationLabel(label);
                    snapshot.setAllocationCode(code);
                }
                case SystemCriteria.DISTRIBUTION_POLICY -> {
                    snapshot.setDistributionLabel(label);
                    snapshot.setDistributionCode(code);
                }
                case SystemCriteria.INDEX_NAME -> snapshot.setIndexName(v.getFreeTextValue());
                default -> { }
            }
        }
        return result;
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
    public boolean isAutoPrice(Asset asset) {
        AssetCriteriaSnapshot snapshot = getSnapshotsByAssetId().get(asset.getId());
        return snapshot != null && snapshot.isAutoPrice();
    }

    @Transactional(readOnly = true)
    public Map<Long, Boolean> getAutoPriceByAssetId(Collection<Long> assetIds) {
        Map<Long, AssetCriteriaSnapshot> snapshots = getSnapshotsByAssetId();
        Map<Long, Boolean> result = new HashMap<>();
        for (Long id : assetIds) {
            AssetCriteriaSnapshot snapshot = snapshots.get(id);
            result.put(id, snapshot != null && snapshot.isAutoPrice());
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
}
