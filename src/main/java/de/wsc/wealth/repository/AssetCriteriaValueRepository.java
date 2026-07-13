package de.wsc.wealth.repository;

import de.wsc.wealth.domain.Asset;
import de.wsc.wealth.domain.AssetCriteriaValue;
import de.wsc.wealth.domain.CriteriaDefinition;
import de.wsc.wealth.domain.CriteriaOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface AssetCriteriaValueRepository extends JpaRepository<AssetCriteriaValue, Long> {
    Optional<AssetCriteriaValue> findByAssetAndDefinition(Asset asset, CriteriaDefinition definition);
    boolean existsByAssetAndDefinition(Asset asset, CriteriaDefinition definition);
    List<AssetCriteriaValue> findByAsset(Asset asset);
    void deleteByDefinition(CriteriaDefinition definition);
    void deleteByOption(CriteriaOption option);

    @Query("SELECT v FROM AssetCriteriaValue v JOIN FETCH v.asset JOIN FETCH v.definition LEFT JOIN FETCH v.option")
    List<AssetCriteriaValue> findAllWithAssetAndDefinitionAndOption();
}
