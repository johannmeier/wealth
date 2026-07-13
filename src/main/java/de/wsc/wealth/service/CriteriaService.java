package de.wsc.wealth.service;

import de.wsc.wealth.domain.CriteriaDefinition;
import de.wsc.wealth.domain.CriteriaOption;
import de.wsc.wealth.repository.AssetCriteriaValueRepository;
import de.wsc.wealth.repository.CriteriaDefinitionRepository;
import de.wsc.wealth.repository.CriteriaOptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class CriteriaService {

    private final CriteriaDefinitionRepository definitionRepository;
    private final CriteriaOptionRepository optionRepository;
    private final AssetCriteriaValueRepository valueRepository;

    public CriteriaService(CriteriaDefinitionRepository definitionRepository,
                           CriteriaOptionRepository optionRepository,
                           AssetCriteriaValueRepository valueRepository) {
        this.definitionRepository = definitionRepository;
        this.optionRepository = optionRepository;
        this.valueRepository = valueRepository;
    }

    @Transactional(readOnly = true)
    public List<CriteriaDefinition> findAll() {
        return definitionRepository.findAllByOrderBySortOrderAsc();
    }

    @Transactional(readOnly = true)
    public Optional<CriteriaDefinition> findById(Long id) {
        return definitionRepository.findById(id);
    }

    public CriteriaDefinition save(CriteriaDefinition form) {
        if (form.getId() == null) {
            form.setSortOrder(nextDefinitionSortOrder());
            return definitionRepository.save(form);
        }
        CriteriaDefinition existing = definitionRepository.findById(form.getId()).orElseThrow();
        existing.setName(form.getName());
        return definitionRepository.save(existing);
    }

    public void delete(Long id) {
        CriteriaDefinition definition = definitionRepository.findById(id).orElseThrow();
        if (!definition.isDeletable()) {
            throw new IllegalStateException("System-Kriterien können nicht gelöscht werden.");
        }
        valueRepository.deleteByDefinition(definition);
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
