package absolute_in_doubt.trading_platform.notification_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentProcessedPayload {
    private String paymentId;
    private String investorId;
    private String operationType;
    private String amount;
    private String currency;
    private String processedAt;
}
