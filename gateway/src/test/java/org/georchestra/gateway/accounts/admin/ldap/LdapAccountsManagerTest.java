package org.georchestra.gateway.accounts.admin.ldap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.georchestra.ds.DataServiceException;
import org.georchestra.ds.orgs.Org;
import org.georchestra.ds.orgs.OrgsDao;
import org.georchestra.ds.roles.RoleDao;
import org.georchestra.ds.users.Account;
import org.georchestra.ds.users.AccountDao;
import org.georchestra.ds.users.DuplicatedEmailException;
import org.georchestra.ds.users.DuplicatedUidException;
import org.georchestra.gateway.security.GeorchestraGatewaySecurityConfigProperties;
import org.georchestra.gateway.security.exceptions.DuplicatedEmailFoundException;
import org.georchestra.gateway.security.exceptions.DuplicatedUsernameFoundException;
import org.georchestra.gateway.security.ldap.extended.DemultiplexingUsersApi;
import org.georchestra.gateway.security.ldap.extended.ExtendedGeorchestraUser;
import org.georchestra.gateway.security.oauth2.OpenIdConnectCustomConfig;
import org.georchestra.security.model.GeorchestraUser;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.ldap.NameNotFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LdapAccountsManagerTest {

    public @Test void testEnsureRoleExist() throws DataServiceException {
        RoleDao roleDao = mock(RoleDao.class);
        when(roleDao.findByCommonName(anyString())).thenThrow(new NameNotFoundException("FAKE_ROLE"));

        LdapAccountsManager toTest = new LdapAccountsManager(mock(ApplicationEventPublisher.class), null, roleDao, null,
                null, null, null, Optional.empty());

        toTest.ensureRoleExists("FAKE_ROLE");
        // No exception thrown
    }

    @Test
    void ensureOrgExists_usesExistingLdapUidWhenOAuthUidDiffers() {
        OrgsDao orgsDao = mock(OrgsDao.class);
        AccountDao accountDao = mock(AccountDao.class);
        RoleDao roleDao = mock(RoleDao.class);
        DemultiplexingUsersApi usersApi = mock(DemultiplexingUsersApi.class);

        GeorchestraGatewaySecurityConfigProperties securityConfig = new GeorchestraGatewaySecurityConfigProperties();
        securityConfig.setModeratedSignup(false);
        securityConfig.setDefaultOrganization("");

        OpenIdConnectCustomConfig providersConfig = new OpenIdConnectCustomConfig();
        OpenIdConnectCustomConfig proconnectConfig = new OpenIdConnectCustomConfig();
        proconnectConfig.setSearchEmail(true);
        providersConfig.getProvider().put("proconnect", proconnectConfig);

        Org existingOrg = new Org();
        existingOrg.setId("ORG_B");
        existingOrg.setMembers(new ArrayList<>());
        when(orgsDao.findByOrgUniqueId("12345678901234")).thenReturn(existingOrg);
        when(orgsDao.findByCommonName("ORG_B")).thenReturn(existingOrg);
        when(orgsDao.findAll()).thenReturn(List.of(existingOrg));
        when(orgsDao.findByUser(any(Account.class))).thenReturn(existingOrg);

        GeorchestraUser existingUser = new GeorchestraUser();
        existingUser.setUsername("fake_uid");
        existingUser.setRoles(new ArrayList<>());
        ExtendedGeorchestraUser existingLdapUser = new ExtendedGeorchestraUser(existingUser);
        when(usersApi.findByEmail("user@example.org", false)).thenReturn(Optional.of(existingLdapUser));

        LdapAccountsManager toTest = new LdapAccountsManager(mock(ApplicationEventPublisher.class), accountDao, roleDao,
                orgsDao, usersApi, securityConfig, providersConfig, Optional.empty());

        GeorchestraUser mappedUser = new GeorchestraUser();
        mappedUser.setUsername("proconnect_uid");
        mappedUser.setEmail("user@example.org");
        mappedUser.setOrganization("ORG_B");
        mappedUser.setOAuth2Provider("proconnect");
        mappedUser.setOAuth2Uid("0226c002-a462-4127-887d-ae2b03633bd9");
        mappedUser.setOAuth2OrgId("12345678901234");
        mappedUser.setRoles(new ArrayList<>());

        toTest.ensureOrgExists(mappedUser);

        assertTrue(existingOrg.getMembers().contains("fake_uid"));
        assertFalse(existingOrg.getMembers().contains("proconnect_uid"));
        verify(orgsDao).update(existingOrg);
    }

    @Test
    void ensureOrgExists_usesExistingLdapUidWhenOAuthUidDiffers_withFakeSearchByEmail() {
        OrgsDao orgsDao = mock(OrgsDao.class);
        AccountDao accountDao = mock(AccountDao.class);
        RoleDao roleDao = mock(RoleDao.class);
        DemultiplexingUsersApi usersApi = mock(DemultiplexingUsersApi.class);

        GeorchestraGatewaySecurityConfigProperties securityConfig = new GeorchestraGatewaySecurityConfigProperties();
        securityConfig.setModeratedSignup(false);
        securityConfig.setDefaultOrganization("");

        OpenIdConnectCustomConfig providersConfig = new OpenIdConnectCustomConfig();
        OpenIdConnectCustomConfig fakeConfig = new OpenIdConnectCustomConfig();
        fakeConfig.setSearchEmail(true);
        providersConfig.getProvider().put("fake", fakeConfig);

        Org existingOrg = new Org();
        existingOrg.setId("ORG_A");
        existingOrg.setMembers(new ArrayList<>());
        when(orgsDao.findByOrgUniqueId("12345678901234")).thenReturn(existingOrg);
        when(orgsDao.findByCommonName("ORG_A")).thenReturn(existingOrg);
        when(orgsDao.findAll()).thenReturn(List.of(existingOrg));
        when(orgsDao.findByUser(any(Account.class))).thenReturn(existingOrg);

        GeorchestraUser existingUser = new GeorchestraUser();
        existingUser.setUsername("proconnect_uid");
        existingUser.setRoles(new ArrayList<>());
        ExtendedGeorchestraUser existingLdapUser = new ExtendedGeorchestraUser(existingUser);
        when(usersApi.findByEmail("user@example.org", false)).thenReturn(Optional.of(existingLdapUser));

        LdapAccountsManager toTest = new LdapAccountsManager(mock(ApplicationEventPublisher.class), accountDao, roleDao,
                orgsDao, usersApi, securityConfig, providersConfig, Optional.empty());

        GeorchestraUser mappedUser = new GeorchestraUser();
        mappedUser.setUsername("fake_uid");
        mappedUser.setEmail("user@example.org");
        mappedUser.setOrganization("ORG_A");
        mappedUser.setOAuth2Provider("fake");
        mappedUser.setOAuth2Uid("fake-external-id");
        mappedUser.setOAuth2OrgId("12345678901234");
        mappedUser.setRoles(new ArrayList<>());

        toTest.ensureOrgExists(mappedUser);

        assertTrue(existingOrg.getMembers().contains("proconnect_uid"));
        assertFalse(existingOrg.getMembers().contains("fake_uid"));
        verify(orgsDao).update(existingOrg);
    }

    // ===== findByOAuth2Uid =====

    @Test
    void findByOAuth2Uid_returnsEmptyWhenNotFound() {
        DemultiplexingUsersApi usersApi = mock(DemultiplexingUsersApi.class);
        when(usersApi.findByOAuth2Uid("google", "uid123")).thenReturn(Optional.empty());

        LdapAccountsManager toTest = new LdapAccountsManager(mock(ApplicationEventPublisher.class), null, null, null,
                usersApi, null, new OpenIdConnectCustomConfig(), Optional.empty());

        assertTrue(toTest.findByOAuth2Uid("google", "uid123").isEmpty());
    }

    @Test
    void findByOAuth2Uid_prefixesUnprefixedRoles() {
        DemultiplexingUsersApi usersApi = mock(DemultiplexingUsersApi.class);
        GeorchestraUser user = new GeorchestraUser();
        user.setRoles(new ArrayList<>(List.of("USER", "ROLE_ADMIN")));
        when(usersApi.findByOAuth2Uid("google", "uid123")).thenReturn(Optional.of(new ExtendedGeorchestraUser(user)));

        LdapAccountsManager toTest = new LdapAccountsManager(mock(ApplicationEventPublisher.class), null, null, null,
                usersApi, null, new OpenIdConnectCustomConfig(), Optional.empty());

        Optional<GeorchestraUser> result = toTest.findByOAuth2Uid("google", "uid123");

        assertTrue(result.isPresent());
        List<String> roles = result.get().getRoles();
        assertTrue(roles.contains("ROLE_USER"));
        assertTrue(roles.contains("ROLE_ADMIN"));
        assertFalse(roles.contains("USER"));
    }

    // ===== findByUsername =====

    @Test
    void findByUsername_returnsEmptyWhenNotFound() {
        DemultiplexingUsersApi usersApi = mock(DemultiplexingUsersApi.class);
        when(usersApi.findByUsername("testuser")).thenReturn(Optional.empty());

        LdapAccountsManager toTest = new LdapAccountsManager(mock(ApplicationEventPublisher.class), null, null, null,
                usersApi, null, new OpenIdConnectCustomConfig(), Optional.empty());

        assertTrue(toTest.findByUsername("testuser").isEmpty());
    }

    @Test
    void findByUsername_prefixesUnprefixedRoles() {
        DemultiplexingUsersApi usersApi = mock(DemultiplexingUsersApi.class);
        GeorchestraUser user = new GeorchestraUser();
        user.setRoles(new ArrayList<>(List.of("ADMIN")));
        when(usersApi.findByUsername("testuser")).thenReturn(Optional.of(new ExtendedGeorchestraUser(user)));

        LdapAccountsManager toTest = new LdapAccountsManager(mock(ApplicationEventPublisher.class), null, null, null,
                usersApi, null, new OpenIdConnectCustomConfig(), Optional.empty());

        Optional<GeorchestraUser> result = toTest.findByUsername("testuser");

        assertTrue(result.isPresent());
        assertTrue(result.get().getRoles().contains("ROLE_ADMIN"));
        assertFalse(result.get().getRoles().contains("ADMIN"));
    }

    // ===== findByEmail =====

    @Test
    void findByEmail_delegatesAndPrefixesRoles() {
        DemultiplexingUsersApi usersApi = mock(DemultiplexingUsersApi.class);
        GeorchestraUser user = new GeorchestraUser();
        user.setRoles(new ArrayList<>(List.of("USER")));
        when(usersApi.findByEmail("user@example.org", false))
                .thenReturn(Optional.of(new ExtendedGeorchestraUser(user)));

        LdapAccountsManager toTest = new LdapAccountsManager(mock(ApplicationEventPublisher.class), null, null, null,
                usersApi, null, new OpenIdConnectCustomConfig(), Optional.empty());

        Optional<GeorchestraUser> result = toTest.findByEmail("user@example.org", false);

        assertTrue(result.isPresent());
        assertTrue(result.get().getRoles().contains("ROLE_USER"));
        assertFalse(result.get().getRoles().contains("USER"));
    }

    @Test
    void findByEmail_withFilterPending_forwardsFlagToUsersApi() {
        DemultiplexingUsersApi usersApi = mock(DemultiplexingUsersApi.class);
        GeorchestraUser user = new GeorchestraUser();
        user.setRoles(new ArrayList<>());
        when(usersApi.findByEmail("user@example.org", true)).thenReturn(Optional.of(new ExtendedGeorchestraUser(user)));
        when(usersApi.findByEmail("user@example.org", false)).thenReturn(Optional.empty());

        LdapAccountsManager toTest = new LdapAccountsManager(mock(ApplicationEventPublisher.class), null, null, null,
                usersApi, null, new OpenIdConnectCustomConfig(), Optional.empty());

        assertTrue(toTest.findByEmail("user@example.org", false).isEmpty());
        assertTrue(toTest.findByEmail("user@example.org", true).isPresent());
    }

    // ===== createInternal =====

    @Test
    void createInternal_throwsDuplicatedEmailFoundExceptionOnDuplicatedEmail() throws Exception {
        AccountDao accountDao = mock(AccountDao.class);
        doThrow(new DuplicatedEmailException("duplicate email")).when(accountDao).insert(any());

        GeorchestraGatewaySecurityConfigProperties securityConfig = new GeorchestraGatewaySecurityConfigProperties();

        LdapAccountsManager toTest = new LdapAccountsManager(mock(ApplicationEventPublisher.class), accountDao, null,
                null, null, securityConfig, new OpenIdConnectCustomConfig(), Optional.empty());

        GeorchestraUser mapped = new GeorchestraUser();
        mapped.setUsername("testuser");
        mapped.setRoles(new ArrayList<>());

        assertThrows(DuplicatedEmailFoundException.class, () -> toTest.createInternal(mapped));
    }

    @Test
    void createInternal_throwsDuplicatedUsernameFoundExceptionOnDuplicatedUid() throws Exception {
        AccountDao accountDao = mock(AccountDao.class);
        doThrow(new DuplicatedUidException("duplicate uid")).when(accountDao).insert(any());

        GeorchestraGatewaySecurityConfigProperties securityConfig = new GeorchestraGatewaySecurityConfigProperties();

        LdapAccountsManager toTest = new LdapAccountsManager(mock(ApplicationEventPublisher.class), accountDao, null,
                null, null, securityConfig, new OpenIdConnectCustomConfig(), Optional.empty());

        GeorchestraUser mapped = new GeorchestraUser();
        mapped.setUsername("testuser");
        mapped.setRoles(new ArrayList<>());

        assertThrows(DuplicatedUsernameFoundException.class, () -> toTest.createInternal(mapped));
    }

    @Test
    void createInternal_throwsIllegalStateOnDataServiceException() throws Exception {
        AccountDao accountDao = mock(AccountDao.class);
        doThrow(new DataServiceException("db error")).when(accountDao).insert(any());

        GeorchestraGatewaySecurityConfigProperties securityConfig = new GeorchestraGatewaySecurityConfigProperties();

        LdapAccountsManager toTest = new LdapAccountsManager(mock(ApplicationEventPublisher.class), accountDao, null,
                null, null, securityConfig, new OpenIdConnectCustomConfig(), Optional.empty());

        GeorchestraUser mapped = new GeorchestraUser();
        mapped.setUsername("testuser");
        mapped.setRoles(new ArrayList<>());

        assertThrows(IllegalStateException.class, () -> toTest.createInternal(mapped));
    }

    @Test
    void createInternal_rollsBackAccountWhenOrgCreationFails() throws Exception {
        AccountDao accountDao = mock(AccountDao.class);
        OrgsDao orgsDao = mock(OrgsDao.class);
        when(orgsDao.findByCommonName("ORG_X")).thenThrow(new NameNotFoundException("not found"));
        doThrow(new RuntimeException("insert failed")).when(orgsDao).insert(any());

        GeorchestraGatewaySecurityConfigProperties securityConfig = new GeorchestraGatewaySecurityConfigProperties();

        LdapAccountsManager toTest = new LdapAccountsManager(mock(ApplicationEventPublisher.class), accountDao, null,
                orgsDao, null, securityConfig, new OpenIdConnectCustomConfig(), Optional.empty());

        GeorchestraUser mapped = new GeorchestraUser();
        mapped.setUsername("testuser");
        mapped.setOrganization("ORG_X");
        mapped.setRoles(new ArrayList<>());

        assertThrows(IllegalStateException.class, () -> toTest.createInternal(mapped));
        verify(accountDao).delete(any());
    }

    // ===== unlinkUserOrg =====

    @Test
    void unlinkUserOrg_callsUnlinkWhenUserHasOrg() {
        OrgsDao orgsDao = mock(OrgsDao.class);
        when(orgsDao.findAll()).thenReturn(List.of());

        GeorchestraGatewaySecurityConfigProperties securityConfig = new GeorchestraGatewaySecurityConfigProperties();

        LdapAccountsManager toTest = new LdapAccountsManager(mock(ApplicationEventPublisher.class), null, null, orgsDao,
                null, securityConfig, new OpenIdConnectCustomConfig(), Optional.empty());

        GeorchestraUser user = new GeorchestraUser();
        user.setUsername("testuser");
        user.setOrganization("ORG_X");
        user.setRoles(new ArrayList<>());

        toTest.unlinkUserOrg(user);

        verify(orgsDao).unlinkUser(any(Account.class));
    }

    @Test
    void unlinkUserOrg_doesNothingWhenUserHasNoOrg() {
        OrgsDao orgsDao = mock(OrgsDao.class);

        GeorchestraGatewaySecurityConfigProperties securityConfig = new GeorchestraGatewaySecurityConfigProperties();

        LdapAccountsManager toTest = new LdapAccountsManager(mock(ApplicationEventPublisher.class), null, null, orgsDao,
                null, securityConfig, new OpenIdConnectCustomConfig(), Optional.empty());

        GeorchestraUser user = new GeorchestraUser();
        user.setUsername("testuser");
        user.setOrganization(null);
        user.setRoles(new ArrayList<>());

        toTest.unlinkUserOrg(user);

        verify(orgsDao, never()).unlinkUser(any());
    }

    // ===== findOrg =====

    @Test
    void findOrg_returnsOrgWhenFound() {
        OrgsDao orgsDao = mock(OrgsDao.class);
        Org org = new Org();
        org.setId("ORG_X");
        when(orgsDao.findByCommonName("ORG_X")).thenReturn(org);

        LdapAccountsManager toTest = new LdapAccountsManager(mock(ApplicationEventPublisher.class), null, null, orgsDao,
                null, null, null, Optional.empty());

        Optional<Org> result = toTest.findOrg("ORG_X");

        assertTrue(result.isPresent());
        assertEquals("ORG_X", result.get().getId());
    }

    @Test
    void findOrg_returnsEmptyWhenNotFound() {
        OrgsDao orgsDao = mock(OrgsDao.class);
        when(orgsDao.findByCommonName("ORG_X")).thenThrow(new NameNotFoundException("not found"));

        LdapAccountsManager toTest = new LdapAccountsManager(mock(ApplicationEventPublisher.class), null, null, orgsDao,
                null, null, null, Optional.empty());

        assertTrue(toTest.findOrg("ORG_X").isEmpty());
    }

    // ===== findOrgById =====

    @Test
    void findOrgById_findsOrgByUniqueId() {
        OrgsDao orgsDao = mock(OrgsDao.class);
        Org org = new Org();
        org.setId("ORG_CN");
        when(orgsDao.findByOrgUniqueId("SIRET123")).thenReturn(org);
        when(orgsDao.findByCommonName("ORG_CN")).thenReturn(org);

        LdapAccountsManager toTest = new LdapAccountsManager(mock(ApplicationEventPublisher.class), null, null, orgsDao,
                null, null, null, Optional.empty());

        Optional<Org> result = toTest.findOrgById("ORG_CN", "SIRET123");

        assertTrue(result.isPresent());
        assertEquals("ORG_CN", result.get().getId());
    }

    @Test
    void findOrgById_fallsBackToOrgIdWhenUniqueIdIsEmpty() {
        OrgsDao orgsDao = mock(OrgsDao.class);
        Org org = new Org();
        org.setId("ORG_CN");
        when(orgsDao.findByCommonName("ORG_CN")).thenReturn(org);

        LdapAccountsManager toTest = new LdapAccountsManager(mock(ApplicationEventPublisher.class), null, null, orgsDao,
                null, null, null, Optional.empty());

        Optional<Org> result = toTest.findOrgById("ORG_CN", "");

        assertTrue(result.isPresent());
        assertEquals("ORG_CN", result.get().getId());
    }

    @Test
    void findOrgById_returnsEmptyWhenOrgNotFound() {
        OrgsDao orgsDao = mock(OrgsDao.class);
        when(orgsDao.findByCommonName("ORG_CN")).thenThrow(new NameNotFoundException("not found"));

        LdapAccountsManager toTest = new LdapAccountsManager(mock(ApplicationEventPublisher.class), null, null, orgsDao,
                null, null, null, Optional.empty());

        assertTrue(toTest.findOrgById("ORG_CN", "").isEmpty());
    }

    // ===== addAccountToOrg name override =====

    @Test
    void addAccountToOrg_updatesOrgNameWhenOverrideEnabled() {
        OrgsDao orgsDao = mock(OrgsDao.class);
        DemultiplexingUsersApi usersApi = mock(DemultiplexingUsersApi.class);
        GeorchestraGatewaySecurityConfigProperties securityConfig = new GeorchestraGatewaySecurityConfigProperties();

        OpenIdConnectCustomConfig providersConfig = new OpenIdConnectCustomConfig();
        OpenIdConnectCustomConfig providerConfig = new OpenIdConnectCustomConfig();
        providerConfig.setOverrideExistingOrgName(true);
        providerConfig.setOrgNameResolvers(List.of("identifier"));
        providersConfig.getProvider().put("myprovider", providerConfig);

        Org existingOrg = new Org();
        existingOrg.setId("ORG_ID");
        existingOrg.setName("Old Name");
        existingOrg.setMembers(new ArrayList<>());

        when(usersApi.findByOAuth2Uid("myprovider", "uid123")).thenReturn(Optional.empty());
        when(orgsDao.findByCommonName("ORG_ID")).thenReturn(existingOrg);
        when(orgsDao.findAll()).thenReturn(List.of(existingOrg));
        when(orgsDao.findByUser(any(Account.class))).thenReturn(existingOrg);

        LdapAccountsManager toTest = new LdapAccountsManager(mock(ApplicationEventPublisher.class), null, null, orgsDao,
                usersApi, securityConfig, providersConfig, Optional.empty());

        GeorchestraUser mappedUser = new GeorchestraUser();
        mappedUser.setUsername("testuser");
        mappedUser.setOrganization("ORG_ID");
        mappedUser.setOAuth2Provider("myprovider");
        mappedUser.setOAuth2Uid("uid123");
        mappedUser.setOAuth2OrgId("SIRET12345");
        mappedUser.setRoles(new ArrayList<>());

        toTest.ensureOrgExists(mappedUser);

        assertEquals("SIRET12345", existingOrg.getName());
        verify(orgsDao).update(existingOrg);
    }

    @Test
    void addAccountToOrg_doesNotUpdateOrgNameWhenOverrideDisabled() {
        OrgsDao orgsDao = mock(OrgsDao.class);
        DemultiplexingUsersApi usersApi = mock(DemultiplexingUsersApi.class);
        GeorchestraGatewaySecurityConfigProperties securityConfig = new GeorchestraGatewaySecurityConfigProperties();
        OpenIdConnectCustomConfig providersConfig = new OpenIdConnectCustomConfig();

        Org existingOrg = new Org();
        existingOrg.setId("ORG_ID");
        existingOrg.setName("Old Name");
        existingOrg.setMembers(new ArrayList<>());

        when(usersApi.findByOAuth2Uid("myprovider", "uid123")).thenReturn(Optional.empty());
        when(orgsDao.findByCommonName("ORG_ID")).thenReturn(existingOrg);
        when(orgsDao.findAll()).thenReturn(List.of(existingOrg));
        when(orgsDao.findByUser(any(Account.class))).thenReturn(existingOrg);

        LdapAccountsManager toTest = new LdapAccountsManager(mock(ApplicationEventPublisher.class), null, null, orgsDao,
                usersApi, securityConfig, providersConfig, Optional.empty());

        GeorchestraUser mappedUser = new GeorchestraUser();
        mappedUser.setUsername("testuser");
        mappedUser.setOrganization("ORG_ID");
        mappedUser.setOAuth2Provider("myprovider");
        mappedUser.setOAuth2Uid("uid123");
        mappedUser.setOAuth2OrgId("SIRET12345");
        mappedUser.setRoles(new ArrayList<>());

        toTest.ensureOrgExists(mappedUser);

        assertEquals("Old Name", existingOrg.getName());
        verify(orgsDao).update(existingOrg);
    }
}
