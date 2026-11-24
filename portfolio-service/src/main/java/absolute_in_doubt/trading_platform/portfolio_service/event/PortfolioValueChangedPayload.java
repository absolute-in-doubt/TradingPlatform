package absolute_in_doubt.trading_platform.portfolio_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioValueChangedPayload {
    private String portfolioId;
    private String portfolioName;
    private String investorId;
    private String previousValue;
    private String currentValue;
    private String changePercent;
    private String currency;
    private String calculatedAt;
}
