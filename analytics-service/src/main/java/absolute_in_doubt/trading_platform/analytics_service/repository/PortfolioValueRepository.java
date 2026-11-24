package absolute_in_doubt.trading_platform.analytics_service.repository;

import absolute_in_doubt.trading_platform.analytics_service.entity.PortfolioValueEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.UUID;

@Repository
public interface PortfolioValueRepository extends JpaRepository<PortfolioValueEntity, UUID> {
    @Query("SELECT COALESCE(SUM(pv.totalValue), 0) FROM PortfolioValueEntity pv")
    BigDecimal sumTotalPortfolioValues();
}
