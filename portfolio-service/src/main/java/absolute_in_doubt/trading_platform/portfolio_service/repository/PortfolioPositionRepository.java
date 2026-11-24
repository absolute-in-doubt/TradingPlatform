package absolute_in_doubt.trading_platform.portfolio_service.repository;

import absolute_in_doubt.trading_platform.portfolio_service.entity.PortfolioPositionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortfolioPositionRepository extends JpaRepository<PortfolioPositionEntity, UUID> {
    List<PortfolioPositionEntity> findByPortfolioId(UUID portfolioId);
    Optional<PortfolioPositionEntity> findByPortfolioIdAndAssetId(UUID portfolioId, UUID assetId);
    List<PortfolioPositionEntity> findByAssetId(UUID assetId);

    @Query("SELECT DISTINCT p.investorId FROM PortfolioPositionEntity pos JOIN pos.portfolio p WHERE pos.assetId = :assetId AND pos.quantity > 0")
    List<UUID> findDistinctInvestorIdsByAssetId(@Param("assetId") UUID assetId);
}
