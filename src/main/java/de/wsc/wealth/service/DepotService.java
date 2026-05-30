package de.wsc.wealth.service;

import de.wsc.wealth.domain.Depot;
import de.wsc.wealth.repository.DepotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class DepotService {

    private final DepotRepository depotRepository;

    public DepotService(DepotRepository depotRepository) {
        this.depotRepository = depotRepository;
    }

    @Transactional(readOnly = true)
    public List<Depot> findAll() { return depotRepository.findAllByOrderByNameAsc(); }

    @Transactional(readOnly = true)
    public Optional<Depot> findById(Long id) { return depotRepository.findById(id); }

    public Depot save(Depot depot) { return depotRepository.save(depot); }

    public void delete(Long id) { depotRepository.deleteById(id); }
}
