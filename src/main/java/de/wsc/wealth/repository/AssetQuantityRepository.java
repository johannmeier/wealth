package de.wsc.wealth.repository;

import de.wsc.wealth.domain.Asset;
import de.wsc.wealth.domain.Depot;
import de.wsc.wealth.domain.AssetQuantity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AssetQuantityRepository extends JpaRepository<AssetQuantity, Long> {
    List<AssetQuantity> findByAssetAndDepotOrderByDateDesc(Asset asset, Depot depot);
    List<AssetQuantity> findByAssetOrderByDateDesc(Asset asset);
    List<AssetQuantity> findByDepotOrderByDateDesc(Depot depot);
    Optional<AssetQuantity> findFirstByAssetAndDepotOrderByDateDesc(Asset asset, Depot depot);
    Optional<AssetQuantity> findByAssetAndDepotAndDate(Asset asset, Depot depot, LocalDate date);
    void deleteByAsset(Asset asset);
    boolean existsByAsset(Asset asset);

    @Query("SELECT q FROM AssetQuantity q JOIN FETCH q.asset JOIN FETCH q.depot")
    List<AssetQuantity> findAllWithAssetAndDepot();

    @Query("SELECT DISTINCT q.asset.id FROM AssetQuantity q")
    java.util.Set<Long> findDistinctAssetIds();
}
