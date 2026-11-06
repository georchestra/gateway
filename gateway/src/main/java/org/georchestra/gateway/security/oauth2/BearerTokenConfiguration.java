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
import java.util.Set;

import org.georchestra.gateway.security.ServerHttpSecurityCustomizer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.extern.slf4j.Slf4j;

/**
 * Bearer access token authentication for API calls, enabled with
 * {@code georchestra.gateway.security.oauth2.bearer.enabled=true}, see
 * {@link BearerTokenConfigProperties}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BearerTokenConfigProperties.class)
@ConditionalOnProperty(name = "georchestra.gateway.security.oauth2.bearer.enabled", havingValue = "true")
@Slf4j(topic = "org.georchestra.gateway.security.oauth2")
public class BearerTokenConfiguration {

    @Bean
    ServerHttpSecurityCustomizer bearerTokenAuthenticationCustomizer(BearerTokenConfigProperties config,
            InMemoryReactiveClientRegistrationRepository registrations,
            @Qualifier("oauth2WebClient") WebClient oauth2WebClient) {

        ClientRegistration registration = registration(config, registrations);
        ReactiveJwtDecoder decoder = jwtDecoder(config, registration, oauth2WebClient);
        String userNameAttribute = registration.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();
        BearerTokenAuthenticationConverter converter = new BearerTokenAuthenticationConverter(
                registration.getRegistrationId(), StringUtils.hasText(userNameAttribute) ? userNameAttribute : "sub");

        log.info("Enabling bearer access token authentication, tokens of client registration '{}'",
                registration.getRegistrationId());

        return new ServerHttpSecurityCustomizer() {
            @Override
            public void customize(ServerHttpSecurity http) {
                http.oauth2ResourceServer(
                        server -> server.jwt(jwt -> jwt.jwtDecoder(decoder).jwtAuthenticationConverter(converter)));
            }

            @Override
            public int getOrder() {
                // after the OAuth2 login customizers
                return 2;
            }
        };
    }

    static ClientRegistration registration(BearerTokenConfigProperties config,
            InMemoryReactiveClientRegistrationRepository registrations) {
        List<ClientRegistration> all = new ArrayList<>();
        registrations.forEach(all::add);
        if (StringUtils.hasText(config.getRegistrationId())) {
            return all.stream().filter(r -> r.getRegistrationId().equals(config.getRegistrationId())).findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Bearer token authentication: no client registration '" + config.getRegistrationId()
                                    + "' (georchestra.gateway.security.oauth2.bearer.registration-id)"));
        }
        if (all.size() != 1) {
            throw new IllegalStateException(
                    "Bearer token authentication: set georchestra.gateway.security.oauth2.bearer.registration-id, "
                            + all.size() + " client registrations found");
        }
        return all.get(0);
    }

    static ReactiveJwtDecoder jwtDecoder(BearerTokenConfigProperties config, ClientRegistration registration,
            WebClient webClient) {
        String jwkSetUri = StringUtils.hasText(config.getJwkSetUri()) ? config.getJwkSetUri()
                : registration.getProviderDetails().getJwkSetUri();
        if (!StringUtils.hasText(jwkSetUri)) {
            throw new IllegalStateException("Bearer token authentication: no jwk-set-uri for client registration '"
                    + registration.getRegistrationId() + "'");
        }
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).webClient(webClient)
                .jwsAlgorithms(algorithms -> algorithms
                        .addAll(Set.of(SignatureAlgorithm.RS256, SignatureAlgorithm.RS384, SignatureAlgorithm.RS512,
                                SignatureAlgorithm.PS256, SignatureAlgorithm.PS384, SignatureAlgorithm.PS512,
                                SignatureAlgorithm.ES256, SignatureAlgorithm.ES384, SignatureAlgorithm.ES512)))
                .build();
        decoder.setJwtValidator(validator(config));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> validator(BearerTokenConfigProperties config) {
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators
                .add(StringUtils.hasText(config.getIssuer()) ? JwtValidators.createDefaultWithIssuer(config.getIssuer())
                        : JwtValidators.createDefault());
        if (!config.getAudiences().isEmpty()) {
            validators.add(jwt -> {
                List<String> audiences = jwt.getAudience();
                boolean accepted = audiences != null && audiences.stream().anyMatch(config.getAudiences()::contains);
                return accepted ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                                "The token audience is not accepted by the gateway", null));
            });
        }
        return new DelegatingOAuth2TokenValidator<>(validators);
    }
}
