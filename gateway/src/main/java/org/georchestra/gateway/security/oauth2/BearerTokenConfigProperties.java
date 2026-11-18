/*
 * Copyright (C) 2026 by the geOrchestra PSC
 *
 * This file is part of geOrchestra.
 *
 * geOrchestra is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * geOrchestra is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * geOrchestra. If not, see <http://www.gnu.org/licenses/>.
 */
package org.georchestra.gateway.security.oauth2;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Authentication of API calls with an OAuth2 access token
 * ({@code Authorization: Bearer <token>}), in addition to the browser login.
 * <p>
 * The token must be a JWT signed by the provider of the client registration
 * {@link #registrationId}. It is mapped to a geOrchestra user exactly like a
 * browser login with that registration: same OpenID Connect claims mapping
 * ({@code georchestra.gateway.security.oidc.claims...}), same username
 * transformation, same account handling. No session is created.
 * <p>
 * Example:
 *
 * <pre>
 * <code>
 * georchestra:
 *   gateway:
 *     security:
 *       oauth2:
 *         bearer:
 *           enabled: true
 *           registration-id: keycloak
 *           issuer: https://auth.example.com/realms/georchestra
 *           audiences: georchestra-gateway
 * </code>
 * </pre>
 */
@ConfigurationProperties(prefix = "georchestra.gateway.security.oauth2.bearer")
@Data
public class BearerTokenConfigProperties {

    /**
     * Whether API calls may authenticate with a bearer access token. Disabled by
     * default: when enabled, a request carrying an invalid bearer token is rejected
     * (401) instead of being forwarded anonymously.
     */
    private boolean enabled;

    /**
     * Client registration id
     * ({@code spring.security.oauth2.client.registration.<id>}) whose provider
     * issues the accepted tokens, and whose claims mapping applies. Optional when
     * there is a single registration.
     */
    private String registrationId;

    /**
     * JWK set URI used to check the token signature. Defaults to the
     * {@code jwk-set-uri} of the registration's provider.
     */
    private String jwkSetUri;

    /**
     * Expected {@code iss} claim. Not checked when empty.
     */
    private String issuer;

    /**
     * Accepted audiences: the {@code aud} claim must contain one of them. Not
     * checked when empty.
     */
    private List<String> audiences = new ArrayList<>();
}
