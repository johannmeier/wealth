package de.wsc.wealth.repository;

import de.wsc.wealth.domain.Asset;
import de.wsc.wealth.domain.AssetCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    List<Asset> findByCategory(AssetCategory category);
    List<Asset> findBySymbolIsNotNull();
    List<Asset> findAllByOrderByNameAsc();
}
