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

import org.georchestra.gateway.security.ldap.extended.GeorchestraUserNamePasswordAuthenticationToken;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Opt-in session reuse for HTTP Basic clients. Uses the standard WebSession
 * store (including Spring Session Redis when configured), without a credentials
 * cache. Only an authenticated password-token session for the same username can
 * avoid reauthentication. New identities and requests without a valid session
 * always go through the existing authentication manager.
 */
final class SessionAwareBasicAuthentication {

    private static final AuthenticationTrustResolver TRUST_RESOLVER = new AuthenticationTrustResolverImpl();

    private SessionAwareBasicAuthentication() {
    }

    static void configure(ServerHttpSecurity http, ReactiveAuthenticationManager delegate) {
        ReactiveAuthenticationManager sessionAwareManager = token -> ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(existing -> isBasicAuthentication(existing)
                        && existing.getName().equals(token.getName()))
                .switchIfEmpty(Mono.defer(() -> delegate.authenticate(token)))
                .map(authentication -> {
                    if (authentication instanceof CredentialsContainer credentials) {
                        credentials.eraseCredentials();
                    }
                    return authentication;
                });

        http.httpBasic(basic -> basic.authenticationManager(sessionAwareManager)
                .securityContextRepository(new BasicSessionSecurityContextRepository()));

        // Basic authentication normally resumes on the LDAP adapter's boundedElastic
        // scheduler. Session reuse skips that adapter. Keep the continuation off
        // Netty's event loop: the existing geOrchestra user mapper may still read
        // the LDAP profile when preparing backend security headers.
        http.addFilterAfter((exchange, chain) -> ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication).filter(SessionAwareBasicAuthentication::isBasicAuthentication)
                .hasElement().flatMap(basicSession -> basicSession
                        ? Mono.defer(() -> chain.filter(exchange)).subscribeOn(Schedulers.boundedElastic())
                        : chain.filter(exchange)), SecurityWebFiltersOrder.ANONYMOUS_AUTHENTICATION);
    }

    private static boolean isBasicAuthentication(Authentication authentication) {
        return (authentication instanceof UsernamePasswordAuthenticationToken
                || authentication instanceof GeorchestraUserNamePasswordAuthenticationToken)
                && authentication.isAuthenticated() && !TRUST_RESOLVER.isAnonymous(authentication);
    }

    /**
     * The standard repository rotates the session id whenever save is called. Avoid
     * saving an unchanged Authentication object: otherwise every repeated Basic
     * request would rotate the cookie, racing with parallel requests and causing
     * unnecessary session writes. New authentications retain Spring's normal
     * session-fixation protection.
     */
    private static final class BasicSessionSecurityContextRepository extends WebSessionServerSecurityContextRepository {

        @Override
        public Mono<Void> save(ServerWebExchange exchange, SecurityContext context) {
            if (context == null) {
                return super.save(exchange, null);
            }
            return exchange.getSession().flatMap(session -> {
                SecurityContext existing = session.getAttribute(DEFAULT_SPRING_SECURITY_CONTEXT_ATTR_NAME);
                if (existing != null && existing.getAuthentication() == context.getAuthentication()) {
                    return Mono.empty();
                }
                return super.save(exchange, context);
            });
        }
    }
}
