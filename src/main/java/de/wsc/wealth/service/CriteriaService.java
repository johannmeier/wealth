package de.wsc.wealth.service;

import de.wsc.wealth.domain.CriteriaDefinition;
import de.wsc.wealth.domain.CriteriaOption;
import de.wsc.wealth.license.LicenseFeature;
import de.wsc.wealth.license.LicenseService;
import de.wsc.wealth.repository.AccountCriteriaValueRepository;
import de.wsc.wealth.repository.AssetCriteriaValueRepository;
import de.wsc.wealth.repository.CriteriaDefinitionRepository;
import de.wsc.wealth.repository.CriteriaOptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class CriteriaService {

    public static final int COLOR_COUNT = 10;

    private final CriteriaDefinitionRepository definitionRepository;
    private final CriteriaOptionRepository optionRepository;
    private final AssetCriteriaValueRepository valueRepository;
    private final AccountCriteriaValueRepository accountValueRepository;
    private final LicenseService licenseService;

    public CriteriaService(CriteriaDefinitionRepository definitionRepository,
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
     * System criteria are always included; user-defined ones only when licensed for
     * {@link LicenseFeature#CUSTOM_CRITERIA} — unlicensed, they're excluded everywhere (this
     * list, the statistics menu, criteria pickers) without ever being deleted, so they reappear
     * exactly as before if the license is renewed.
     */
    @Transactional(readOnly = true)
    public List<CriteriaDefinition> findAll() {
        List<CriteriaDefinition> all = definitionRepository.findAllByOrderBySortOrderAsc();
        if (licenseService.isFeatureEnabled(LicenseFeature.CUSTOM_CRITERIA)) return all;
        return all.stream().filter(d -> d.getSystemCode() != null).toList();
    }

    @Transactional(readOnly = true)
    public Optional<CriteriaDefinition> findById(Long id) {
        return definitionRepository.findById(id);
    }

    public CriteriaDefinition save(CriteriaDefinition form) {
        if (form.getId() == null) {
            if (!licenseService.isFeatureEnabled(LicenseFeature.CUSTOM_CRITERIA)) {
                throw new IllegalStateException("Eigene Kriterien erfordern eine Lizenz.");
            }
            form.setSortOrder(nextDefinitionSortOrder());
            form.setColorIndex(form.getColorIndex() != null ? form.getColorIndex() : nextFreeColorIndex());
            return definitionRepository.save(form);
        }
        CriteriaDefinition existing = definitionRepository.findById(form.getId()).orElseThrow();
        existing.setName(form.getName());
        if (form.getColorIndex() != null) existing.setColorIndex(form.getColorIndex());
        return definitionRepository.save(existing);
    }

    /**
     * The lowest color-pair index (0-9) not yet used by any criterion; once all 10 are taken,
     * falls back to the least-used one so colors stay as spread out as possible.
     */
    @Transactional(readOnly = true)
    public int nextFreeColorIndex() {
        List<CriteriaDefinition> all = definitionRepository.findAllByOrderBySortOrderAsc();
        Set<Integer> used = all.stream().map(CriteriaDefinition::getColorIndex)
            .filter(Objects::nonNull).collect(Collectors.toSet());
        for (int i = 0; i < COLOR_COUNT; i++) {
            if (!used.contains(i)) return i;
        }
        Map<Integer, Long> counts = all.stream().map(CriteriaDefinition::getColorIndex)
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(i -> i, Collectors.counting()));
        int best = 0;
        long bestCount = Long.MAX_VALUE;
        for (int i = 0; i < COLOR_COUNT; i++) {
            long count = counts.getOrDefault(i, 0L);
            if (count < bestCount) {
                bestCount = count;
                best = i;
            }
        }
        return best;
    }

    /** One-time, re-run-safe backfill for criteria that predate the color-pair feature. */
    public void assignMissingColorIndexes() {
        for (CriteriaDefinition definition : definitionRepository.findAllByOrderBySortOrderAsc()) {
            if (definition.getColorIndex() == null) {
                definition.setColorIndex(nextFreeColorIndex());
                definitionRepository.save(definition);
            }
        }
    }

    public void delete(Long id) {
        CriteriaDefinition definition = definitionRepository.findById(id).orElseThrow();
        if (!definition.isDeletable()) {
            throw new IllegalStateException("System-Kriterien können nicht gelöscht werden.");
        }
        valueRepository.deleteByDefinition(definition);
        accountValueRepository.deleteByDefinition(definition);
        optionRepository.deleteAll(optionRepository.findByDefinitionOrderBySortOrderAsc(definition));
        definitionRepository.delete(definition);
    }

    @Transactional(readOnly = true)
    public List<CriteriaOption> getOptions(Long definitionId) {
        CriteriaDefinition definition = definitionRepository.findById(definitionId).orElseThrow();
        return optionRepository.findByDefinitionOrderBySortOrderAsc(definition);
    }

    public CriteriaOption saveOption(Long definitionId, Long optionId, String value) {
        if (optionId != null) {
            CriteriaOption existing = optionRepository.findById(optionId).orElseThrow();
            existing.setValue(value);
            return optionRepository.save(existing);
        }
        CriteriaDefinition definition = definitionRepository.findById(definitionId).orElseThrow();
        CriteriaOption option = new CriteriaOption();
        option.setDefinition(definition);
        option.setValue(value);
        option.setSortOrder(nextOptionSortOrder(definition));
        return optionRepository.save(option);
    }

    public void deleteOption(Long optionId) {
        CriteriaOption option = optionRepository.findById(optionId).orElseThrow();
        if (!option.isDeletable()) {
            throw new IllegalStateException("System-Werte können nicht gelöscht werden.");
        }
        valueRepository.deleteByOption(option);
        accountValueRepository.deleteByOption(option);
        optionRepository.delete(option);
    }

    private int nextDefinitionSortOrder() {
        return definitionRepository.findAllByOrderBySortOrderAsc().stream()
            .mapToInt(CriteriaDefinition::getSortOrder).max().orElse(-1) + 1;
    }

    private int nextOptionSortOrder(CriteriaDefinition definition) {
        return optionRepository.findByDefinitionOrderBySortOrderAsc(definition).stream()
            .mapToInt(CriteriaOption::getSortOrder).max().orElse(-1) + 1;
    }
}
