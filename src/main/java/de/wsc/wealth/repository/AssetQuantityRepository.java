package de.wsc.wealth.repository;

import de.wsc.wealth.domain.Asset;
import de.wsc.wealth.domain.Depot;
import de.wsc.wealth.domain.AssetQuantity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AssetQuantityRepository extends JpaRepository<AssetQuantity, Long> {
    List<AssetQuantity> findByAssetAndDepotOrderByDateDesc(Asset asset, Depot depot);
    List<AssetQuantity> findByAssetOrderByDateDesc(Asset asset);
    List<AssetQuantity> findByDepotOrderByDateDesc(Depot depot);
    Optional<AssetQuantity> findFirstByAssetAndDepotOrderByDateDesc(Asset asset, Depot depot);
}
