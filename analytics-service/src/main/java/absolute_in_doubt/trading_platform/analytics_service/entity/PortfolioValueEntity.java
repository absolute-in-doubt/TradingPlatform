package absolute_in_doubt.trading_platform.analytics_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "portfolio_values")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioValueEntity {

    @Id
    @Column(name = "portfolio_id")
    private UUID portfolioId;

    @Column(name = "total_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalValue;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;
}
