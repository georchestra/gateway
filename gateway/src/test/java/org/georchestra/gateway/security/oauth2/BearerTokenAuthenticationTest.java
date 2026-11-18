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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.georchestra.gateway.security.GeorchestraGatewaySecurityConfigProperties;
import org.georchestra.security.model.GeorchestraUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;

class BearerTokenAuthenticationTest {

    private Jwt jwt(String issuer, List<String> audience) {
        return Jwt.withTokenValue("token").header("alg", "RS256").issuer(issuer).audience(audience)
                .subject("0c6bb556-4ee8-46f2-892d-6116e262b489").claim("preferred_username", "testadmin")
                .claim("given_name", "Test").claim("family_name", "ADMIN").claim("scope", "openid profile")
                .claim("roles", List.of("ROLE_ADMINISTRATOR")).claim("organization", "PSC")
                .issuedAt(Instant.now().minusSeconds(10)).expiresAt(Instant.now().plusSeconds(300)).build();
    }

    @Test
    void convertsTheTokenLikeABrowserOidcLogin() {
        var converter = new BearerTokenAuthenticationConverter("keycloak", "preferred_username");

        var auth = (OAuth2AuthenticationToken) converter.convert(jwt("https://kc/realms/r", List.of("gw"))).block();

        assertThat(auth.getAuthorizedClientRegistrationId()).isEqualTo("keycloak");
        assertThat(auth.getPrincipal()).isInstanceOf(OidcUser.class);
        assertThat(auth.getName()).isEqualTo("testadmin");
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).contains("OIDC_USER",
                "SCOPE_openid", "SCOPE_profile");
    }

    @Test
    void fallsBackToTheSubjectWithoutTheUsernameClaim() {
        var converter = new BearerTokenAuthenticationConverter("keycloak", "missing_claim");

        var auth = converter.convert(jwt("https://kc/realms/r", List.of("gw"))).block();

        assertThat(auth.getName()).isEqualTo("0c6bb556-4ee8-46f2-892d-6116e262b489");
    }

    @Test
    void theOidcMapperResolvesTheGeorchestraUser() {
        var claimsConfig = new OpenIdConnectCustomClaimsConfigProperties();
        var roles = new OpenIdConnectCustomClaimsConfigProperties.RolesMapping();
        roles.getJson().getPath().add("$.roles");
        claimsConfig.setRoles(roles);
        claimsConfig.getOrganization().getPath().add("$.organization");
        var securityConfig = mock(GeorchestraGatewaySecurityConfigProperties.class);
        when(securityConfig.getDisableUidTransformation()).thenReturn("keycloak");
        var mapper = new OpenIdConnectUserMapper(claimsConfig, securityConfig);
        var auth = new BearerTokenAuthenticationConverter("keycloak", "preferred_username")
                .convert(jwt("https://kc/realms/r", List.of("gw"))).block();

        Optional<GeorchestraUser> user = mapper.resolve(auth);

        assertThat(user).isPresent();
        assertThat(user.get().getUsername()).isEqualTo("testadmin");
        assertThat(user.get().getOrganization()).isEqualTo("PSC");
        assertThat(user.get().getRoles()).contains("ROLE_ADMINISTRATOR");
    }

    @Test
    void validatesIssuerAndAudience() {
        var config = new BearerTokenConfigProperties();
        config.setIssuer("https://kc/realms/r");
        config.setAudiences(List.of("georchestra-gateway"));
        OAuth2TokenValidator<Jwt> validator = BearerTokenConfiguration.validator(config);

        assertThat(
                validator.validate(jwt("https://kc/realms/r", List.of("account", "georchestra-gateway"))).hasErrors())
                        .isFalse();
        assertThat(validator.validate(jwt("https://kc/realms/r", List.of("account"))).hasErrors()).isTrue();
        assertThat(validator.validate(jwt("https://other/realms/r", List.of("georchestra-gateway"))).hasErrors())
                .isTrue();
    }

    @Test
    void withoutIssuerNorAudienceOnlyTimestampsAreChecked() {
        OAuth2TokenValidator<Jwt> validator = BearerTokenConfiguration.validator(new BearerTokenConfigProperties());

        assertThat(validator.validate(jwt("https://any", List.of("any"))).hasErrors()).isFalse();
    }
}
