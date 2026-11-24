package absolute_in_doubt.trading_platform.analytics_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "asset_refs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetRefEntity {

    @Id
    @Column(name = "asset_id")
    private UUID assetId;

    @Column(nullable = false, length = 20)
    private String ticker;

    @Column(name = "category_name", nullable = false, length = 100)
    private String categoryName;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "current_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal currentPrice;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
