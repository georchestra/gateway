package org.georchestra.gateway.security;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import org.georchestra.gateway.app.GeorchestraGatewayApplication;
import org.georchestra.gateway.model.GatewayConfigProperties;
import org.georchestra.gateway.model.HeaderMappings;
import org.georchestra.gateway.model.Service;
import org.georchestra.testcontainers.ldap.GeorchestraLdapContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getAllServeEvents;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = GeorchestraGatewayApplication.class)
@WireMockTest
@AutoConfigureWebTestClient(timeout = "PT200S")
@ActiveProfiles("customgeorheaders")
public class DefaultVsCustomGeorchestraHeadersConfigIT {
    @Container
    public static GeorchestraLdapContainer ldap = new GeorchestraLdapContainer();

    public static WireMockRuntimeInfo wmri;

    private @Autowired WebTestClient testClient;

    private @Autowired GatewayConfigProperties config;

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("ldapHost", ldap::getHost);
        registry.add("ldapPort", () -> ldap.getMappedPort(389));
        registry.add("ldapScheme", () -> "ldap");
    }

    @BeforeAll
    public static void setUp(WireMockRuntimeInfo wmri) {
        DefaultVsCustomGeorchestraHeadersConfigIT.wmri = wmri;
        System.setProperty("wmHost", "localhost");
        System.setProperty("wmPort", Integer.toString(wmri.getHttpPort()));
    }

    /**
     * This test checks that the base64-json versions of the http headers are sent
     * to proxified services, if specified in the configuration of the service. e.g.
     * if they are deactivated by default:
     *
     * <pre>
     * <code>
     * georchestra:
     *   gateway:
     *     default-headers:
     *       proxy: true
     *       username: true
     *       roles: true
     *       org: true
     *       orgname: true
     * </code>
     * </pre>
     * 
     * but activated at the service level, e.g.:
     *
     * <pre>
     * <code>
     * georchestra
     *   gateway:
     *     services:
     *       myservice: [...]
     *       headers:
     *         json-user: true
     *         json-organization: true
     * </code>
     * </pre>
     * 
     * Then the sec-user and sec-organization headers should be received by the
     * proxified webapp.
     *
     */
    @Test
    void testCustomizedHeadersForTarget() {
        // preflight, verify config
        Service serviceConfig = config.getServices().get("echo");
        assertThat(serviceConfig).isNotNull();
        assertThat(serviceConfig.headers()).isPresent();
        HeaderMappings mappings = serviceConfig.headers().orElseThrow();
        assertThat(mappings.getJsonUser()).isPresent().get().isEqualTo(true);
        assertThat(mappings.getJsonOrganization()).isPresent().get().isEqualTo(true);

        stubFor(get("/echo/").willReturn(ok()));

        testClient.get().uri("/echo/")//
                .header("Host", "localhost")//
                .header("Authorization", "Basic dGVzdGFkbWluOnRlc3RhZG1pbg==") // testadmin:testadmin
                .exchange();

        List<ServeEvent> events = getAllServeEvents();

        assertThat(events).hasSize(1);
        assertEquals(1, events.size());
        assertThat(events.getFirst().getRequest().getAllHeaderKeys()).contains("sec-user", "sec-organization");
    }
}
