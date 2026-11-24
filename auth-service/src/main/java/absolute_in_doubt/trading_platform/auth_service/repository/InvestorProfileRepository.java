package absolute_in_doubt.trading_platform.auth_service.repository;

import absolute_in_doubt.trading_platform.auth_service.entity.InvestorProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InvestorProfileRepository extends JpaRepository<InvestorProfileEntity, UUID> {
    List<InvestorProfileEntity> findByAssignedBrokerId(UUID assignedBrokerId);
}
