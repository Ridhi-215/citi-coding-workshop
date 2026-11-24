package com.example.common.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.common.http.ApiException;
import com.example.common.security.Role;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Issues and verifies HMAC-SHA256 JWTs.
 *
 * <p>Access tokens are short-lived and carry the role used for permission checks.
 * Refresh tokens are long-lived and only exchange for a new access token
 * (auth-service re-reads the user, so role changes and deactivation take effect).
 */
public final class Tokens {

    /** Token type for API calls. */
    public static final String ACCESS = "access";

    /** Token type accepted only by the refresh endpoint. */
    public static final String REFRESH = "refresh";

    /** Lifetime of an access token. */
    public static final Duration ACCESS_TTL = Duration.ofMinutes(30);

    /** Lifetime of a refresh token. */
    public static final Duration REFRESH_TTL = Duration.ofDays(7);

    private static final String ISSUER = "acme-tracker";
    private static final int MIN_SECRET_LENGTH = 32;

    private final Algorithm algorithm;
    private final Clock clock;

    /**
     * Creates a token service.
     *
     * @param secret signing secret (at least 32 characters)
     * @param clock  clock used for issue and expiry times
     * @throws IllegalStateException if the secret is missing or too short
     */
    public Tokens(String secret, Clock clock) {
        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("JWT_SECRET must be set and at least " + MIN_SECRET_LENGTH + " characters");
        }
        this.algorithm = Algorithm.HMAC256(secret);
        this.clock = clock;
    }

    /**
     * Creates a token service from the {@code JWT_SECRET} environment variable.
     *
     * @return token service using the system clock
     */
    public static Tokens fromEnv() {
        return new Tokens(System.getenv("JWT_SECRET"), Clock.systemUTC());
    }

    /**
     * Issues a token.
     *
     * @param principal user the token is for
     * @param type      {@link #ACCESS} or {@link #REFRESH}
     * @return signed JWT
     */
    public String issue(Principal principal, String type) {
        Instant now = clock.instant();
        Duration ttl = REFRESH.equals(type) ? REFRESH_TTL : ACCESS_TTL;
        return JWT.create()
            .withIssuer(ISSUER)
            .withSubject(String.valueOf(principal.userId()))
            .withClaim("email", principal.email())
            .withClaim("name", principal.name())
            .withClaim("role", principal.role().value())
            .withClaim("typ", type)
            .withIssuedAt(now)
            .withExpiresAt(now.plus(ttl))
            .sign(algorithm);
    }

    /**
     * Verifies a token's signature, issuer, expiry and type.
     *
     * @param token        JWT text
     * @param expectedType {@link #ACCESS} or {@link #REFRESH}
     * @return the caller encoded in the token
     * @throws ApiException 401 {@code TOKEN_EXPIRED} or {@code INVALID_TOKEN}
     */
    public Principal verify(String token, String expectedType) {
        try {
            JWTVerifier verifier = ((JWTVerifier.BaseVerification) JWT.require(algorithm)
                .withIssuer(ISSUER)
                .withClaim("typ", expectedType))
                .build(clock);
            DecodedJWT jwt = verifier.verify(token);
            return new Principal(
                Long.parseLong(jwt.getSubject()),
                jwt.getClaim("email").asString(),
                jwt.getClaim("name").asString(),
                Role.from(jwt.getClaim("role").asString()));
        } catch (TokenExpiredException e) {
            throw ApiException.unauthorized("TOKEN_EXPIRED", "Your session has expired, please sign in again");
        } catch (JWTVerificationException | IllegalArgumentException e) {
            throw ApiException.unauthorized("INVALID_TOKEN", "Invalid authentication token");
        }
    }
}
