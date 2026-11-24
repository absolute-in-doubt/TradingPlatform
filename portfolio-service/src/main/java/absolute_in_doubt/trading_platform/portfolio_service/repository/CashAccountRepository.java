package absolute_in_doubt.trading_platform.portfolio_service.repository;

import absolute_in_doubt.trading_platform.portfolio_service.entity.CashAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CashAccountRepository extends JpaRepository<CashAccountEntity, UUID> {
    Optional<CashAccountEntity> findByInvestorIdAndCurrency(UUID investorId, String currency);
}
