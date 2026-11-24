package absolute_in_doubt.trading_platform.analytics_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetPriceUpdatedPayload {
    private String assetId;
    private String ticker;
    private String categoryName;
    private String currency;
    private String oldPrice;
    private String newPrice;
    private String changePercent;
    private String updatedAt;
}
