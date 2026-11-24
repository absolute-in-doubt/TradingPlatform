package absolute_in_doubt.trading_platform.asset_service.repository;

import absolute_in_doubt.trading_platform.asset_service.entity.AssetCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AssetCategoryRepository extends JpaRepository<AssetCategoryEntity, Short> {
    Optional<AssetCategoryEntity> findByCode(String code);
}
