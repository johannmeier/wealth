package de.wsc.wealth.service;

import de.wsc.wealth.domain.*;
import de.wsc.wealth.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class AssetService {

    private final AssetRepository assetRepository;
    private final AssetQuantityRepository quantityRepository;
    private final DepotRepository depotRepository;

    public AssetService(AssetRepository assetRepository,
                        AssetQuantityRepository quantityRepository,
                        DepotRepository depotRepository) {
        this.assetRepository = assetRepository;
        this.quantityRepository = quantityRepository;
        this.depotRepository = depotRepository;
    }

    @Transactional(readOnly = true)
    public List<Asset> findAll() { return assetRepository.findAllByOrderByNameAsc(); }

    @Transactional(readOnly = true)
    public Optional<Asset> findById(Long id) { return assetRepository.findById(id); }

    public Asset save(Asset asset) { return assetRepository.save(asset); }

    public void delete(Long id) { assetRepository.deleteById(id); }

    public AssetQuantity saveQuantity(Long assetId, Long depotId, AssetQuantity quantity) {
        Asset asset = assetRepository.findById(assetId).orElseThrow();
        Depot depot = depotRepository.findById(depotId).orElseThrow();
        quantity.setAsset(asset);
        quantity.setDepot(depot);
        return quantityRepository.save(quantity);
    }

    @Transactional(readOnly = true)
    public List<AssetQuantity> getQuantities(Long assetId) {
        Asset asset = assetRepository.findById(assetId).orElseThrow();
        return quantityRepository.findByAssetOrderByDateDesc(asset);
    }

    @Transactional(readOnly = true)
    public List<Depot> findAllDepots() { return depotRepository.findAllByOrderByNameAsc(); }
}
