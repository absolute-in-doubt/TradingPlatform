package absolute_in_doubt.trading_platform.analytics_service.repository;

import absolute_in_doubt.trading_platform.analytics_service.entity.AssetRefEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AssetRefRepository extends JpaRepository<AssetRefEntity, UUID> {
}
