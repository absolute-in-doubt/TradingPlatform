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
@Table(name = "trade_facts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeFactEntity {

    @Id
    @Column(name = "trade_id")
    private UUID tradeId;

    @Column(name = "portfolio_id", nullable = false)
    private UUID portfolioId;

    @Column(name = "investor_id", nullable = false)
    private UUID investorId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "trade_type", nullable = false, length = 10)
    private String tradeType;

    @Column(nullable = false, precision = 14, scale = 6)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal price;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;
}
