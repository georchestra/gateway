/*
 * Copyright (C) 2022 by the geOrchestra PSC
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
package org.georchestra.gateway.security.ldap.extended;

import java.io.Serial;
import java.util.Collection;

import org.georchestra.security.api.UsersApi;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * A specialized {@link Authentication} implementation for Georchestra that
 * associates authentication details with a specific LDAP configuration.
 * <p>
 * This class is designed for use with Georchestra-aware LDAP databases, such as
 * the default OpenLDAP schema, where {@link UsersApi} can be used to fetch
 * additional user identity information.
 * </p>
 * <p>
 * It acts as a wrapper around an existing {@link Authentication} instance,
 * ensuring that authentication context remains associated with the correct LDAP
 * configuration.
 * </p>
 */
@RequiredArgsConstructor
public class GeorchestraUserNamePasswordAuthenticationToken implements Authentication, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The name of the LDAP configuration associated with this authentication.
     */
    private final @NonNull @Getter String configName;

    /**
     * The original authentication instance being wrapped.
     */
    private final @NonNull Authentication orig;

    /**
     * Returns the name of the authenticated principal.
     *
     * @return the authenticated user's name
     */
    @Override
    public String getName() {
        return orig.getName();
    }

    /**
     * Returns the authorities granted to the authenticated user.
     *
     * @return a collection of granted authorities
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return orig.getAuthorities();
    }

    /**
     * Returns the credentials (e.g., password) used for authentication.
     * <p>
     * This method always returns {@code null} as passwords should not be stored or
     * exposed beyond the authentication process.
     * </p>
     *
     * @return {@code null} (credentials are not stored)
     */
    @Override
    public Object getCredentials() {
        return null;
    }

    /**
     * Erases credentials from the wrapped token before the authentication is
     * retained in a session. Returning null from getCredentials alone does not
     * remove credentials held inside orig.
     */
    @Override
    public void eraseCredentials() {
        if (orig instanceof CredentialsContainer credentials) {
            credentials.eraseCredentials();
        }
    }

    /**
     * Returns additional details about the authenticated request.
     *
     * @return the authentication details object
     */
    @Override
    public Object getDetails() {
        return orig.getDetails();
    }

    /**
     * Returns the authenticated principal, typically a user object or username.
     *
     * @return the authenticated principal
     */
    @Override
    public Object getPrincipal() {
        return orig.getPrincipal();
    }

    /**
     * Indicates whether the authentication is currently valid.
     *
     * @return {@code true} if authenticated, otherwise {@code false}
     */
    @Override
    public boolean isAuthenticated() {
        return orig.isAuthenticated();
    }

    /**
     * Sets the authentication status of the user.
     *
     * @param isAuthenticated {@code true} to mark as authenticated, {@code false}
     *                        otherwise
     * @throws IllegalArgumentException if setting authentication is not supported
     */
    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        orig.setAuthenticated(isAuthenticated);
    }
}
