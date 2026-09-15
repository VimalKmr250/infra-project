package com.vksiv.personal.apps.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

/** Plain unit tests - no Spring context, no database. */
class JwtServiceTest {

    private static final String SECRET = "unit-test-signing-key-at-least-32-bytes";

    private final JwtService jwtService =
            new JwtService(new JwtProperties(SECRET, Duration.ofMinutes(5), Duration.ofDays(1)));

    @Test
    void roundTripsAnAccessToken() {
        UUID id = UUID.randomUUID();
        Claims claims = jwtService.parse(jwtService.issueAccessToken(id, "a@b.test"));

        assertThat(claims.getSubject()).isEqualTo(id.toString());
        assertThat(claims.get("email", String.class)).isEqualTo("a@b.test");
        assertThat(jwtService.isAccessToken(claims)).isTrue();
        assertThat(jwtService.isRefreshToken(claims)).isFalse();
    }

    @Test
    void distinguishesRefreshTokensFromAccessTokens() {
        Claims claims = jwtService.parse(jwtService.issueRefreshToken(UUID.randomUUID(), "a@b.test"));

        assertThat(jwtService.isRefreshToken(claims)).isTrue();
        assertThat(jwtService.isAccessToken(claims)).isFalse();
    }

    @Test
    void rejectsATokenSignedWithADifferentKey() {
        JwtService other = new JwtService(
                new JwtProperties("a-completely-different-key-also-32-bytes", Duration.ofMinutes(5),
                        Duration.ofDays(1)));
        String foreignToken = other.issueAccessToken(UUID.randomUUID(), "mallory@example.test");

        assertThatThrownBy(() -> jwtService.parse(foreignToken)).isInstanceOf(JwtException.class);
    }

    @Test
    void refusesASecretTooShortForHs256() {
        assertThatThrownBy(() -> new JwtService(
                new JwtProperties("too-short", Duration.ofMinutes(5), Duration.ofDays(1))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }
}
