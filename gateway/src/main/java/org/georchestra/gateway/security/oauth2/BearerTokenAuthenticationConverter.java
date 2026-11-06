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
import java.util.Collection;
import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * Converts a validated bearer access token into the same authentication as a
 * browser OpenID Connect login with the client registration
 * {@link #registrationId}: an {@link OAuth2AuthenticationToken} whose principal
 * is an OIDC user built from the token claims.
 * <p>
 * The rest of the gateway (user mappers, account handling, headers sent to the
 * backends) then treats API calls and browser sessions alike.
 */
@RequiredArgsConstructor
public class BearerTokenAuthenticationConverter implements Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    private final @NonNull String registrationId;

    /** Claim holding the username, the provider's {@code user-name-attribute} */
    private final @NonNull String userNameAttribute;

    private final JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

    @Override
    public Mono<AbstractAuthenticationToken> convert(@NonNull Jwt jwt) {
        OidcIdToken token = new OidcIdToken(jwt.getTokenValue(), jwt.getIssuedAt(), jwt.getExpiresAt(),
                jwt.getClaims());

        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new OidcUserAuthority(token));
        Collection<GrantedAuthority> scopeAuthorities = scopes.convert(jwt);
        if (scopeAuthorities != null) {
            authorities.addAll(scopeAuthorities);
        }

        String nameAttribute = jwt.hasClaim(userNameAttribute) ? userNameAttribute : "sub";
        DefaultOidcUser user = new DefaultOidcUser(authorities, token, nameAttribute);
        return Mono.just(new OAuth2AuthenticationToken(user, authorities, registrationId));
    }
}
