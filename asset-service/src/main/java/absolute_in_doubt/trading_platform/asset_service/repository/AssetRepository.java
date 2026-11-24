package absolute_in_doubt.trading_platform.asset_service.repository;

import absolute_in_doubt.trading_platform.asset_service.entity.AssetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetRepository extends JpaRepository<AssetEntity, UUID>, JpaSpecificationExecutor<AssetEntity> {
    Optional<AssetEntity> findByTicker(String ticker);
    boolean existsByTicker(String ticker);
}
