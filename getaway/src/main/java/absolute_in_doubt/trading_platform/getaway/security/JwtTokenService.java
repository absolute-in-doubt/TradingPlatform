package absolute_in_doubt.trading_platform.getaway.security;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
@Slf4j
public class JwtTokenService {

    private final byte[] secretKey;

    public JwtTokenService(@Value("${jwt.secret:c3VwZXItc2VjcmV0LWtleS1mb3Itand0LXRyYWRpbmctcGxhdGZvcm0tc2VjdXJpdHk=}") String secret) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            this.secretKey = padded;
        } else {
            this.secretKey = keyBytes;
        }
    }

    public UserPrincipal parseToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWSVerifier verifier = new MACVerifier(secretKey);

            if (!signedJWT.verify(verifier)) {
                log.warn("JWT verification failed");
                return null;
            }

            Date expiration = signedJWT.getJWTClaimsSet().getExpirationTime();
            if (expiration != null && expiration.before(new Date())) {
                log.warn("JWT is expired");
                return null;
            }

            String sub = signedJWT.getJWTClaimsSet().getSubject();
            String email = signedJWT.getJWTClaimsSet().getStringClaim("email");
            String role = signedJWT.getJWTClaimsSet().getStringClaim("role");

            return UserPrincipal.builder()
                    .userId(UUID.fromString(sub))
                    .email(email != null ? email : "")
                    .role(role != null ? role : "INVESTOR")
                    .build();

        } catch (Exception e) {
            log.warn("Failed to parse JWT: {}", e.getMessage());
            return null;
        }
    }
}
