package de.wsc.wealth.repository;

import de.wsc.wealth.domain.StandingOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StandingOrderRepository extends JpaRepository<StandingOrder, Long> {
    List<StandingOrder> findAllByOrderByNextDateAsc();
}
