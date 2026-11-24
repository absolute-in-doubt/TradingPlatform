package absolute_in_doubt.trading_platform.portfolio_service.repository;

import absolute_in_doubt.trading_platform.portfolio_service.entity.PortfolioEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortfolioRepository extends JpaRepository<PortfolioEntity, UUID> {
    List<PortfolioEntity> findByInvestorId(UUID investorId, Sort sort);
    Optional<PortfolioEntity> findByInvestorIdAndName(UUID investorId, String name);
    boolean existsByInvestorIdAndName(UUID investorId, String name);
}
