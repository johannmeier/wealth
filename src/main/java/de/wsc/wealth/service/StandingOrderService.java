package de.wsc.wealth.service;

import de.wsc.wealth.domain.Asset;
import de.wsc.wealth.domain.Depot;
import de.wsc.wealth.domain.StandingOrder;
import de.wsc.wealth.repository.DepotRepository;
import de.wsc.wealth.repository.AssetRepository;
import de.wsc.wealth.repository.StandingOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class StandingOrderService {

    private final StandingOrderRepository standingOrderRepository;
    private final AssetRepository assetRepository;
    private final DepotRepository depotRepository;

    public StandingOrderService(StandingOrderRepository standingOrderRepository,
                                AssetRepository assetRepository,
                                DepotRepository depotRepository) {
        this.standingOrderRepository = standingOrderRepository;
        this.assetRepository = assetRepository;
        this.depotRepository = depotRepository;
    }

    @Transactional(readOnly = true)
    public List<StandingOrder> findAll() { return standingOrderRepository.findAllByOrderByNextDateAsc(); }

    @Transactional(readOnly = true)
    public Optional<StandingOrder> findById(Long id) { return standingOrderRepository.findById(id); }

    public StandingOrder save(StandingOrder order, Long assetId, Long depotId) {
        Asset asset = assetRepository.findById(assetId).orElseThrow();
        Depot depot = depotRepository.findById(depotId).orElseThrow();
        order.setAsset(asset);
        order.setDepot(depot);
        return standingOrderRepository.save(order);
    }

    public void delete(Long id) { standingOrderRepository.deleteById(id); }
}
