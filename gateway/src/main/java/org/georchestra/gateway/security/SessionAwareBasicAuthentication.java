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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.georchestra.gateway.security.ldap.extended.GeorchestraUserNamePasswordAuthenticationToken;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.authentication.HttpBasicServerAuthenticationEntryPoint;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Basic sessions with bounded authentication age and standard session persistence.
 * No passwords or successful-authentication results are cached across sessions.
 */
final class SessionAwareBasicAuthentication {
    static final String VERIFIED_AT = SessionAwareBasicAuthentication.class.getName() + ".verifiedAt";
    private static final String VERIFIED_IDENTITY = VERIFIED_AT + ".identity";
    private static final String LOGIN_NAME = VERIFIED_AT + ".login";
    private static final String FORM_SESSION = VERIFIED_AT + ".form";
    private static final String FRESH_RESULT_AT = VERIFIED_AT + ".result";
    private static final Object EXCHANGE_KEY = new Object();
    private static final String SECURITY_CONTEXT = WebSessionServerSecurityContextRepository.DEFAULT_SPRING_SECURITY_CONTEXT_ATTR_NAME;

    private final ReactiveAuthenticationManager delegate;
    private final Duration interval;
    private final Clock clock;
    private final ConcurrentHashMap<String, Mono<Verified>> inFlight = new ConcurrentHashMap<>();
    private final byte[] digestSalt = new byte[32];
    private final HttpBasicServerAuthenticationEntryPoint entryPoint = new HttpBasicServerAuthenticationEntryPoint();

    SessionAwareBasicAuthentication(ReactiveAuthenticationManager delegate, Duration interval, Clock clock) {
        if (interval == null || interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("Basic session reauthentication interval must be positive");
        }
        this.delegate = Objects.requireNonNull(delegate);
        this.interval = interval;
        this.clock = Objects.requireNonNull(clock);
        new SecureRandom().nextBytes(digestSalt);
    }

    static void configure(ServerHttpSecurity http, ReactiveAuthenticationManager manager, Duration interval) {
        new SessionAwareBasicAuthentication(manager, interval, Clock.systemUTC()).configure(http);
    }

    void configure(ServerHttpSecurity http) {
        http.httpBasic(basic -> basic.authenticationManager(this::authenticate)
                .securityContextRepository(new BasicRepository()));
        http.addFilterBefore((exchange, chain) -> {
            return ReactiveSecurityContextHolder.getContext()
                    .flatMap(context -> Mono.justOrEmpty(context.getAuthentication()))
                    .map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty())
                    .flatMap(current -> exchange.getSession().flatMap(session -> {
                        Authentication auth = latest(session, current.orElse(null));
                        if (isBasic(auth) && !Boolean.TRUE.equals(session.getAttribute(FORM_SESSION))
                                && !fresh(session, auth) && !hasBasicHeader(exchange)) {
                            return challenge(exchange);
                        }
                        Mono<Void> next = chain.filter(exchange);
                        return auth == null ? next : next.contextWrite(ReactiveSecurityContextHolder
                                .withSecurityContext(Mono.just(new SecurityContextImpl(auth))));
                    })).contextWrite(context -> context.put(EXCHANGE_KEY, exchange));
        }, SecurityWebFiltersOrder.HTTP_BASIC);
        // Profile resolution in the existing gateway may still perform blocking LDAP reads.
        http.addFilterAfter((exchange, chain) -> ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication).filter(SessionAwareBasicAuthentication::isBasic)
                .hasElement().flatMap(basic -> basic
                        ? Mono.defer(() -> chain.filter(exchange)).subscribeOn(Schedulers.boundedElastic())
                        : chain.filter(exchange)), SecurityWebFiltersOrder.ANONYMOUS_AUTHENTICATION);
    }

    private Mono<Authentication> authenticate(Authentication token) {
        return Mono.deferContextual(context -> {
            ServerWebExchange exchange = context.get(EXCHANGE_KEY);
            return exchange.getSession().flatMap(session -> ReactiveSecurityContextHolder.getContext()
                    .flatMap(securityContext -> Mono.justOrEmpty(securityContext.getAuthentication()))
                    .map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty())
                    .flatMap(current -> {
                        Authentication existing = latest(session, current.orElse(null));
                        String login = session.getAttribute(LOGIN_NAME);
                        if (isBasic(existing) && token.getName().equals(login) && fresh(session, existing)) {
                            return Mono.just(existing);
                        }
                        return verify(exchange, session, token, existing);
                    }));
        });
    }

    private Mono<Authentication> verify(ServerWebExchange exchange, WebSession session, Authentication token,
            Authentication existing) {
        boolean renewing = isBasic(existing) && !fresh(session, existing);
        Mono<Verified> operation;
        if (renewing) {
            // Coalesce only concurrent renewals of this session AND these credentials.
            // The salted digest is never logged. Entries disappear when verification ends.
            String key = session.getId() + ":" + credentialDigest(token);
            operation = inFlight.computeIfAbsent(key, ignored -> performVerification(token)
                    .doFinally(signal -> inFlight.remove(key)).cache());
        } else {
            operation = performVerification(token);
        }
        return operation.doOnNext(result -> exchange.getAttributes().put(FRESH_RESULT_AT, result))
                .map(Verified::authentication)
                .onErrorResume(AuthenticationException.class, error -> renewing
                        ? session.invalidate().then(Mono.error(error)) : Mono.error(error));
    }

    private Mono<Verified> performVerification(Authentication token) {
        return Mono.defer(() -> delegate.authenticate(token)).map(authentication -> {
            erase(authentication);
            return new Verified(authentication, clock.instant().toEpochMilli(), token.getName());
        });
    }

    private String credentialDigest(Authentication token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(digestSalt);
            digest.update(token.getName().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(Objects.toString(token.getCredentials(), "").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static Authentication latest(WebSession session, Authentication loaded) {
        SecurityContext stored = session.getAttribute(SECURITY_CONTEXT);
        return isBasic(loaded) && stored != null && isBasic(stored.getAuthentication())
                ? stored.getAuthentication() : loaded;
    }

    private boolean fresh(WebSession session, Authentication authentication) {
        Object value = session.getAttribute(VERIFIED_AT);
        if (!(value instanceof Number number) || !identity(authentication).equals(session.getAttribute(VERIFIED_IDENTITY))) {
            return false;
        }
        Instant verified = Instant.ofEpochMilli(number.longValue());
        Instant now = clock.instant();
        return !verified.isAfter(now) && Duration.between(verified, now).compareTo(interval) < 0;
    }

    private Mono<Void> challenge(ServerWebExchange exchange) {
        // Keep the expired context server-side for the credential retry, but never
        // authorize the cookie-only request. This preserves a stable coordination key.
        return entryPoint.commence(exchange,
                new InsufficientAuthenticationException("Basic session requires reauthentication"));
    }

    private static boolean hasBasicHeader(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        return header != null && header.regionMatches(true, 0, "Basic ", 0, 6);
    }

    private static boolean isBasic(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && (authentication instanceof UsernamePasswordAuthenticationToken
                        || authentication instanceof GeorchestraUserNamePasswordAuthenticationToken);
    }

    private static String identity(Authentication authentication) {
        String provider = authentication instanceof GeorchestraUserNamePasswordAuthenticationToken ldap
                ? ldap.getConfigName() : "";
        return provider + ":" + authentication.getName();
    }

    private static void erase(Authentication authentication) {
        if (authentication instanceof CredentialsContainer credentials) {
            credentials.eraseCredentials();
        }
    }

    static ServerSecurityContextRepository formRepository() {
        // Distinguish newly established form sessions from Basic sessions. OIDC uses
        // a different authentication type and is outside this mechanism entirely.
        return new WebSessionServerSecurityContextRepository() {
            @Override
            public Mono<Void> save(ServerWebExchange exchange, SecurityContext context) {
                if (context == null) {
                    return super.save(exchange, null);
                }
                erase(context.getAuthentication());
                return exchange.getSession().doOnNext(session -> {
                    session.getAttributes().put(FORM_SESSION, true);
                    session.getAttributes().remove(VERIFIED_AT);
                    session.getAttributes().remove(VERIFIED_IDENTITY);
                    session.getAttributes().remove(LOGIN_NAME);
                }).then(super.save(exchange, context));
            }
        };
    }

    private final class BasicRepository extends WebSessionServerSecurityContextRepository {
        @Override
        public Mono<Void> save(ServerWebExchange exchange, SecurityContext context) {
            if (context == null) {
                return super.save(exchange, null);
            }
            return exchange.getSession().flatMap(session -> {
                SecurityContext previous = session.getAttribute(SECURITY_CONTEXT);
                Authentication current = context.getAuthentication();
                Verified verified = exchange.getAttribute(FRESH_RESULT_AT);
                if (verified == null && previous != null && previous.getAuthentication() == current) {
                    return Mono.empty(); // Reuse does not extend the absolute verification interval.
                }
                if (verified == null) {
                    throw new IllegalStateException("Missing successful Basic verification timestamp");
                }
                session.getAttributes().put(VERIFIED_AT, verified.at());
                session.getAttributes().put(LOGIN_NAME, verified.login());
                session.getAttributes().put(VERIFIED_IDENTITY, identity(current));
                session.getAttributes().remove(FORM_SESSION);
                if (previous != null && isBasic(previous.getAuthentication())
                        && identity(previous.getAuthentication()).equals(identity(current))) {
                    // Periodic verification replaces the whole authentication (including
                    // authorities), but retains the already protected session id.
                    session.getAttributes().put(SECURITY_CONTEXT, context);
                    return Mono.empty();
                }
                return super.save(exchange, context); // New login/identity: rotate against fixation.
            });
        }
    }

    private record Verified(Authentication authentication, long at, String login) {
    }
}
