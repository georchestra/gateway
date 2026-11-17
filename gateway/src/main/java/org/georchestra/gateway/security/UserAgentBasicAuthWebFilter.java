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

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.server.authentication.HttpBasicServerAuthenticationEntryPoint;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * Challenges anonymous clients selected by their User-Agent before proxying,
 * even when the requested resource allows anonymous access. Registered only
 * inside Spring Security, after Basic and session authentication and before
 * authorization. No backend request is made when a challenge is returned.
 */
final class UserAgentBasicAuthWebFilter implements WebFilter {

    private final List<Pattern> patterns;

    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

    private final HttpBasicServerAuthenticationEntryPoint entryPoint = new HttpBasicServerAuthenticationEntryPoint();

    UserAgentBasicAuthWebFilter(List<String> expressions) {
        // Compile once at startup; reject invalid expressions rather than silently
        // leaving clients without the configured authentication challenge.
        this.patterns = expressions.stream().map(Pattern::compile).toList();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String userAgent = exchange.getRequest().getHeaders().getFirst(HttpHeaders.USER_AGENT);
        if (userAgent == null || patterns.stream().noneMatch(pattern -> pattern.matcher(userAgent).matches())) {
            return chain.filter(exchange);
        }
        return ReactiveSecurityContextHolder.getContext().map(SecurityContext::getAuthentication).filter(
                authentication -> authentication.isAuthenticated() && !trustResolver.isAnonymous(authentication))
                .hasElement().flatMap(authenticated -> {
                    if (authenticated) {
                        return chain.filter(exchange);
                    }
                    return entryPoint.commence(exchange,
                            new InsufficientAuthenticationException("Authentication required for this User-Agent"));
                });
    }
}
