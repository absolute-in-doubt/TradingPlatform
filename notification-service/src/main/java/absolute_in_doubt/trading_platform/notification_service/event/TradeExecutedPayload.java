package absolute_in_doubt.trading_platform.notification_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeExecutedPayload {
    private String tradeId;
    private String portfolioId;
    private String investorId;
    private String brokerId;
    private String assetId;
    private String ticker;
    private String tradeType;
    private String quantity;
    private String price;
    private String totalAmount;
    private String currency;
    private String executedAt;
}
