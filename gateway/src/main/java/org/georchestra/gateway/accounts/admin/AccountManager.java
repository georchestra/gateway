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

import java.util.Optional;

import org.georchestra.ds.DataServiceException;
import org.georchestra.ds.users.DuplicatedEmailException;
import org.georchestra.gateway.security.GeorchestraUserMapper;
import org.georchestra.gateway.security.ResolveGeorchestraUserGlobalFilter;
import org.georchestra.security.model.GeorchestraUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;

/**
 * Manages the retrieval and creation of stored {@link GeorchestraUser
 * Georchestra users}.
 * <p>
 * This interface provides methods to look up an existing user or create a new
 * one if it does not exist. Implementations of this interface should ensure
 * that user accounts are correctly managed within the system and that necessary
 * events are published when a new user is created.
 * </p>
 *
 * @see CreateAccountUserCustomizer
 * @see ResolveGeorchestraUserGlobalFilter
 */
public interface AccountManager {

    /**
     * Retrieves the stored user corresponding to the given {@code mappedUser}, if
     * it exists.
     *
     * @param mappedUser the user resolved by
     *                   {@link ResolveGeorchestraUserGlobalFilter}, obtained
     *                   through a call to
     *                   {@link GeorchestraUserMapper#resolve(Authentication)}
     * @return an {@link Optional} containing the stored version of the user if
     *         found; otherwise, an empty {@link Optional}
     */
    Optional<GeorchestraUser> find(GeorchestraUser mappedUser);

    /**
     * Retrieves the stored user corresponding to the given {@code mappedUser}, or
     * creates a new user if one does not already exist in the repository.
     * <p>
     * If a new user is created, an {@link AccountCreated} event must be published
     * via the {@link ApplicationEventPublisher} to notify the system of the new
     * account.
     * </p>
     *
     * @param mappedUser the user resolved by
     *                   {@link ResolveGeorchestraUserGlobalFilter}, obtained
     *                   through a call to
     *                   {@link GeorchestraUserMapper#resolve(Authentication)}
     * @return the stored version of the user, either previously existing or newly
     *         created
     */
    GeorchestraUser getOrCreate(GeorchestraUser mappedUser);

    GeorchestraUser createOrUpdate(GeorchestraUser mappedUser);

    /**
     * Retrieves the stored organization corresponding to the given
     * {@code mappedUser}, or creates a new organization if one does not already
     * exist in the repository.
     * <p>
     * </p>
     *
     * @param mappedUser the user resolved by
     *                   {@link ResolveGeorchestraUserGlobalFilter}, obtained
     *                   through a call to
     *                   {@link GeorchestraUserMapper#resolve(Authentication)}
     * @return the stored version of the user, either previously existing or newly
     *         created
     */
    void createUserOrgUniqueIdIfMissing(GeorchestraUser mappedUser);

}
