package io.github.defiro;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

/** Signs the ID tokens of {@link FakeOidcProvider}. */
public final class TestTokens {

    private TestTokens() {}

    /** An ID token signed by {@code key}, valid for 5 minutes, with these claims besides its own. */
    public static String idToken(
            RSAKey key, String issuer, String audience, String subject, Map<String, Object> claims) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder all = new JWTClaimsSet.Builder();
        claims.forEach(all::claim);
        all.issuer(issuer)
                .audience(audience)
                .subject(subject)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(5, ChronoUnit.MINUTES)));
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(key.getKeyID())
                            .build(),
                    all.build());
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    public static RSAKey newKey() {
        try {
            return new RSAKeyGenerator(2048).keyIDFromThumbprint(true).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
