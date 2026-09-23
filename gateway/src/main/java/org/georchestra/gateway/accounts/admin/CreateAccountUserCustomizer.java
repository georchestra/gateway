/*
 * Copyright (C) 2023 by the geOrchestra PSC
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
package org.georchestra.gateway.accounts.admin;

import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;

import org.georchestra.ds.DataServiceException;
import org.georchestra.ds.users.Account;
import org.georchestra.ds.users.AccountDao;
import org.georchestra.gateway.security.GeorchestraUserCustomizerExtension;
import org.georchestra.gateway.security.exceptions.DuplicatedEmailFoundException;
import org.georchestra.gateway.security.exceptions.PendingUserException;
import org.georchestra.gateway.security.oauth2.OpenIdConnectCustomConfig;
import org.georchestra.security.model.GeorchestraUser;
import org.springframework.core.Ordered;
import org.springframework.ldap.NameNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * {@link GeorchestraUserCustomizerExtension} that
 * {@link AccountManager#getOrCreate creates an account} when authenticated
 * through request headers (trusted proxy feature) or through OAuth2.
 */
@RequiredArgsConstructor
public class CreateAccountUserCustomizer implements GeorchestraUserCustomizerExtension, Ordered {

    private final @NonNull AccountManager accounts;
    private final @NonNull AccountDao accountDao;
    private final @NonNull OpenIdConnectCustomConfig oidcCustomConfig;

    private final WeakHashMap<Authentication, GeorchestraUser> loggedInUsers = new WeakHashMap<>();

    /**
     * @return {@link Ordered#LOWEST_PRECEDENCE} so it runs after all other
     *         authentication customizations have been performed, such as setting
     *         additional roles from externalized configuration, etc.
     */
    public @Override int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    /**
     * @return the stored version (either existing or created as result of calling
     *         this method) of the user account, if the {@code Authentication}
     *         object is either an {@link OAuth2AuthenticationToken} or
     *         {@link PreAuthenticatedAuthenticationToken}; {@code mappedUser}
     *         otherwise.
     */
    @Override
    public @NonNull GeorchestraUser apply(@NonNull Authentication auth, @NonNull GeorchestraUser mappedUser)
            throws DuplicatedEmailFoundException {
        final boolean isPreAuth = auth instanceof PreAuthenticatedAuthenticationToken;
        final boolean isOauth2Authoritative = auth instanceof OAuth2AuthenticationToken && this.oidcCustomConfig
                .getProviderConfig(((OAuth2AuthenticationToken) auth).getAuthorizedClientRegistrationId())
                .map(OpenIdConnectCustomConfig::getAuthoritative).orElseGet(() -> false);
        final boolean isOauth2 = auth instanceof OAuth2AuthenticationToken && !isOauth2Authoritative;
        if (isOauth2 || isPreAuth) {
            GeorchestraUser user = applyInternal(auth, mappedUser, accounts::getOrCreate);
            if (isPendingAccount(user)) {
                throw new PendingUserException("User is pending approval.");
            }
            return user;
        }
        if (isOauth2Authoritative) {
            return applyInternal(auth, mappedUser, accounts::createOrUpdate);
        }
        return mappedUser;
    }

    @FunctionalInterface
    interface DelegateToAccountManager {
        GeorchestraUser delegate(GeorchestraUser user);
    }

    private GeorchestraUser applyInternal(@NonNull Authentication auth, @NonNull GeorchestraUser mappedUser,
            DelegateToAccountManager delegateToAccountManager) {
        Objects.requireNonNull(mappedUser.getUsername(), "GeorchestraUser.username is null");
        GeorchestraUser user = loggedInUsers.get(auth);
        if (user != null) {
            Optional<GeorchestraUser> ldapUser = accounts.find(mappedUser);
            if (ldapUser.isPresent()) {
                user = ldapUser.get();
            }
        } else {
            user = delegateToAccountManager.delegate(mappedUser);
        }
        user.setIsExternalAuth(true);
        loggedInUsers.put(auth, user);
        return user;
    }

    /**
     * Checks the pending status against the original LDAP account using
     * {@link AccountDao#findByUID(String)}.
     *
     * @param user the resolved user whose UID will be looked up
     * @return {@code true} if the stored account is marked pending
     * @throws IllegalStateException if the account cannot be retrieved
     */
    private boolean isPendingAccount(@NonNull GeorchestraUser user) {
        final String uid = Objects.requireNonNull(user.getUsername(), "GeorchestraUser.username is null");
        try {
            Account originalAccount = this.accountDao.findByUID(uid);
            return originalAccount.isPending();
        } catch (NameNotFoundException e) {
            throw new IllegalStateException("Account " + uid + " not found after login", e);
        } catch (DataServiceException e) {
            throw new IllegalStateException("Error retrieving account " + uid, e);
        }
    }
}
