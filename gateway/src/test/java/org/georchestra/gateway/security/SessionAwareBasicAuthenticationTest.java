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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.session.WebSessionManager;
import org.springframework.web.server.session.DefaultWebSessionManager;
import org.springframework.web.server.session.InMemoryWebSessionStore;

import reactor.core.publisher.Mono;

@SpringJUnitConfig(SessionAwareBasicAuthenticationTest.Config.class)
class SessionAwareBasicAuthenticationTest {

    private WebTestClient client;
    private AtomicInteger authentications;
    private InMemoryWebSessionStore sessions;
    private State state;

    @BeforeEach
    void setUp(ApplicationContext context) {
        client = WebTestClient.bindToApplicationContext(context).configureClient().build();
        authentications = context.getBean(AtomicInteger.class);
        authentications.set(0);
        state = context.getBean(State.class);
        state.password = "password";
        state.admin = true;
        state.enabled = true;
        state.delayMillis = 0;
        sessions = context.getBean(InMemoryWebSessionStore.class);
        sessions.setClock(java.time.Clock.systemUTC());
        List.copyOf(sessions.getSessions().keySet()).forEach(id -> sessions.removeSession(id).block());
    }

    private WebTestClient.RequestHeadersSpec<?> request(String username, String password, String sessionId) {
        var request = client.get().uri("/whoami").header(HttpHeaders.USER_AGENT, "Apache-HttpClient/4.5.14");
        if (username != null) {
            request.headers(headers -> headers.setBasicAuth(username, password));
        }
        if (sessionId != null) {
            request.cookie("SESSION", sessionId);
        }
        return request;
    }

    private ResponseCookie login(String username) {
        var cookie = request(username, "password", null).exchange().expectStatus().isOk().expectBody(String.class)
                .isEqualTo(username).returnResult().getResponseCookies().getFirst("SESSION");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    @Test
    void sessionReuseIsDisabledByDefault() {
        assertThat(new GeorchestraGatewaySecurityConfigProperties().getHttpBasicSession().isEnabled()).isFalse();
    }

    @Test
    void sessionOptionAndUserAgentPatternsBindFromConfiguration() {
        var source = new org.springframework.boot.context.properties.source.MapConfigurationPropertySource(
                java.util.Map.of("georchestra.gateway.security.http-basic-session.enabled", "true",
                        "georchestra.gateway.security.http-basic-user-agent-patterns[0]", ".*QGIS.*"));
        var properties = new org.springframework.boot.context.properties.bind.Binder(source)
                .bind("georchestra.gateway.security", org.springframework.boot.context.properties.bind.Bindable
                        .of(GeorchestraGatewaySecurityConfigProperties.class))
                .get();
        assertThat(properties.getHttpBasicSession().isEnabled()).isTrue();
        assertThat(properties.getHttpBasicUserAgentPatterns()).containsExactly(".*QGIS.*");
    }

    @Test
    void challengeDoesNotAuthenticateOrCreateSession() {
        request(null, null, null).exchange().expectStatus().isUnauthorized().expectHeader()
                .exists(HttpHeaders.WWW_AUTHENTICATE).expectCookie().doesNotExist("SESSION");
        assertThat(authentications.get()).isZero();
        assertThat(sessions.getSessions()).isEmpty();
    }

    @Test
    void successfulBasicAuthenticationCreatesSessionWithoutCredentials() {
        var cookie = login("alice");
        var session = sessions.retrieveSession(cookie.getValue()).block();
        assertThat(session).isNotNull();
        SecurityContext context = session
                .getAttribute(WebSessionServerSecurityContextRepository.DEFAULT_SPRING_SECURITY_CONTEXT_ATTR_NAME);
        assertThat(context).isNotNull();
        assertThat(context.getAuthentication().getCredentials()).isNull();
        assertThat(authentications.get()).isEqualTo(1);
    }

    @Test
    void successiveBasicRequestsReuseSessionWithoutRotatingCookie() {
        var cookie = login("alice");
        for (int i = 0; i < 100; i++) {
            request("alice", "password", cookie.getValue()).exchange().expectStatus().isOk().expectBody(String.class)
                    .isEqualTo("alice").returnResult();
        }
        request("alice", "password", cookie.getValue()).exchange().expectStatus().isOk().expectCookie()
                .doesNotExist("SESSION");
        assertThat(authentications.get()).isEqualTo(1);
        assertThat(sessions.getSessions()).containsOnlyKeys(cookie.getValue());
    }

    @Test
    void parallelBasicRequestsReuseSessionWithoutCookieRaces() {
        var cookie = login("alice");
        var executor = Executors.newFixedThreadPool(8);
        try {
            var futures = IntStream.range(0, 100)
                    .mapToObj(i -> CompletableFuture.runAsync(
                            () -> request("alice", "password", cookie.getValue()).exchange().expectStatus().isOk()
                                    .expectCookie().doesNotExist("SESSION").expectBody(String.class).isEqualTo("alice"),
                            executor))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(futures).orTimeout(30, java.util.concurrent.TimeUnit.SECONDS).join();
        } finally {
            executor.shutdownNow();
        }
        assertThat(authentications.get()).isEqualTo(1);
        assertThat(sessions.getSessions()).containsOnlyKeys(cookie.getValue());
    }

    @Test
    void sessionWorksWithoutAuthorizationHeader() {
        var cookie = login("alice");
        request(null, null, cookie.getValue()).exchange().expectStatus().isOk().expectBody(String.class)
                .isEqualTo("alice");
        assertThat(authentications.get()).isEqualTo(1);
    }

    @Test
    void validSessionDoesNotRecheckRepeatedPasswordForSameUsername() {
        var cookie = login("alice");
        request("alice", "not-checked-with-valid-session", cookie.getValue()).exchange().expectStatus().isOk()
                .expectBody(String.class).isEqualTo("alice");
        assertThat(authentications.get()).isEqualTo(1);
    }

    @Test
    void differentUsernameAuthenticatesAndRotatesSession() {
        var cookie = login("alice");
        var bob = request("bob", "password", cookie.getValue()).exchange().expectStatus().isOk()
                .expectBody(String.class).isEqualTo("bob").returnResult().getResponseCookies().getFirst("SESSION");
        assertThat(bob).isNotNull();
        assertThat(bob.getValue()).isNotEqualTo(cookie.getValue());
        assertThat(authentications.get()).isEqualTo(2);
    }

    @Test
    void differentUsernameWithWrongPasswordCannotUseExistingIdentity() {
        var cookie = login("alice");
        request("bob", "wrong", cookie.getValue()).exchange().expectStatus().isUnauthorized();
        assertThat(authentications.get()).isEqualTo(2);
    }

    @Test
    void invalidCredentialsWithoutSessionAreRejected() {
        request("alice", "wrong", null).exchange().expectStatus().isUnauthorized().expectCookie()
                .doesNotExist("SESSION");
        assertThat(authentications.get()).isEqualTo(1);
        assertThat(sessions.getSessions()).isEmpty();
    }

    @Test
    void clientDroppingSessionCookieIsReauthenticatedOnEachRequest() {
        login("alice");
        login("alice");
        assertThat(authentications.get()).isEqualTo(2);
    }

    @Test
    void expiredSessionIsChallengedAndBasicCanCreateNewSession() {
        var cookie = login("alice");
        sessions.removeSession(cookie.getValue()).block();
        request(null, null, cookie.getValue()).exchange().expectStatus().isUnauthorized();
        assertThat(authentications.get()).isEqualTo(1);
        request("alice", "password", cookie.getValue()).exchange().expectStatus().isOk().expectCookie()
                .exists("SESSION");
        assertThat(authentications.get()).isEqualTo(2);
    }

    @Test
    void logoutInvalidatesSessionAndNextBasicRequestAuthenticatesAgain() {
        var cookie = login("alice");
        client.get().uri("/logout").cookie("SESSION", cookie.getValue()).exchange().expectStatus().isFound();
        request(null, null, cookie.getValue()).exchange().expectStatus().isUnauthorized();
        request("alice", "password", cookie.getValue()).exchange().expectStatus().isOk();
        assertThat(authentications.get()).isEqualTo(2);
    }

    @Test
    void basicSessionContinuationStaysOffTheNettyEventLoop() {
        var cookie = login("alice");
        client.get().uri("/thread").cookie("SESSION", cookie.getValue())
                .header(HttpHeaders.USER_AGENT, "Apache-HttpClient/4.5.14").exchange().expectStatus().isOk()
                .expectBody(String.class).value(name -> assertThat(name).startsWith("boundedElastic-"));
        assertThat(authentications.get()).isEqualTo(1);
    }

    @Test
    void extendedGeorchestraTokenIsReusedAfterRedisSerializerRoundTrip() {
        var cookie = login("extended");
        var session = sessions.retrieveSession(cookie.getValue()).block();
        SecurityContext context = session
                .getAttribute(WebSessionServerSecurityContextRepository.DEFAULT_SPRING_SECURITY_CONTEXT_ATTR_NAME);
        assertThat(context.getAuthentication()).isInstanceOf(
                org.georchestra.gateway.security.ldap.extended.GeorchestraUserNamePasswordAuthenticationToken.class);
        var serializer = new org.georchestra.gateway.session.redis.RedisSessionConfiguration()
                .springSessionDefaultRedisSerializer();
        byte[] encoded = serializer.serialize(context);
        assertThat(new String(encoded, java.nio.charset.StandardCharsets.UTF_8))
                .doesNotContain("SENSITIVE_TEST_PASSWORD");
        var restored = (SecurityContext) serializer.deserialize(encoded);
        session.getAttributes().put(WebSessionServerSecurityContextRepository.DEFAULT_SPRING_SECURITY_CONTEXT_ATTR_NAME,
                restored);
        assertThat(
                ((org.georchestra.gateway.security.ldap.extended.GeorchestraUserNamePasswordAuthenticationToken) restored
                        .getAuthentication()).getConfigName()).isEqualTo("default");
        request("extended", "password", cookie.getValue()).exchange().expectStatus().isOk().expectCookie()
                .doesNotExist("SESSION").expectBody(String.class).isEqualTo("extended");
        assertThat(authentications.get()).isEqualTo(1);
    }

    @Test
    void wrappedCredentialsAreErasedRecursively() {
        var original = UsernamePasswordAuthenticationToken.authenticated("extended", "SENSITIVE_TEST_PASSWORD",
                List.of());
        var wrapped = new org.georchestra.gateway.security.ldap.extended.GeorchestraUserNamePasswordAuthenticationToken(
                "default", original);
        wrapped.eraseCredentials();
        assertThat(original.getCredentials()).isNull();
    }

    @Test
    void existingOidcSessionWorksWithoutBasicAuthentication() {
        var cookie = client.get().uri("/oidc-session").exchange().expectStatus().isOk().returnResult(String.class)
                .getResponseCookies().getFirst("SESSION");
        assertThat(cookie).isNotNull();
        request(null, null, cookie.getValue()).exchange().expectStatus().isOk().expectBody(String.class)
                .isEqualTo("alice");
        assertThat(authentications.get()).isZero();
    }

    @Test
    void explicitBasicCredentialsAreNotReplacedBySameNamedOidcIdentity() {
        var cookie = client.get().uri("/oidc-session").exchange().expectStatus().isOk().returnResult(String.class)
                .getResponseCookies().getFirst("SESSION");
        assertThat(cookie).isNotNull();
        request("alice", "wrong", cookie.getValue()).exchange().expectStatus().isUnauthorized();
        assertThat(authentications.get()).isEqualTo(1);
    }

    @Test
    void firstAuthenticationRotatesAnExistingAnonymousSession() {
        var anonymous = client.get().uri("/seed").exchange().expectStatus().isOk().returnResult(String.class)
                .getResponseCookies().getFirst("SESSION");
        assertThat(anonymous).isNotNull();
        var authenticated = request("alice", "password", anonymous.getValue()).exchange().expectStatus().isOk()
                .returnResult(String.class).getResponseCookies().getFirst("SESSION");
        assertThat(authenticated).isNotNull();
        assertThat(authenticated.getValue()).isNotEqualTo(anonymous.getValue());
    }

    private void age(String id) {
        sessions.retrieveSession(id).block().getAttributes().put(SessionAwareBasicAuthentication.VERIFIED_AT,
                System.currentTimeMillis() - java.time.Duration.ofHours(1).toMillis());
    }

    @Test
    void intervalDefaultsToOneHourAndMustBePositive() {
        var options = new GeorchestraGatewaySecurityConfigProperties().getHttpBasicSession();
        assertThat(options.getReauthenticationInterval()).isEqualTo(java.time.Duration.ofHours(1));
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> options.setReauthenticationInterval(java.time.Duration.ZERO));
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> options.setReauthenticationInterval(java.time.Duration.ofSeconds(-1)));
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> options.setReauthenticationInterval(null));
    }

    @Test
    void nestedDurationBindsAndObsoleteFlatOptionFails() {
        var source = new org.springframework.boot.context.properties.source.MapConfigurationPropertySource(
                java.util.Map.of("http-basic-session.enabled", "true",
                        "http-basic-session.reauthentication-interval", "30m"));
        var properties = new org.springframework.boot.context.properties.bind.Binder(source)
                .bind("", org.springframework.boot.context.properties.bind.Bindable
                        .of(GeorchestraGatewaySecurityConfigProperties.class)).get();
        assertThat(properties.getHttpBasicSession().getReauthenticationInterval())
                .isEqualTo(java.time.Duration.ofMinutes(30));
        var old = new org.springframework.boot.context.properties.source.MapConfigurationPropertySource(
                java.util.Map.of("http-basic-session-enabled", "true"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new org.springframework.boot.context.properties.bind.Binder(old).bind("",
                        org.springframework.boot.context.properties.bind.Bindable
                                .of(GeorchestraGatewaySecurityConfigProperties.class)))
                .hasRootCauseMessage("Replace httpBasicSessionEnabled with httpBasicSession.enabled");
    }

    @Test
    void clientsWithoutCookiesAuthenticateEachTimeAndSessionsExpireNormally() {
        for (int i = 0; i < 3; i++) {
            client.get().uri("/whoami").headers(h -> h.setBasicAuth("alice", "password"))
                    .exchange().expectStatus().isOk().expectCookie().exists("SESSION")
                    .expectCookie().doesNotExist("GW_BASIC_SESSION");
        }
        assertThat(authentications.get()).isEqualTo(3);
        assertThat(sessions.getSessions()).hasSize(3);
        sessions.getSessions().values().forEach(session ->
                assertThat(session.getMaxIdleTime()).isEqualTo(java.time.Duration.ofMinutes(30)));
        sessions.setClock(java.time.Clock.offset(java.time.Clock.systemUTC(), java.time.Duration.ofMinutes(31)));
        sessions.removeExpiredSessions();
        assertThat(sessions.getSessions()).isEmpty();
    }

    @Test
    void anonymousChallengeCreatesNoSessionOrCapabilityCookie() {
        client.get().uri("https://example.org/whoami").header(HttpHeaders.USER_AGENT, "Apache-HttpClient/4.5")
                .exchange().expectStatus().isUnauthorized().expectCookie().doesNotExist("SESSION")
                .expectCookie().doesNotExist("GW_BASIC_SESSION");
        assertThat(sessions.getSessions()).isEmpty();
        assertThat(authentications.get()).isZero();
    }

    @Test
    void reuseDoesNotSlideVerificationDeadline() {
        var cookie = login("alice");
        var session = sessions.retrieveSession(cookie.getValue()).block();
        Object timestamp = session.getAttribute(SessionAwareBasicAuthentication.VERIFIED_AT);
        request("alice", "password", cookie.getValue()).exchange().expectStatus().isOk();
        assertThat((Object) session.getAttribute(SessionAwareBasicAuthentication.VERIFIED_AT)).isEqualTo(timestamp);
        age(cookie.getValue());
        request("alice", "password", cookie.getValue()).exchange().expectStatus().isOk()
                .expectCookie().doesNotExist("SESSION");
        assertThat(authentications.get()).isEqualTo(2);
        assertThat((Long) session.getAttribute(SessionAwareBasicAuthentication.VERIFIED_AT))
                .isGreaterThan((Long) timestamp - 1000);
    }

    @Test
    void expiredCookieIsChallengedAndCredentialRetryRenewsSession() {
        var cookie = login("alice");
        age(cookie.getValue());
        request(null, null, cookie.getValue()).exchange().expectStatus().isUnauthorized()
                .expectHeader().exists(HttpHeaders.WWW_AUTHENTICATE);
        assertThat(authentications.get()).isEqualTo(1);
        request("alice", "password", cookie.getValue()).exchange().expectStatus().isOk();
        request(null, null, cookie.getValue()).exchange().expectStatus().isOk();
        assertThat(authentications.get()).isEqualTo(2);
    }

    @Test
    void passwordRevocationInvalidatesExpiredSession() {
        var cookie = login("alice");
        state.password = "new-password";
        age(cookie.getValue());
        request("alice", "password", cookie.getValue()).exchange().expectStatus().isUnauthorized();
        request(null, null, cookie.getValue()).exchange().expectStatus().isUnauthorized();
        assertThat(sessions.getSessions()).isEmpty();
        request("alice", "new-password", null).exchange().expectStatus().isOk();
    }

    @Test
    void periodicAuthenticationReplacesRevokedAuthorities() {
        var cookie = login("alice");
        client.get().uri("/authorities").cookie("SESSION", cookie.getValue()).exchange()
                .expectStatus().isOk().expectBody(String.class).isEqualTo("ROLE_ADMIN");
        client.get().uri("/admin").cookie("SESSION", cookie.getValue()).exchange().expectStatus().isOk();
        state.admin = false;
        age(cookie.getValue());
        request("alice", "password", cookie.getValue()).exchange().expectStatus().isOk();
        client.get().uri("/authorities").cookie("SESSION", cookie.getValue()).exchange()
                .expectStatus().isOk().expectBody(String.class).isEqualTo("ROLE_USER");
        client.get().uri("/admin").cookie("SESSION", cookie.getValue()).exchange().expectStatus().isForbidden();
        assertThat(authentications.get()).isEqualTo(2);
    }

    @Test
    void concurrentExpiredRequestsShareVerificationWithoutCookieRotation() {
        var cookie = login("alice");
        age(cookie.getValue());
        state.delayMillis = 300;
        var executor = Executors.newFixedThreadPool(16);
        try {
            var futures = IntStream.range(0, 100).mapToObj(i -> CompletableFuture.runAsync(() ->
                    request("alice", "password", cookie.getValue()).exchange().expectStatus().isOk()
                            .expectCookie().doesNotExist("SESSION"), executor)).toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(futures).orTimeout(30, java.util.concurrent.TimeUnit.SECONDS).join();
        } finally {
            executor.shutdownNow();
        }
        assertThat(authentications.get()).isEqualTo(2);
        assertThat(sessions.getSessions()).containsOnlyKeys(cookie.getValue());
    }

    @Test
    void concurrentWrongPasswordCannotPiggybackOnCorrectRenewal() {
        var cookie = login("alice");
        age(cookie.getValue());
        state.delayMillis = 300;
        var valid = CompletableFuture.runAsync(() -> request("alice", "password", cookie.getValue())
                .exchange().expectStatus().isOk());
        request("alice", "wrong", cookie.getValue()).exchange().expectStatus().isUnauthorized();
        valid.join();
        assertThat(authentications.get()).isEqualTo(3);
        request(null, null, cookie.getValue()).exchange().expectStatus().isUnauthorized();
    }

    @Test
    void canonicalUsernameCanDifferFromLoginWithoutRepeatedAuthentication() {
        var cookie = request("alice@example.org", "password", null).exchange().expectStatus().isOk()
                .expectBody(String.class).isEqualTo("alice").returnResult().getResponseCookies().getFirst("SESSION");
        request("alice@example.org", "password", cookie.getValue()).exchange().expectStatus().isOk();
        assertThat(authentications.get()).isEqualTo(1);
    }

    @Test
    void oldUnmarkedBasicSessionRequiresImmediateVerification() {
        var cookie = login("alice");
        sessions.retrieveSession(cookie.getValue()).block().getAttributes()
                .remove(SessionAwareBasicAuthentication.VERIFIED_AT);
        request(null, null, cookie.getValue()).exchange().expectStatus().isUnauthorized();
        request("alice", "password", cookie.getValue()).exchange().expectStatus().isOk();
        assertThat(authentications.get()).isEqualTo(2);
    }

    @Test
    void formSessionIsNotSubjectToBasicRenewal() {
        var cookie = client.get().uri("/form-session").exchange().expectStatus().isOk()
                .returnResult(String.class).getResponseCookies().getFirst("SESSION");
        request(null, null, cookie.getValue()).exchange().expectStatus().isOk()
                .expectBody(String.class).isEqualTo("alice");
        assertThat(authentications.get()).isZero();
    }

    @Test
    void cookieSessionRenewsWithoutAnyProbeMarker() {
        var cookie = login("alice");
        age(cookie.getValue());
        client.get().uri("/whoami").cookie("SESSION", cookie.getValue())
                .headers(h -> h.setBasicAuth("alice", "password")).exchange().expectStatus().isOk();
        request(null, null, cookie.getValue()).exchange().expectStatus().isOk();
        assertThat(authentications.get()).isEqualTo(2);
    }

    @Test
    void futureTimestampDoesNotExtendTrust() {
        var cookie = login("alice");
        sessions.retrieveSession(cookie.getValue()).block().getAttributes()
                .put(SessionAwareBasicAuthentication.VERIFIED_AT, System.currentTimeMillis() + 3600000L);
        request(null, null, cookie.getValue()).exchange().expectStatus().isUnauthorized();
        request("alice", "password", cookie.getValue()).exchange().expectStatus().isOk();
        assertThat(authentications.get()).isEqualTo(2);
    }

    @Test
    void legacyProbeCookieDoesNotGrantAccessAndDoesNotAffectSessionCreation() {
        client.get().uri("/whoami").cookie("GW_BASIC_SESSION", "1")
                .header(HttpHeaders.USER_AGENT, "Apache-HttpClient/4.5").exchange()
                .expectStatus().isUnauthorized().expectCookie().doesNotExist("SESSION");
        assertThat(authentications.get()).isZero();
        client.get().uri("/whoami").cookie("GW_BASIC_SESSION", "wrong")
                .headers(h -> h.setBasicAuth("alice", "password")).exchange().expectStatus().isOk()
                .expectCookie().exists("SESSION").expectCookie().doesNotExist("GW_BASIC_SESSION");
        assertThat(authentications.get()).isEqualTo(1);
    }

    @Test
    void verificationMetadataSurvivesRedisSerializerRoundTrip() {
        var cookie = login("extended");
        var session = sessions.retrieveSession(cookie.getValue()).block();
        var serializer = new org.georchestra.gateway.session.redis.RedisSessionConfiguration()
                .springSessionDefaultRedisSerializer();
        session.getAttributes().replaceAll((key, value) -> serializer.deserialize(serializer.serialize(value)));
        request("extended", "password", cookie.getValue()).exchange().expectStatus().isOk();
        assertThat(authentications.get()).isEqualTo(1);
        age(cookie.getValue());
        request(null, null, cookie.getValue()).exchange().expectStatus().isUnauthorized();
        request("extended", "password", cookie.getValue()).exchange().expectStatus().isOk();
        assertThat(authentications.get()).isEqualTo(2);
    }

    @Test
    void disabledAccountCannotRenewAndOldCookieLosesAccess() {
        var cookie = login("alice");
        state.enabled = false;
        age(cookie.getValue());
        request("alice", "password", cookie.getValue()).exchange().expectStatus().isUnauthorized();
        request(null, null, cookie.getValue()).exchange().expectStatus().isUnauthorized();
        assertThat(sessions.getSessions()).isEmpty();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullSource
    @org.junit.jupiter.params.provider.ValueSource(strings = {"QGIS/3.40", "Apache-HttpClient/4.5", "Java/21",
            "python-requests/2", "UnknownClient"})
    void firstPreemptiveBasicCreatesSessionForEveryClient(String userAgent) {
        var request = client.get().uri("/whoami").headers(h -> h.setBasicAuth("alice", "password"));
        if (userAgent != null) {
            request.header(HttpHeaders.USER_AGENT, userAgent);
        }
        var first = request.exchange().expectStatus().isOk().returnResult(String.class);
        var cookie = first.getResponseCookies().getFirst("SESSION");
        assertThat(cookie).isNotNull();
        assertThat(first.getResponseCookies().getFirst("GW_BASIC_SESSION")).isNull();
        client.get().uri("/whoami").cookie("SESSION", cookie.getValue())
                .headers(h -> h.setBasicAuth("alice", "password")).exchange().expectStatus().isOk();
        assertThat(authentications.get()).isEqualTo(1);
    }

    @Test
    void concurrentInitialRequestsDoNotRequireExtraAuthenticationAfterReceivingCookies() {
        var executor = Executors.newFixedThreadPool(8);
        try {
            var futures = IntStream.range(0, 8).mapToObj(i -> CompletableFuture.runAsync(() -> {
                var cookie = login("alice");
                request("alice", "password", cookie.getValue()).exchange().expectStatus().isOk();
            }, executor)).toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(futures).orTimeout(30, java.util.concurrent.TimeUnit.SECONDS).join();
        } finally {
            executor.shutdownNow();
        }
        assertThat(authentications.get()).isEqualTo(8);
        assertThat(sessions.getSessions()).hasSize(8);
    }

    @Test
    void firstHttpsBasicResponseUsesStandardSecureSessionCookie() {
        var result = client.get().uri("https://example.org/whoami")
                .headers(h -> h.setBasicAuth("alice", "password")).exchange().expectStatus().isOk()
                .returnResult(String.class);
        var cookie = result.getResponseCookies().getFirst("SESSION");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
    }

    static class State {
        volatile String password = "password";
        volatile boolean admin = true;
        volatile boolean enabled = true;
        volatile long delayMillis;
    }

    @Configuration
    @EnableWebFlux
    @EnableWebFluxSecurity
    static class Config {

        @Bean
        State state() {
            return new State();
        }

        @Bean
        AtomicInteger authentications() {
            return new AtomicInteger();
        }

        @Bean
        InMemoryWebSessionStore sessions() {
            return new InMemoryWebSessionStore();
        }

        @Bean
        WebSessionManager webSessionManager(InMemoryWebSessionStore sessions) {
            var manager = new DefaultWebSessionManager();
            manager.setSessionStore(sessions);
            return manager;
        }

        @Bean
        SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, AtomicInteger authentications, State state)
                throws Exception {
            ReactiveAuthenticationManager manager = authentication -> Mono
                    .delay(java.time.Duration.ofMillis(state.delayMillis)).flatMap(ignored -> {
                        authentications.incrementAndGet();
                        if (!state.enabled) {
                            return Mono.error(new org.springframework.security.authentication.DisabledException("Disabled"));
                        }
                        if ("extended".equals(authentication.getName()) && "password".equals(authentication.getCredentials())) {
                            var essence = new org.springframework.security.ldap.userdetails.LdapUserDetailsImpl.Essence();
                            essence.setUsername("extended");
                            essence.setDn("uid=extended,ou=users,dc=example,dc=org");
                            essence.addAuthority(
                                    new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"));
                            var original = UsernamePasswordAuthenticationToken.authenticated(essence.createUserDetails(),
                                    "SENSITIVE_TEST_PASSWORD",
                                    List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                            "ROLE_USER")));
                            return Mono.just(
                                    new org.georchestra.gateway.security.ldap.extended.GeorchestraUserNamePasswordAuthenticationToken(
                                            "default", original));
                        }
                        if (("alice".equals(authentication.getName()) || "bob".equals(authentication.getName())
                                || "alice@example.org".equals(authentication.getName()))
                                && state.password.equals(authentication.getCredentials())) {
                            return Mono.just(UsernamePasswordAuthenticationToken.authenticated(
                                    "alice@example.org".equals(authentication.getName()) ? "alice" : authentication.getName(), null,
                                    List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                            state.admin ? "ROLE_ADMIN" : "ROLE_USER"))));
                        }
                        return Mono.error(new BadCredentialsException("Invalid credentials"));
                    });
            var configuration = new GatewaySecurityConfiguration();
            org.springframework.test.util.ReflectionTestUtils.setField(configuration, "georchestraLogoutUrl",
                    "/?logout");
            var properties = new GeorchestraGatewaySecurityConfigProperties()
                    .setHttpBasicUserAgentPatterns(List.of(".*Apache-HttpClient.*"));
            properties.getHttpBasicSession().setEnabled(true);
            ServerHttpSecurityCustomizer customizer = security -> {
                security.httpBasic(org.springframework.security.config.Customizer.withDefaults());
                security.formLogin(form -> form.disable());
                security.authorizeExchange(rules -> rules.pathMatchers("/admin").hasRole("ADMIN")
                        .anyExchange().permitAll());
                security.exceptionHandling(exceptions -> exceptions.accessDeniedHandler(
                        new org.springframework.security.web.server.authorization.HttpStatusServerAccessDeniedHandler(
                                org.springframework.http.HttpStatus.FORBIDDEN)));
            };
            return configuration.securityWebFilterChain(http, List.of(customizer), manager,
                    configuration.authenticationEntryPoint(), configuration.authenticationFailureHandler(), properties);
        }

        @Bean
        RouterFunction<ServerResponse> backend() {
            return RouterFunctions.route().GET("/admin", request -> ServerResponse.ok().bodyValue("admin"))
                    .GET("/authorities", request ->
                            org.springframework.security.core.context.ReactiveSecurityContextHolder.getContext()
                                    .flatMap(context -> ServerResponse.ok().bodyValue(context.getAuthentication()
                                            .getAuthorities().iterator().next().getAuthority())))
                    .GET("/form-session", request -> SessionAwareBasicAuthentication.formRepository()
                            .save(request.exchange(), new org.springframework.security.core.context.SecurityContextImpl(
                                    UsernamePasswordAuthenticationToken.authenticated("alice", null, List.of())))
                            .then(ServerResponse.ok().bodyValue("form")))
                    .GET("/thread", request ->
                            Mono.defer(() -> ServerResponse.ok().bodyValue(Thread.currentThread().getName())))
                    .GET("/oidc-session", request -> {
                        var user = new org.springframework.security.oauth2.core.user.DefaultOAuth2User(List.of(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")),
                                java.util.Map.of("sub", "alice"), "sub");
                        var authentication = new org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken(
                                user, user.getAuthorities(), "keycloak");
                        return new WebSessionServerSecurityContextRepository().save(request.exchange(),
                                new org.springframework.security.core.context.SecurityContextImpl(authentication))
                                .then(ServerResponse.ok().bodyValue("oidc session"));
                    }).GET("/seed", request -> request.session().flatMap(session -> {
                        session.start();
                        return ServerResponse.ok().bodyValue("anonymous session");
                    }))
                    .GET("/whoami",
                            request -> request.principal().map(java.security.Principal::getName)
                                    .defaultIfEmpty("anonymous").flatMap(name -> ServerResponse.ok().bodyValue(name)))
                    .build();
        }
    }
}
