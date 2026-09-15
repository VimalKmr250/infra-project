package com.vksiv.personal.apps.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param secret        HS256 signing key. Must be at least 32 bytes.
 *                      Generate one with: openssl rand -base64 48
 * @param expiry        lifetime of an access token
 * @param refreshExpiry lifetime of a refresh token
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("PT1H") Duration expiry,
        @DefaultValue("P7D") Duration refreshExpiry) {
}
