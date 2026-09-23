package org.georchestra.gateway.accounts.admin;

import org.georchestra.ds.orgs.OrgsDao;
import org.georchestra.ds.users.AccountDao;
import org.georchestra.gateway.app.GeorchestraGatewayApplication;
import org.georchestra.testcontainers.ldap.GeorchestraLdapContainer;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ldap.NameNotFoundException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for {@link CreateAccountUserCustomizer}.
 */
@SpringBootTest(classes = GeorchestraGatewayApplication.class)
@AutoConfigureWebTestClient(timeout = "PT20S")
@ActiveProfiles("createaccount")
@Testcontainers(disabledWithoutDocker = true)
public class CreateAccountUserCustomizerIT {
    private @Autowired WebTestClient testClient;

    private @Autowired AccountDao accountDao;

    private @Autowired OrgsDao orgsDao;

    @Container
    public static GeorchestraLdapContainer ldap = new GeorchestraLdapContainer();

    @DynamicPropertySource
    static void registerLdap(DynamicPropertyRegistry registry) {
        registry.add("testcontainers.georchestra.ldap.host", ldap::getHost);
        registry.add("testcontainers.georchestra.ldap.port", ldap::getMappedLdapPort);
    }

    private static final Map<String, String> NOT_EXISTING_ACCOUNT_HEADERS = Map.of( //
            "sec-georchestra-preauthenticated", "true", //
            "preauth-username", "pmartin", //
            "preauth-email", "pierre.martin@example.org", //
            "preauth-firstname", "Pierre", //
            "preauth-lastname", "Martin", //
            "preauth-org", "C2C");

    private static final Map<String, String> ANOTHER_NOT_EXISTING_ACCOUNT_HEADERS = Map.of( //
            "sec-georchestra-preauthenticated", "true", //
            "preauth-username", "pmartin2", //
            "preauth-email", "pierre.martin2@example.org", //
            "preauth-firstname", "Pierre-Jean-Pierre", //
            "preauth-lastname", "Martin", //
            "preauth-org", "NEWORG", "preauth-provider", "georchestra", "preauth-provider-id", "georchestra12");

    private static final Map<String, String> ANOTHER_NOT_EXISTING_ACCOUNT_HEADERS_EXISTING_ORG = Map.of( //
            "sec-georchestra-preauthenticated", "true", //
            "preauth-username", "pmartin3", //
            "preauth-email", "pierre.martin3@example.org", //
            "preauth-firstname", "Pierre-Jean-Marie", //
            "preauth-lastname", "Martin", //
            "preauth-org", "PSC"); // PSC is an existing org in the default geOrchestra LDAP
    private static final Map<String, String> EXISTING_ADMIN_ACCOUNT_HEADERS = Map.of( //
            "sec-georchestra-preauthenticated", "true", //
            "preauth-username", "testadmin", //
            "preauth-email", "psc+testadmin@georchestra.org", //
            "preauth-firstname", "Admin", //
            "preauth-lastname", "Test", //
            "preauth-org", "GEORCHESTRA");

    private static final Map<String, String> NON_EXISTING_USER_WITH_ORG_EMPTY_HEADERS = Map.of( //
            "sec-georchestra-preauthenticated", "true", //
            "preauth-username", "jmflup", //
            "preauth-email", "jmflup@georchestra.org", //
            "preauth-firstname", "Jean-Marc", //
            "preauth-lastname", "Flup", //
            "preauth-org", "");

    private WebTestClient.RequestHeadersUriSpec<?> prepareWebTestClientHeaders(
            WebTestClient.RequestHeadersUriSpec<?> spec, Map<String, String> headers) {
        headers.forEach((k, v) -> {
            spec.header(k, v);
        });
        return spec;
    }

    @Test
    void testPreauthenticatedHeadersAccess() throws Exception {
        prepareWebTestClientHeaders(testClient.get(), NOT_EXISTING_ACCOUNT_HEADERS).uri("/whoami")//
                .exchange()//
                .expectStatus()//
                .is2xxSuccessful()//
                .expectBody()//
                .jsonPath("$.GeorchestraUser").isNotEmpty();

        // Make sure the account has been created
        assertNotNull(accountDao.findByUID("pmartin"));
    }

    @Test
    void testPreauthenticatedHeadersAccessCreateOrg() throws Exception {
        assertThrows(NameNotFoundException.class, () -> accountDao.findByUID("pmartin2"));
        prepareWebTestClientHeaders(testClient.get(), ANOTHER_NOT_EXISTING_ACCOUNT_HEADERS).uri("/whoami")//
                .exchange()//
                .expectStatus()//
                .is2xxSuccessful()//
                .expectBody()//
                .jsonPath("$.GeorchestraUser").isNotEmpty()//
                .jsonPath("$.GeorchestraUser.organization").isEqualTo("NEWORG")
                .jsonPath("$.GeorchestraUser.oauth2Provider").isEqualTo("georchestra")
                .jsonPath("$.GeorchestraUser.oauth2Uid").isEqualTo("georchestra12");

        // Make sure the account has been created
        assertNotNull(accountDao.findByUID("pmartin2"));
        // And the organization has been created as well
        assertNotNull(orgsDao.findByCommonName("NEWORG"));
    }

    @Test
    void testPreauthenticatedHeadersAccessUpdateOrg() throws Exception {
        assertThrows(NameNotFoundException.class, () -> accountDao.findByUID("pmartin3"));
        prepareWebTestClientHeaders(testClient.get(), ANOTHER_NOT_EXISTING_ACCOUNT_HEADERS_EXISTING_ORG)//
                .uri("/whoami")//
                .exchange()//
                .expectStatus()//
                .is2xxSuccessful()//
                .expectBody()//
                .jsonPath("$.GeorchestraUser").isNotEmpty()//
                .jsonPath("$.GeorchestraUser.organization").isEqualTo("PSC");

        // Make sure the account has been created
        assertNotNull(accountDao.findByUID("pmartin3"));
        // And the PSC organization contains our newly created user
        assertThat(orgsDao.findByCommonName("PSC").getMembers()).contains("pmartin3");
    }

    @Test
    void testPreauthenticatedHeadersAccessExistingAccount() throws Exception {
        // the account should already exist before issuing the request
        assertNotNull(accountDao.findByUID("testadmin"));
        prepareWebTestClientHeaders(testClient.get(), EXISTING_ADMIN_ACCOUNT_HEADERS).uri("/whoami")//
                .exchange()//
                .expectStatus()//
                .is2xxSuccessful()//
                .expectBody()//
                .jsonPath("$.GeorchestraUser").isNotEmpty()//
                .jsonPath("$.GeorchestraUser.roles").value(Matchers.hasItems("ROLE_ADMINISTRATOR", //
                        "ROLE_SUPERUSER", //
                        "ROLE_GN_ADMIN", //
                        "ROLE_IMPORT", //
                        "ROLE_USER", //
                        "ROLE_MAPSTORE_ADMIN", //
                        "ROLE_EMAILPROXY"));
    }

    @Test
    void testPreauthenticatedHeadersWithOrgNotNullButEmpty() {
        prepareWebTestClientHeaders(testClient.get(), NON_EXISTING_USER_WITH_ORG_EMPTY_HEADERS).uri("/whoami")//
                .exchange()//
                .expectStatus()//
                .is2xxSuccessful()//
                .expectBody()//
                .jsonPath("$.GeorchestraUser").isNotEmpty().jsonPath("$.GeorchestraUser.organization").isEqualTo(null);
    }
}
