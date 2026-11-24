package absolute_in_doubt.trading_platform.getaway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class TradingDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TradeDto {
        private String id;
        private String portfolioId;
        private String investorId;
        private String brokerId;
        private String assetId;
        private PortfolioDtos.PortfolioDto portfolio;
        private AssetDtos.AssetDto asset;
        private AuthDtos.UserDto broker;
        private String type;
        private BigDecimal quantity;
        private BigDecimal price;
        private BigDecimal totalAmount;
        private String currency;
        private String status;
        private String rejectReason;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TradePageDto {
        private List<TradeDto> items;
        private Integer totalCount;
    }

    public record TradeInput(
            String portfolioId,
            String assetId,
            BigDecimal quantity
    ) {}

    public record TradeFilter(
            String portfolioId,
            String investorId,
            String assetId,
            String type,
            String status,
            Instant dateFrom,
            Instant dateTo
    ) {}

    public record TradeSort(
            String field,
            String direction
    ) {}
}
