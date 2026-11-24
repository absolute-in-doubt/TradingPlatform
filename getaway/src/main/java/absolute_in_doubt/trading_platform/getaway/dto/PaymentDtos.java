package absolute_in_doubt.trading_platform.getaway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class PaymentDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentOperationDto {
        private String id;
        private String type;
        private BigDecimal amount;
        private String currency;
        private String status;
        private String rejectReason;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentPageDto {
        private List<PaymentOperationDto> items;
        private Integer totalCount;
    }

    public record PaymentInput(
            BigDecimal amount,
            String currency
    ) {}

    public record PaymentFilter(
            String type,
            Instant dateFrom,
            Instant dateTo
    ) {}
}
