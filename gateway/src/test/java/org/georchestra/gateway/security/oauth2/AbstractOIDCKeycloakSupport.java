package org.georchestra.gateway.security.oauth2;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import jakarta.ws.rs.core.Response;
import org.georchestra.ds.orgs.OrgsDao;
import org.georchestra.ds.roles.RoleDao;
import org.georchestra.ds.users.AccountDao;
import org.georchestra.gateway.app.GeorchestraGatewayApplication;
import org.georchestra.testcontainers.ldap.GeorchestraLdapContainer;
import org.jetbrains.annotations.NotNull;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = GeorchestraGatewayApplication.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@DirtiesContext
@AutoConfigureWebTestClient(timeout = "PT200S")
@ActiveProfiles("keycloak")
public abstract class AbstractOIDCKeycloakSupport {

    @Container
    static KeycloakContainer keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:26.4")
            .withRealmImportFile("/georchestra-oidc.json");

    @Container
    public static GeorchestraLdapContainer ldap = new GeorchestraLdapContainer();

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    protected AccountDao accountDao;

    @Autowired
    protected RoleDao roleDao;

    @Autowired
    protected OrgsDao orgsDao;

    @DynamicPropertySource
    static void customRolesClaimProcessor(DynamicPropertyRegistry registry) {
        registry.add("georchestra.gateway.security.oidc.claims.provider.keycloak.roles.json.path", () -> "$.groups");
        registry.add("georchestra.gateway.security.oidc.claims.provider.keycloak.roles.uppercase", () -> true);
        registry.add("georchestra.gateway.security.oidc.claims.provider.keycloak.roles.normalize", () -> true);
        registry.add("georchestra.gateway.security.oidc.claims.provider.keycloak.roles.splitcsv", () -> true);
        registry.add("georchestra.gateway.security.oidc.claims.provider.keycloak.organization.path",
                () -> "$.given_name");
        registry.add("georchestra.gateway.security.oidc.claims.provider.keycloak.organizationUid.path",
                () -> "$.given_name");
    }

    public static final String THREE_ROLES = "TEST_ROLE;Apps_Georchestra;Other_role";
    public static final String FOUR_ROLES = "TEST_ROLE;Apps_Georchestra;ROLE_USER;ROLE_PREFIX";
    public static final String GEOR_CLIENT_ID = "715ee18c-7dc3-4fa4-8c2e-8bacdbd9da25";

    // The previous webTestClient object will be scoped to our spring boot
    // application
    // and won't allow reaching other endpoints (e.g. our keycloak). Hence using a
    // dedicated webclient for such calls.
    private final WebTestClient oidcClient = WebTestClient.bindToServer().build();

    // Using a traditional random port won't allow to use it in the dynamic property
    // source
    // so we have to select the random port by ourselves.
    private static final int APP_PORT;

    // finds a free port on which spring could listen to.
    static {
        try (ServerSocket socket = new ServerSocket(0)) {
            APP_PORT = socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Failed to allocate an open port", e);
        }
    }

    @DynamicPropertySource
    static void keycloakProperties(DynamicPropertyRegistry registry) {

        registry.add("server.port", () -> APP_PORT);
        registry.add("spring.security.oauth2.client.registration.keycloak.client-id", () -> "georchestra-oidc");
        registry.add("spring.security.oauth2.client.registration.keycloak.client-secret",
                () -> "ouMohlei4Tuthi6paimahr2ieRohvogh");
        registry.add("spring.security.oauth2.client.provider.keycloak.issuer-uri",
                () -> keycloak.getAuthServerUrl() + "/realms/georchestra-oidc");
        registry.add("spring.security.oauth2.client.registration.keycloak.redirect-uri",
                () -> "http://localhost:" + APP_PORT + "/login/oauth2/code/keycloak");
        // the email scope is mandatory, without it we cannot create an account into the
        // LDAP.
        registry.add("spring.security.oauth2.client.registration.keycloak.scope", () -> "openid,profile,email,groups");

        registry.add("ldapHost", ldap::getHost);
        registry.add("ldapPort", () -> ldap.getMappedPort(389));
        registry.add("ldapScheme", () -> "ldap");
    }

    private String extractFormAction(String html) {
        // Regex to find: <form ... action="URL" ...>
        Pattern pattern = Pattern.compile("<form[^>]*action=\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1).replace("&amp;", "&");
        }
        throw new IllegalStateException("Could not find login form action in Keycloak HTML");
    }

    protected static UserRepresentation createTestUser(String userId, String orgSuffix, String roles) {
        RealmResource realm = keycloak.getKeycloakAdminClient().realm("georchestra-oidc");
        UserRepresentation testUser = createUserRepresentation(userId, orgSuffix);
        Response response = realm.users().create(testUser);
        response.close();

        RoleRepresentation rolesToAdd = realm.clients().get(GEOR_CLIENT_ID).roles().list().stream()
                .filter(x -> roles.equals(x.getName())).findFirst().get();
        UserResource userToComplete = realm.users().get(realm.users().searchByUsername(userId, true).get(0).getId());
        List<RoleRepresentation> toClear = userToComplete.roles().clientLevel(GEOR_CLIENT_ID).listEffective();
        userToComplete.roles().clientLevel(GEOR_CLIENT_ID).remove(toClear);
        userToComplete.roles().clientLevel(GEOR_CLIENT_ID).add(List.of(rolesToAdd));
        return testUser;
    }

    protected static UserRepresentation updateTestUser(String userId, String orgSuffix, String roles) {
        RealmResource realm = keycloak.getKeycloakAdminClient().realm("georchestra-oidc");
        UserRepresentation testUser = createUserRepresentation(userId, orgSuffix);
        UserResource userToUpdate = realm.users().get(realm.users().searchByUsername(userId, true).get(0).getId());
        userToUpdate.update(testUser);

        RoleRepresentation rolesToAdd = realm.clients().get(GEOR_CLIENT_ID).roles().list().stream()
                .filter(x -> roles.equals(x.getName())).findFirst().get();
        UserResource userToComplete = realm.users().get(realm.users().searchByUsername(userId, true).get(0).getId());
        List<RoleRepresentation> toClear = userToComplete.roles().clientLevel(GEOR_CLIENT_ID).listEffective();
        userToComplete.roles().clientLevel(GEOR_CLIENT_ID).remove(toClear);
        userToComplete.roles().clientLevel(GEOR_CLIENT_ID).add(List.of(rolesToAdd));
        return testUser;
    }

    protected void logAndFollowRedirect(String userId) {
        FluxExchangeResult<Void> springSecurityInitialRedirect = webTestClient.get()
                .uri("/oauth2/authorization/keycloak").exchange().expectStatus().is3xxRedirection()
                .returnResult(Void.class);
        URI springSecurityRedirect = springSecurityInitialRedirect.getResponseHeaders().getLocation();
        String cookie = springSecurityInitialRedirect.getResponseCookies().getFirst("SESSION").getValue();

        EntityExchangeResult<String> loginPageResult = oidcClient.get().uri(springSecurityRedirect).exchange()
                .expectStatus().isOk().expectBody(String.class).returnResult();
        String authSessionId = loginPageResult.getResponseCookies().getFirst("AUTH_SESSION_ID").getValue();
        String formActionUrl = extractFormAction(loginPageResult.getResponseBody());

        URI appCallbackUri = oidcClient.post().uri(formActionUrl).cookie("AUTH_SESSION_ID", authSessionId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("username", userId).with("password", userId).with("credentialId", ""))
                .exchange().expectStatus().is3xxRedirection().returnResult(Void.class).getResponseHeaders()
                .getLocation();
        // Ensure we are being redirected back to the Spring Boot application callback
        assertThat(appCallbackUri.getPath()).contains("/login/oauth2/code/");

        FluxExchangeResult<Void> finalCallbackResult = webTestClient.get().uri(appCallbackUri).cookie("SESSION", cookie)
                .exchange().expectStatus().is3xxRedirection().returnResult(Void.class);
        String sessionId = finalCallbackResult.getResponseCookies().getFirst("SESSION").getValue();

        String body = new String(webTestClient.get().uri("/whoami").cookie("SESSION", sessionId).exchange()
                .expectStatus().isOk().expectHeader().contentType(MediaType.APPLICATION_JSON).expectBody()
                .jsonPath("$.GeorchestraUser.username").isEqualTo("keycloak_" + userId).returnResult()
                .getResponseBody());
        assertThat(body.contains("\"groups\":\"TEST_ROLE")).isTrue();
    }

    protected String random() {
        return UUID.randomUUID().toString().substring(0, 6);
    }

    private static @NotNull UserRepresentation createUserRepresentation(String userId, String orgSuffix) {
        UserRepresentation testUser = new UserRepresentation();
        testUser.setUsername(userId);
        testUser.setEmail(String.format("psc+%s@georchestra.org", userId));
        testUser.setFirstName("when_test_use_given_name_as_org_" + orgSuffix);
        testUser.setLastName("user");
        testUser.setEnabled(true);
        CredentialRepresentation pwd = new CredentialRepresentation();
        pwd.setTemporary(false);
        pwd.setType(CredentialRepresentation.PASSWORD);
        pwd.setValue(userId);
        testUser.setCredentials(List.of(pwd));
        return testUser;
    }
}
