package absolute_in_doubt.trading_platform.getaway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class AssetDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssetCategoryDto {
        private String id;
        private String name;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssetDto {
        private String id;
        private String ticker;
        private String name;
        private AssetCategoryDto category;
        private BigDecimal currentPrice;
        private String currency;
        private Boolean isActive;
        private Instant priceUpdatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssetPageDto {
        private List<AssetDto> items;
        private Integer totalCount;
    }

    public record CreateAssetInput(
            String ticker,
            String name,
            String categoryId,
            BigDecimal initialPrice,
            String currency
    ) {}

    public record UpdateAssetPriceInput(
            String assetId,
            BigDecimal newPrice
    ) {}

    public record AssetFilter(
            String categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String search,
            Boolean onlyActive
    ) {}

    public record AssetSort(
            String field,
            String direction
    ) {}
}
