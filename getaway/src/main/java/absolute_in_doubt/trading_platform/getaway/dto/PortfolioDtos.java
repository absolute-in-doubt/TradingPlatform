package absolute_in_doubt.trading_platform.getaway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class PortfolioDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PositionDto {
        private String assetId;
        private AssetDtos.AssetDto asset;
        private BigDecimal quantity;
        private BigDecimal avgPurchasePrice;
        private BigDecimal currentPrice;
        private BigDecimal marketValue;
        private BigDecimal profit;
        private BigDecimal profitPercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PortfolioDto {
        private String id;
        private String investorId;
        private String name;
        private String baseCurrency;
        private Instant createdAt;
        private List<PositionDto> positions;
        private BigDecimal totalValue;
        private BigDecimal investedAmount;
        private BigDecimal profit;
        private BigDecimal profitPercent;
        private BigDecimal cashBalance;
    }

    public record CreatePortfolioInput(
            String name,
            String baseCurrency
    ) {}

    public record PortfolioSort(
            String field,
            String direction
    ) {}
}
