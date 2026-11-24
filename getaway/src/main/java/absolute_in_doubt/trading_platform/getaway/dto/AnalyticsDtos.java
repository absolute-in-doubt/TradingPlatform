package absolute_in_doubt.trading_platform.getaway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class AnalyticsDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryShareDto {
        private String category;
        private BigDecimal value;
        private BigDecimal share;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PortfolioPerformanceDto {
        private String portfolioId;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private String currency;
        private BigDecimal startValue;
        private BigDecimal endValue;
        private BigDecimal purchasesAmount;
        private BigDecimal salesAmount;
        private BigDecimal profit;
        private BigDecimal yieldPercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportDto {
        private String id;
        private String portfolioId;
        private String type;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private String currency;
        private BigDecimal startValue;
        private BigDecimal endValue;
        private BigDecimal purchasesAmount;
        private BigDecimal salesAmount;
        private BigDecimal netCashFlow;
        private BigDecimal profit;
        private BigDecimal yieldPercent;
        private List<CategoryShareDto> structure;
        private Instant generatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlatformStatsDto {
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private Integer investorsCount;
        private Integer portfoliosCount;
        private Integer tradesCount;
        private BigDecimal tradesVolume;
        private BigDecimal depositsVolume;
        private BigDecimal withdrawalsVolume;
        private BigDecimal totalPortfolioValue;
        private List<CategoryShareDto> structure;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportPageDto {
        private List<ReportDto> items;
        private Integer totalCount;
    }

    public record GenerateReportInput(
            String portfolioId,
            LocalDate periodStart,
            LocalDate periodEnd
    ) {}
}
