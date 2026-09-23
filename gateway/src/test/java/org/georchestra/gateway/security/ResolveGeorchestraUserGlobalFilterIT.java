package org.georchestra.gateway.security;

import org.georchestra.gateway.accounts.admin.CreateAccountUserCustomizer;
import org.georchestra.gateway.app.GeorchestraGatewayApplication;
import org.georchestra.gateway.filter.headers.providers.JsonPayloadHeadersContributor;
import org.georchestra.gateway.model.GatewayConfigProperties;
import org.georchestra.gateway.security.preauth.PreauthAuthenticationManager;
import org.georchestra.testcontainers.ldap.GeorchestraLdapContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = GeorchestraGatewayApplication.class)
@AutoConfigureWebTestClient(timeout = "PT200S")
@ActiveProfiles("georheaders")
@Testcontainers(disabledWithoutDocker = true)
public class ResolveGeorchestraUserGlobalFilterIT {

    private @Autowired WebTestClient testClient;

    private @Autowired GatewayConfigProperties gatewayConfig;

    private @Autowired ApplicationContext context;

    @Container
    public static GeorchestraLdapContainer ldap = new GeorchestraLdapContainer();

    @Container
    public static GenericContainer<?> httpEcho = new GenericContainer(DockerImageName.parse("ealen/echo-server"))
            .withExposedPorts(80);

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("ldapHost", ldap::getHost);
        registry.add("ldapPort", ldap::getMappedLdapPort);
        registry.add("httpEchoHost", httpEcho::getHost);
        registry.add("httpEchoPort", () -> httpEcho.getMappedPort(80));
    }

    @Test
    void testReceivedHeadersAsJson() {
        gatewayConfig.getDefaultHeaders().setJsonUser(Optional.of(true));
        gatewayConfig.getDefaultHeaders().setJsonOrganization(Optional.of(true));
        assertNotNull(context.getBean(JsonPayloadHeadersContributor.class));

        testClient.get().uri("/echo/")//
                .header("Host", "localhost")//
                .header("Authorization", "Basic dGVzdGFkbWluOnRlc3RhZG1pbg==") // testadmin:testadmin
                .exchange()//
                .expectStatus()//
                .is2xxSuccessful()//
                .expectBody()//
                .jsonPath(".request.headers.sec-user").exists().jsonPath(".request.headers.sec-organization").exists();
    }

    @Test
    void testJsonUserNoOrganization() {
        gatewayConfig.getDefaultHeaders().setJsonUser(Optional.of(true));
        gatewayConfig.getDefaultHeaders().setJsonOrganization(Optional.of(false));

        testClient.get().uri("/echo/")//
                .header("Host", "localhost")//
                .header("Authorization", "Basic dGVzdGFkbWluOnRlc3RhZG1pbg==") // testadmin:testadmin
                .exchange()//
                .expectStatus()//
                .is2xxSuccessful()//
                .expectBody()//
                .jsonPath(".request.headers.sec-user").exists()//
                .jsonPath(".request.headers.sec-organization").doesNotHaveJsonPath();

    }

    @Test
    void testNoJsonUserJsonOrganization() {
        gatewayConfig.getDefaultHeaders().setJsonUser(Optional.of(false));
        gatewayConfig.getDefaultHeaders().setJsonOrganization(Optional.of(true));

        testClient.get().uri("/echo/")//
                .header("Host", "localhost")//
                .header("Authorization", "Basic dGVzdGFkbWluOnRlc3RhZG1pbg==") // testadmin:testadmin
                .exchange()//
                .expectStatus()//
                .is2xxSuccessful()//
                .expectBody()//
                .jsonPath(".request.headers.sec-user").doesNotHaveJsonPath()//
                .jsonPath(".request.headers.sec-organization").exists();
    }

    @Test
    void testSecOrgnamePresent() {
        testClient.get().uri("/echo/")//
                .header("Host", "localhost")//
                .header("Authorization", "Basic dGVzdGFkbWluOnRlc3RhZG1pbg==") // testadmin:testadmin
                .exchange()//
                .expectStatus()//
                .is2xxSuccessful()//
                .expectBody()//
                .jsonPath(".request.headers.sec-orgname").exists();
    }

    /**
     * Show error message to OAuth2 user when a matching local account already
     * exists: i.e. it tries to create a user with an email address for which a user
     * already exists.
     * <p>
     * {@link GeorchestraUserMapper} calls the
     * {@link GeorchestraUserCustomizerExtension}s.
     * {@link CreateAccountUserCustomizer} will try to create an account with email
     * {@literal psc+testadmin@georchestra.org}, which already exists (for user
     * {@literal testadmin})
     */
    @Test
    void testRedirectIfOauth2UserExists() {
        final String email = "psc+testadmin@georchestra.org";
        final String expected = "/login?error=duplicate_account";

        testClient.get().uri("/echo/")//
                .header(PreauthAuthenticationManager.PREAUTH_HEADER_NAME, "true")
                .header(PreauthAuthenticationManager.PREAUTH_EMAIL, email)
                .header(PreauthAuthenticationManager.PREAUTH_FIRSTNAME, "bob")
                .header(PreauthAuthenticationManager.PREAUTH_LASTNAME, "sponge")
                .header(PreauthAuthenticationManager.PREAUTH_USERNAME, "bobsponge").accept(MediaType.APPLICATION_JSON)
                .exchange()//
                .expectStatus()//
                .is3xxRedirection().expectHeader().location(expected);
    }

}
