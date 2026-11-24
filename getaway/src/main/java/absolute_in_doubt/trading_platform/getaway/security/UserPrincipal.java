package absolute_in_doubt.trading_platform.getaway.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPrincipal {
    private UUID userId;
    private String email;
    private String role; // INVESTOR, BROKER, ADMIN
}
