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
package org.georchestra.gateway.accounts.admin.ldap;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.StringUtils;
import org.georchestra.ds.DataServiceException;
import org.georchestra.ds.DuplicatedCommonNameException;
import org.georchestra.ds.orgs.Org;
import org.georchestra.ds.orgs.OrgsDao;
import org.georchestra.ds.roles.Role;
import org.georchestra.ds.roles.RoleDao;
import org.georchestra.ds.roles.RoleFactory;
import org.georchestra.ds.users.Account;
import org.georchestra.ds.users.AccountDao;
import org.georchestra.ds.users.AccountFactory;
import org.georchestra.ds.users.DuplicatedEmailException;
import org.georchestra.ds.users.DuplicatedUidException;
import org.georchestra.gateway.accounts.admin.AbstractAccountsManager;
import org.georchestra.gateway.accounts.admin.AccountManager;
import org.georchestra.gateway.security.GeorchestraGatewaySecurityConfigProperties;
import org.georchestra.gateway.security.exceptions.DuplicatedEmailFoundException;
import org.georchestra.gateway.security.exceptions.DuplicatedUsernameFoundException;
import org.georchestra.gateway.security.ldap.extended.DemultiplexingUsersApi;
import org.georchestra.gateway.security.oauth2.OpenIdConnectCustomConfig;
import org.georchestra.gateway.orgresolvers.OrganizationNameResolver;
import org.georchestra.gateway.orgresolvers.ResolvedOrganization;
import org.georchestra.security.model.GeorchestraUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.ldap.NameNotFoundException;

import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link AccountManager} that manages {@link GeorchestraUser}
 * accounts through an extended LDAP service.
 * <p>
 * This class provides methods for fetching, creating, and managing user
 * accounts stored in LDAP via an {@link AccountDao} and {@link RoleDao}. If a
 * user does not exist, it ensures the necessary roles and organizational
 * memberships are created.
 * </p>
 *
 * <p>
 * Role names are automatically prefixed with {@code "ROLE_"} if missing.
 * </p>
 *
 * @see AccountManager
 * @see AbstractAccountsManager
 * @see DemultiplexingUsersApi
 * @see AccountDao
 * @see RoleDao
 * @see OrgsDao
 */
@Slf4j(topic = "org.georchestra.gateway.accounts.admin.ldap")
class LdapAccountsManager extends AbstractAccountsManager {

    /** Configuration properties for security-related settings. */
    private final @NonNull GeorchestraGatewaySecurityConfigProperties georchestraGatewaySecurityConfigProperties;

    /** DAO for managing user accounts in LDAP. */
    private final @NonNull AccountDao accountDao;

    /** DAO for managing roles and permissions in LDAP. */
    private final @NonNull RoleDao roleDao;

    /** DAO for managing organizations in LDAP. */
    private final @NonNull OrgsDao orgsDao;

    /** API for resolving users based on OAuth2 credentials. */
    private final @NonNull DemultiplexingUsersApi demultiplexingUsersApi;
    private final @NonNull OpenIdConnectCustomConfig providersConfig;

    /** Optional organization name resolver. */
    private final Optional<OrganizationNameResolver> orgNameResolver;

    /**
     * Constructs an instance of {@code LdapAccountsManager}.
     *
     * @param eventPublisher                             the application event
     *                                                   publisher used for
     *                                                   publishing account-related
     *                                                   events
     * @param accountDao                                 the DAO responsible for
     *                                                   managing user accounts in
     *                                                   LDAP
     * @param roleDao                                    the DAO responsible for
     *                                                   managing roles in LDAP
     * @param orgsDao                                    the DAO responsible for
     *                                                   managing organizations in
     *                                                   LDAP
     * @param demultiplexingUsersApi                     the API used for resolving
     *                                                   users by OAuth2 credentials
     * @param georchestraGatewaySecurityConfigProperties configuration properties
     *                                                   for security settings
     * @param providersConfig                            the providers configuration
     * @param orgNameResolver                            optional organization name
     *                                                   resolver
     */
    public LdapAccountsManager(ApplicationEventPublisher eventPublisher, AccountDao accountDao, RoleDao roleDao,
            OrgsDao orgsDao, DemultiplexingUsersApi demultiplexingUsersApi,
            GeorchestraGatewaySecurityConfigProperties georchestraGatewaySecurityConfigProperties,
            OpenIdConnectCustomConfig providersConfig, Optional<OrganizationNameResolver> orgNameResolver) {
        super(eventPublisher, providersConfig);
        this.accountDao = accountDao;
        this.roleDao = roleDao;
        this.orgsDao = orgsDao;
        this.demultiplexingUsersApi = demultiplexingUsersApi;
        this.georchestraGatewaySecurityConfigProperties = georchestraGatewaySecurityConfigProperties;
        this.providersConfig = providersConfig;
        this.orgNameResolver = orgNameResolver;
    }

    /**
     * Retrieves a stored user based on their OAuth2 provider and unique identifier.
     * <p>
     * This method queries the {@link DemultiplexingUsersApi} for a user with the
     * given OAuth2 provider and unique identifier. If found, the user's roles are
     * normalized to ensure they are properly prefixed.
     * </p>
     *
     * @param oAuth2Provider the OAuth2 provider (e.g., Google, GitHub)
     * @param oAuth2Uid      the unique identifier assigned by the OAuth2 provider
     * @return an {@link Optional} containing the user if found, otherwise empty
     */
    @Override
    protected Optional<GeorchestraUser> findByOAuth2Uid(@NonNull String oAuth2Provider, @NonNull String oAuth2Uid) {
        return demultiplexingUsersApi.findByOAuth2Uid(oAuth2Provider, oAuth2Uid).map(this::ensureRolesPrefixed);
    }

    /**
     * Retrieves a stored user based on their username.
     * <p>
     * This method queries the {@link DemultiplexingUsersApi} for a user with the
     * given username. If found, the user's roles are normalized to ensure they are
     * properly prefixed.
     * </p>
     *
     * @param username the username to search for
     * @return an {@link Optional} containing the user if found, otherwise empty
     */
    @Override
    protected Optional<GeorchestraUser> findByUsername(@NonNull String username) {
        return demultiplexingUsersApi.findByUsername(username).map(this::ensureRolesPrefixed);
    }

    @Override
    protected Optional<GeorchestraUser> findByEmail(@NonNull String email, boolean filterPending) {
        return demultiplexingUsersApi.findByEmail(email, filterPending).map(this::ensureRolesPrefixed);
    }

    /**
     * Ensures all roles assigned to a user are prefixed with {@code "ROLE_"}.
     * <p>
     * If a role does not start with "ROLE_", this method adds the prefix. This
     * normalization ensures consistency when handling role-based access.
     * </p>
     *
     * @param user the user whose roles need to be normalized
     * @return the updated {@link GeorchestraUser} with properly formatted roles
     */
    private GeorchestraUser ensureRolesPrefixed(GeorchestraUser user) {
        List<String> roles = user.getRoles().stream().filter(Objects::nonNull)
                .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r).toList(); // Converts to an immutable list
        user.setRoles(new ArrayList<>(roles)); // Ensures mutability
        return user;
    }

    /**
     * Creates a new user in the LDAP repository if one does not already exist.
     * <p>
     * This method first attempts to insert the user into LDAP. If an error occurs
     * due to duplicate emails or usernames, appropriate exceptions are thrown.
     * </p>
     * <p>
     * If the user is successfully inserted, their organization is ensured to exist.
     * If an error occurs while managing the organization, the user account creation
     * is rolled back.
     * </p>
     * <p>
     * Finally, roles are assigned to the user to ensure correct access levels.
     * </p>
     *
     * @param mapped the user to create
     * @throws DuplicatedEmailFoundException    if a user with the same email
     *                                          already exists
     * @throws DuplicatedUsernameFoundException if a user with the same username
     *                                          already exists
     */
    @Override
    protected void createInternal(GeorchestraUser mapped) throws DuplicatedEmailFoundException {
        Account newAccount = mapToAccountBrief(mapped);
        try {
            accountDao.insert(newAccount);
        } catch (DataServiceException accountError) {
            throw new IllegalStateException(accountError);
        } catch (DuplicatedEmailException accountError) {
            throw new DuplicatedEmailFoundException(accountError.getMessage());
        } catch (DuplicatedUidException accountError) {
            throw new DuplicatedUsernameFoundException(accountError.getMessage());
        }

        try {
            ensureOrgExists(newAccount, mapped.getOAuth2Provider());
        } catch (IllegalStateException orgError) {
            log.error("Error when trying to create / update the organisation {}, reverting the account creation",
                    newAccount.getOrg(), orgError);
            rollbackAccount(newAccount);
            throw orgError;
        }

        ensureRolesExist(mapped, newAccount);
    }

    @Override
    protected void updateInternal(GeorchestraUser existing, GeorchestraUser mapped)
            throws DataServiceException, DuplicatedEmailException {
        Account existingAccount = mapToAccountBrief(existing);
        Account modifiedAccount = mapToAccountBrief(mapped);
        accountDao.update(existingAccount, modifiedAccount);

        List<String> existingRoles = roleDao.findAllForUser(existingAccount).stream().map(Role::getName).toList();
        Set<String> expectedRoles = Stream.concat(mapped.getRoles().stream(), Stream.of("USER"))
                .map(s -> s.replaceFirst("^ROLE_", "")).collect(Collectors.toSet());
        List<String> rolesToRemove = existingRoles.stream().filter(r -> !expectedRoles.contains(r)).toList();
        List<String> rolesToAdd = expectedRoles.stream().filter(r -> !existingRoles.contains(r)).toList();
        for (String role : rolesToAdd) {
            ensureRoleExists(role);
            roleDao.addUser(role, modifiedAccount);
        }
        for (String role : rolesToRemove) {
            roleDao.deleteUser(role, modifiedAccount);
        }
        if (modifiedAccount.getOAuth2OrgId() != null) {
            List<String> roles = roleDao.findAllForOrg(orgsDao.findByOrgUniqueId(modifiedAccount.getOAuth2OrgId()))
                    .stream().map(Role::getName).collect(Collectors.toList());
            roleDao.addUsersInRoles(roles, List.of(modifiedAccount));
        }
    }

    private void ensureRolesExist(GeorchestraUser mapped, Account newAccount) {
        try {// account created, add roles
            if (!mapped.getRoles().contains("ROLE_USER")) {
                roleDao.addUser("USER", newAccount);
            }
            if (newAccount.getOrg() != null) {
                List<String> roles = roleDao.findAllForOrg(orgsDao.findByCommonName(newAccount.getOrg())).stream()
                        .map(Role::getName).collect(Collectors.toList());
                roleDao.addUsersInRoles(roles, List.of(newAccount));
            }
            for (String role : mapped.getRoles()) {
                role = role.replaceFirst("^ROLE_", "");
                ensureRoleExists(role);
                roleDao.addUser(role, newAccount);
            }
        } catch (NameNotFoundException | DataServiceException roleError) {
            try {// roll-back account
                accountDao.delete(newAccount);
            } catch (NameNotFoundException | DataServiceException rollbackError) {
                log.warn("Error reverting user creation after roleDao update failure", rollbackError);
            }
            throw new IllegalStateException(roleError);
        }
    }

    /**
     * Ensures the role exists in LDAP.
     *
     * @param role The CN of the role LDAP-side as a string.
     * @throws DataServiceException
     */
    @VisibleForTesting
    void ensureRoleExists(String role) throws DataServiceException {
        try {
            roleDao.findByCommonName(role);
        } catch (NameNotFoundException notFound) {
            try {
                roleDao.insert(RoleFactory.create(role, null, false));
            } catch (DuplicatedCommonNameException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /**
     * Maps a {@link GeorchestraUser} to a brief {@link Account} representation for
     * LDAP storage.
     * <p>
     * This method extracts key user details such as username, email, and
     * organization, and constructs an {@link Account} object suitable for insertion
     * into LDAP. The generated account is marked as non-pending and assigned a
     * default organization if none is provided.
     * </p>
     *
     * @param preAuth the pre-authenticated {@link GeorchestraUser} containing user
     *                details
     * @return a newly created {@link Account} object with mapped attributes
     */
    private Account mapToAccountBrief(@NonNull GeorchestraUser preAuth) {
        return mapToAccountBrief(preAuth, preAuth.getUsername());
    }

    /**
     * Maps a {@link GeorchestraUser} to an {@link Account} using the provided uid.
     *
     * @param preAuth the source user
     * @param uid     LDAP uid to use for the mapped account
     * @return a newly created {@link Account} object with mapped attributes
     */
    private Account mapToAccountBrief(@NonNull GeorchestraUser preAuth, @Nullable String uid) {
        String username = uid;
        String email = preAuth.getEmail();
        String firstName = preAuth.getFirstName();
        String lastName = preAuth.getLastName();
        String org = preAuth.getOrganization();
        String password = null;
        String phone = "";
        String title = "";
        String description = "";
        final @Nullable String oAuth2Provider = preAuth.getOAuth2Provider();
        final @Nullable String oAuth2Uid = preAuth.getOAuth2Uid();
        final @Nullable String oAuth2OrgId = preAuth.getOAuth2OrgId();

        Account newAccount = AccountFactory.createBrief(username, password, firstName, lastName, email, phone, title,
                description, oAuth2Provider, oAuth2Uid);
        // if provider org id exists, we will use it as uniqueOrgId
        newAccount.setOAuth2OrgId(Optional.ofNullable(oAuth2OrgId).orElse(""));
        // use default.properties param or params from provider gateway's config
        boolean defaultModeratedSignup = this.georchestraGatewaySecurityConfigProperties.isModeratedSignup();
        boolean moderatedSignup = Optional.ofNullable(oAuth2Provider).filter(StringUtils::isNotBlank)
                .map(providersConfig::moderatedSignup).orElse(defaultModeratedSignup);
        newAccount.setPending(moderatedSignup);
        String defaultOrg = this.georchestraGatewaySecurityConfigProperties.getDefaultOrganization();
        if (StringUtils.isEmpty(org) && !StringUtils.isBlank(defaultOrg)) {
            newAccount.setOrg(defaultOrg);
        } else {
            newAccount.setOrg(org);
        }
        return newAccount;
    }

    /**
     * @throws IllegalStateException if the org can't be created/updated
     */
    @Override
    protected void unlinkUserOrg(@NonNull GeorchestraUser user) {
        if (user.getOrganization() != null) {
            Account newAccount = mapToAccountBrief(user);
            orgsDao.unlinkUser(newAccount);
        }
    }

    /**
     * @throws IllegalStateException if the org can't be created/updated
     */
    @Override
    protected void ensureOrgExists(@NonNull GeorchestraUser mappedUser) {
        // Reuse the persisted LDAP uid when the user is already known (e.g. lookup by
        // email).
        String effectiveUid = findInternal(mappedUser).map(GeorchestraUser::getUsername).filter(StringUtils::isNotBlank)
                .orElse(mappedUser.getUsername());
        Account newAccount = mapToAccountBrief(mappedUser, effectiveUid);
        ensureOrgExists(newAccount, mappedUser.getOAuth2Provider());
    }

    /**
     * Retrieve LDAP organization from org value
     *
     * @param oAuth2Provider the OAuth2 provider name (may be null for non-OAuth2
     *                       users)
     * @throws IllegalStateException if the org can't be created/updated
     */
    private void ensureOrgExists(@NonNull Account newAccount, @Nullable String oAuth2Provider) {
        final String orgId = newAccount.getOrg();
        String orgUniqueId = Optional.ofNullable(newAccount.getOAuth2OrgId()).orElse("");

        // search by orgUniqueId or CN
        if (!StringUtils.isEmpty(orgId)) {
            Optional<Org> existingOrg = StringUtils.isNotEmpty(orgUniqueId) ? findOrgById(orgId, orgUniqueId)
                    : findOrg(orgId);

            existingOrg.ifPresentOrElse(org -> addAccountToOrg(newAccount, org, oAuth2Provider),
                    () -> createOrgAndAddAccount(newAccount, orgId, orgUniqueId, oAuth2Provider));
        }
    }

    /**
     * Creates an organization and assigns the user to it. If an
     * {@link OrganizationNameResolver} is available and enabled for the provider,
     * the resolved name is used instead of the raw orgId.
     */
    private void createOrgAndAddAccount(Account newAccount, final String orgId, final String orgUniqueId,
            @Nullable String oAuth2Provider) {
        try {
            log.info("Org {} does not exist, trying to create it", orgId);
            Org org = newOrg(orgId, orgUniqueId, oAuth2Provider);
            org.getMembers().add(newAccount.getUid());
            orgsDao.insert(org);
        } catch (Exception orgError) {
            throw new IllegalStateException(orgError);
        }
    }

    /**
     * Adds an account to an existing organization. If
     * {@code overrideExistingOrgName} is enabled for the provider, the organization
     * name is updated from the resolver.
     */
    private void addAccountToOrg(Account newAccount, Org org, @Nullable String oAuth2Provider) {
        // org already in the LDAP, add the newly created account to it
        // The org may have been found by orgUniqueId (e.g. SIRET) while its CN differs
        // —
        // sync the account's org field to the actual CN so ensureRolesExist can look it
        // up.
        newAccount.setOrg(org.getId());
        org.getMembers().add(newAccount.getUid());

        // Optionally update existing org name if override is enabled
        if (oAuth2Provider != null && providersConfig.overrideExistingOrgName(oAuth2Provider)) {
            String orgUniqueId = Optional.ofNullable(newAccount.getOAuth2OrgId()).orElse("");
            String identifier = StringUtils.isNotEmpty(orgUniqueId) ? orgUniqueId : org.getId();
            resolveOrgNameFromChain(identifier, oAuth2Provider).ifPresent(resolved -> {
                log.info("Overriding existing org '{}' name from '{}' to '{}'", org.getId(), org.getName(),
                        resolved.name());
                org.setName(resolved.name());
                org.setShortName(resolved.shortName());
            });
        }

        orgsDao.update(org);
    }

    protected Optional<Org> findOrgById(String orgId, String orgUniqueId) {
        orgUniqueId = (orgUniqueId == null || orgUniqueId.isEmpty()) ? null : orgUniqueId;
        try {
            String cn = Optional.ofNullable(orgUniqueId)
                    .flatMap(id -> Optional.ofNullable(orgsDao.findByOrgUniqueId(id))).map(Org::getId).orElse(orgId);
            return findOrg(cn);
        } catch (NameNotFoundException e) {
            return Optional.empty();
        }
    }

    /**
     * Finds an organization by its identifier.
     *
     * @param orgId the identifier of the organization
     * @return an {@link Optional} containing the organization if found, otherwise
     *         empty
     */
    protected Optional<Org> findOrg(String orgId) {
        try {
            return Optional.of(orgsDao.findByCommonName(orgId));
        } catch (NameNotFoundException e) {
            return Optional.empty();
        }
    }

    /**
     * Rolls back user creation if an error occurs.
     *
     * @param newAccount the user account to remove from LDAP
     */
    private void rollbackAccount(Account newAccount) {
        try {// roll-back account
            accountDao.delete(newAccount);
        } catch (NameNotFoundException | DataServiceException rollbackError) {
            log.warn("Error reverting user creation after orgsDao update failure", rollbackError);
        }
    }

    /**
     * Factory method to create a new org with the given id. When an
     * {@link OrganizationNameResolver} is available and the provider has SIRENE API
     * enabled, the resolved name is used for the org name and shortName.
     *
     * @param orgId          organization ID to create (cn)
     * @param orgUniqueId    uniqueOrgId field value
     * @param oAuth2Provider the OAuth2 provider name (may be null)
     * @return {@link Org} created organization
     */
    private Org newOrg(final String orgId, final String orgUniqueId, @Nullable String oAuth2Provider) {
        Org org = new Org();
        org.setId(orgId);
        org.setOrgUniqueId(Optional.ofNullable(orgUniqueId).orElse(""));
        org.setOrgType("Other");

        // Resolve the org name via the fallback chain
        String identifier = StringUtils.isNotEmpty(orgUniqueId) ? orgUniqueId : orgId;
        Optional<ResolvedOrganization> resolved = (oAuth2Provider != null)
                ? resolveOrgNameFromChain(identifier, oAuth2Provider)
                : Optional.empty();

        if (resolved.isPresent()) {
            org.setName(resolved.get().name());
            org.setShortName(resolved.get().shortName());
        } else {
            org.setName(orgId);
            org.setShortName(orgId);
        }

        return org;
    }

    /**
     * Resolves an organization name by trying each entry in the configured
     * {@code orgNameResolvers} list for the given provider. The first non-empty
     * result wins.
     * <p>
     * Supported resolver types:
     * <ul>
     * <li>{@code sirene} — delegates to the registered
     * {@link OrganizationNameResolver}</li>
     * <li>{@code identifier} — returns the raw identifier (e.g. SIRET number) as
     * the org name</li>
     * <li>{@code <value>} — returns the literal value</li>
     * </ul>
     * </p>
     *
     * @param identifier     the organization identifier (e.g. SIRET number)
     * @param oAuth2Provider the OAuth2 provider name
     * @return an {@link Optional} containing the resolved organization, or empty
     */
    private Optional<ResolvedOrganization> resolveOrgNameFromChain(String identifier, @NonNull String oAuth2Provider) {
        List<String> resolvers = providersConfig.orgNameResolvers(oAuth2Provider);
        if (resolvers.isEmpty()) {
            return Optional.empty();
        }

        for (String resolverEntry : resolvers) {
            Optional<ResolvedOrganization> result = tryResolver(resolverEntry, identifier);
            if (result.isPresent()) {
                return result;
            }
        }

        log.debug("No organization name resolver returned a result for identifier '{}' (provider: {})", identifier,
                oAuth2Provider);
        return Optional.empty();
    }

    private Optional<ResolvedOrganization> tryResolver(String resolverEntry, String identifier) {
        if (orgNameResolver.isPresent()
                && orgNameResolver.get().getOrgNameResolverEntry().equalsIgnoreCase(resolverEntry)) {
            return orgNameResolver.flatMap(resolver -> resolver.resolve(identifier));
        }

        if ("identifier".equalsIgnoreCase(resolverEntry)) {
            if (StringUtils.isNotEmpty(identifier)) {
                String shortName = identifier.length() > 32 ? identifier.substring(0, 32) : identifier;
                return Optional.of(new ResolvedOrganization(identifier, shortName));
            }
            return Optional.empty();
        } else if (!resolverEntry.isEmpty()) {
            String shortName = resolverEntry.length() > 32 ? resolverEntry.substring(0, 32) : resolverEntry;
            return Optional.of(new ResolvedOrganization(resolverEntry, shortName));
        }

        log.warn("Unknown organization name resolver type: '{}'", resolverEntry);
        return Optional.empty();
    }
}
