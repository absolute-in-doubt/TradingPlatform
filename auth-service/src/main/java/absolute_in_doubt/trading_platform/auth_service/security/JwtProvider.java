package absolute_in_doubt.trading_platform.auth_service.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtProvider {

    private final byte[] secretKey;
    private final long expirationMs;

    public JwtProvider(
            @Value("${jwt.secret:c3VwZXItc2VjcmV0LWtleS1mb3Itand0LXRyYWRpbmctcGxhdGZvcm0tc2VjdXJpdHk=}") String secret,
            @Value("${jwt.expiration-ms:86400000}") long expirationMs) {
        // Ensure secret is at least 256 bits (32 bytes)
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            this.secretKey = padded;
        } else {
            this.secretKey = keyBytes;
        }
        this.expirationMs = expirationMs;
    }

    public String generateToken(UUID userId, String email, String role) {
        try {
            JWSSigner signer = new MACSigner(secretKey);

            Instant now = Instant.now();
            Instant expiry = now.plusMillis(expirationMs);

            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .subject(userId.toString())
                    .claim("email", email)
                    .claim("role", role)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(expiry))
                    .jwtID(UUID.randomUUID().toString())
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS256),
                    claimsSet
            );

            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("Failed to generate JWT", e);
        }
    }

    public Instant getExpiryInstant() {
        return Instant.now().plusMillis(expirationMs);
    }
}
