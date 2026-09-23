package org.georchestra.gateway.security.oauth2;

import org.georchestra.ds.DataServiceException;
import org.georchestra.ds.DuplicatedCommonNameException;
import org.georchestra.ds.orgs.Org;
import org.georchestra.ds.roles.Role;
import org.georchestra.ds.users.Account;
import org.georchestra.ds.users.DuplicatedEmailException;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.ldap.NameNotFoundException;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class OIDCKeycloakIT extends AbstractOIDCKeycloakSupport {

    @Test
    public void keycloakLoginCreateUserInLdapWhenUserUnknown() throws DataServiceException {
        String userId = "testoidcuser1";
        createTestUser(userId, random(), THREE_ROLES);

        logAndFollowRedirect(userId);

        Account account = accountDao.findByUID("keycloak_" + userId);
        assertNotNull(account, "Account should have been created in LDAP by CreateAccountUserCustomizer");
        Set<String> roles = roleDao.findAllForUser(account).stream().map(Role::getName).collect(Collectors.toSet());
        assertEquals(Set.of("TEST_ROLE", "APPS_GEORCHESTRA", "OTHER_ROLE", "USER", "OIDC_USER"), roles);
        Org org = orgsDao.findByCommonName(account.getOrg());
        assertNotNull(org, "Org should have been created in LDAP");
        assertThat(org.getMembers().contains(account.getUid())).isTrue();
    }

    @Test
    public void keycloakLoginLetUserUnmodifiedInLdapWhenUserExists()
            throws DataServiceException, DuplicatedEmailException {
        String userId = "testoidcuser2";
        createTestUser(userId, random(), THREE_ROLES);
        logAndFollowRedirect(userId);
        Account account = accountDao.findByUID("keycloak_" + userId);
        Account updatedAccount = accountDao.findByUID("keycloak_" + userId);
        updatedAccount.setGivenName("mo");
        accountDao.update(account, updatedAccount);

        logAndFollowRedirect(userId);

        assertThat(accountDao.findByUID("keycloak_" + userId).getGivenName().equals("mo")).isTrue();
    }

    @Test
    public void keycloakLoginLetUserUnmodifiedWithROLEPrefixedRole() throws DataServiceException {
        String userId = "testoidcuser3";
        UserRepresentation keycloakUser = createTestUser(userId, random(), FOUR_ROLES);
        String initialOrg = keycloakUser.getFirstName();
        logAndFollowRedirect(userId);

        keycloakUser = updateTestUser(userId, random(), THREE_ROLES);
        String updatedOrg = keycloakUser.getFirstName();
        logAndFollowRedirect(userId);

        Account account = accountDao.findByUID("keycloak_" + userId);
        Set<String> roles = roleDao.findAllForUser(account).stream().map(Role::getName).collect(Collectors.toSet());
        assertEquals(Set.of("TEST_ROLE", "APPS_GEORCHESTRA", "PREFIX", "USER", "OIDC_USER"), roles);
        Org org = orgsDao.findByCommonName(initialOrg);
        assertNotNull(org, "Org should have been created in LDAP");
        assertThat(org.getMembers().contains(account.getUid())).isTrue();
        assertThrows(NameNotFoundException.class, () -> orgsDao.findByCommonName(updatedOrg));
    }

}
