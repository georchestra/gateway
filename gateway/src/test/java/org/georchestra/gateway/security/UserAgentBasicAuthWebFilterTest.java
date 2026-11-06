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
package org.georchestra.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.regex.PatternSyntaxException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationEntryPoint;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import reactor.core.publisher.Mono;

/**
 * Exercises the filter in a real Spring Security chain without LDAP or Docker.
 */
@SpringJUnitConfig(UserAgentBasicAuthWebFilterTest.Config.class)
class UserAgentBasicAuthWebFilterTest {

    private WebTestClient client;

    @BeforeEach
    void setUp(ApplicationContext context) {
        client = WebTestClient.bindToApplicationContext(context).configureClient().build();
    }

    @ParameterizedTest
    @ValueSource(strings = { "GeoWebCache/2.0.0", "geowebcache", "QGIS/3.40" })
    void anonymousMatchingClientIsChallengedBeforeBackend(String userAgent) {
        client.get().uri("/public").header(HttpHeaders.USER_AGENT, userAgent).accept(MediaType.TEXT_HTML).exchange()
                .expectStatus().isUnauthorized().expectHeader()
                .valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"Realm\"").expectHeader()
                .doesNotExist(HttpHeaders.LOCATION).expectBody().isEmpty();
    }

    @Test
    void wfsPostIsChallengedBeforeBackend() {
        client.post().uri("/geoserver/wfs").header(HttpHeaders.USER_AGENT, "GeoWebCache/2.0.0")
                .contentType(MediaType.APPLICATION_XML).bodyValue("<GetFeature/>").exchange().expectStatus()
                .isUnauthorized().expectHeader().exists(HttpHeaders.WWW_AUTHENTICATE);
    }

    @Test
    void matchingClientCanRetryWithValidBasicCredentials() {
        client.get().uri("/public").header(HttpHeaders.USER_AGENT, "GeoWebCache/2.0.0").exchange().expectStatus()
                .isUnauthorized();
        client.get().uri("/public").header(HttpHeaders.USER_AGENT, "GeoWebCache/2.0.0")
                .headers(headers -> headers.setBasicAuth("alice", "password")).exchange().expectStatus().isOk()
                .expectBody(String.class).isEqualTo("backend");
    }

    @Test
    void invalidBasicCredentialsAreRejected() {
        client.get().uri("/public").header(HttpHeaders.USER_AGENT, "GeoWebCache/2.0.0")
                .headers(headers -> headers.setBasicAuth("alice", "wrong")).exchange().expectStatus().isUnauthorized()
                .expectHeader().exists(HttpHeaders.WWW_AUTHENTICATE);
    }

    @Test
    void existingAuthenticatedSessionIsAccepted() {
        var session = client.get().uri("/session").exchange().expectStatus().isOk().returnResult(String.class)
                .getResponseCookies().getFirst("SESSION");
        assertThat(session).isNotNull();
        client.get().uri("/public").header(HttpHeaders.USER_AGENT, "GeoWebCache/2.0.0")
                .cookie("SESSION", session.getValue()).exchange().expectStatus().isOk().expectBody(String.class)
                .isEqualTo("backend");
    }

    @Test
    void authenticatedMatchingClientStillObeysAccessRules() {
        client.get().uri("/forbidden").header(HttpHeaders.USER_AGENT, "GeoWebCache/2.0.0")
                .headers(headers -> headers.setBasicAuth("alice", "password")).exchange().expectStatus().isForbidden();
    }

    @Test
    void browserWithoutMatchingUserAgentStillReachesPublicBackend() {
        client.get().uri("/public").header(HttpHeaders.USER_AGENT, "Mozilla/5.0").exchange().expectStatus().isOk()
                .expectBody(String.class).isEqualTo("backend");
    }

    @Test
    void nonMatchingBrowserRetainsLoginRedirect() {
        client.get().uri("/private").header(HttpHeaders.USER_AGENT, "Mozilla/5.0").accept(MediaType.TEXT_HTML)
                .exchange().expectStatus().isFound().expectHeader().valueEquals(HttpHeaders.LOCATION, "/login");
    }

    @Test
    void missingUserAgentStillReachesPublicBackend() {
        client.get().uri("/public").exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("backend");
    }

    @Test
    void patternsMatchEntireHeader() {
        client.get().uri("/public").header(HttpHeaders.USER_AGENT, "prefix-QGIS/3.40").exchange().expectStatus().isOk();
    }

    @Test
    void disabledByDefault() {
        var exchange = org.springframework.mock.web.server.MockServerWebExchange
                .from(org.springframework.mock.http.server.reactive.MockServerHttpRequest.get("/public")
                        .header(HttpHeaders.USER_AGENT, "GeoWebCache/2.0.0"));
        new UserAgentBasicAuthWebFilter(List.of()).filter(exchange, e -> {
            e.getResponse().setStatusCode(org.springframework.http.HttpStatus.OK);
            return e.getResponse().setComplete();
        }).block();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
    }

    @Test
    void invalidPatternFailsAtStartup() {
        assertThatThrownBy(() -> new UserAgentBasicAuthWebFilter(List.of("[")))
                .isInstanceOf(PatternSyntaxException.class);
    }

    @Configuration
    @EnableWebFlux
    @EnableWebFluxSecurity
    static class Config {

        @Bean
        SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) throws Exception {
            ReactiveAuthenticationManager manager = authentication -> {
                if ("alice".equals(authentication.getName()) && "password".equals(authentication.getCredentials())) {
                    return Mono.just(UsernamePasswordAuthenticationToken.authenticated("alice", "unused",
                            List.of(new SimpleGrantedAuthority("ROLE_USER"))));
                }
                return Mono.error(new BadCredentialsException("Invalid credentials"));
            };
            http.csrf(csrf -> csrf.disable());
            http.authenticationManager(manager);
            http.httpBasic(org.springframework.security.config.Customizer.withDefaults());
            http.formLogin(form -> form.disable());
            http.exceptionHandling(handling -> handling
                    .authenticationEntryPoint(new RedirectServerAuthenticationEntryPoint("/login")));
            http.addFilterAfter(new UserAgentBasicAuthWebFilter(List.of("(?i).*GeoWebCache.*", "QGIS/.*")),
                    SecurityWebFiltersOrder.ANONYMOUS_AUTHENTICATION);
            http.authorizeExchange(rules -> rules.pathMatchers("/forbidden").denyAll().pathMatchers("/private")
                    .authenticated().anyExchange().permitAll());
            return http.build();
        }

        @Bean
        RouterFunction<ServerResponse> backend() {
            return RouterFunctions.route().GET("/session", request -> {
                var authentication = UsernamePasswordAuthenticationToken.authenticated("alice", "unused",
                        List.of(new SimpleGrantedAuthority("ROLE_USER")));
                return new WebSessionServerSecurityContextRepository()
                        .save(request.exchange(), new SecurityContextImpl(authentication))
                        .then(ServerResponse.ok().bodyValue("session created"));
            }).route(request -> true, request -> ServerResponse.ok().bodyValue("backend")).build();
        }
    }
}
