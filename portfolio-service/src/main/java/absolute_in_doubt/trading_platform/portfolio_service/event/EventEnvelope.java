package absolute_in_doubt.trading_platform.portfolio_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventEnvelope<T> {
    @Builder.Default
    private String eventId = UUID.randomUUID().toString();
    private String eventType;
    @Builder.Default
    private int eventVersion = 1;
    @Builder.Default
    private String occurredAt = Instant.now().toString();
    @Builder.Default
    private String producer = "portfolio-service";
    private T payload;
}
