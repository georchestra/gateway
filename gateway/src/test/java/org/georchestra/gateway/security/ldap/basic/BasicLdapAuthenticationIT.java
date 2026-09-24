package org.georchestra.gateway.security.ldap.basic;

import org.georchestra.gateway.app.GeorchestraGatewayApplication;
import org.georchestra.testcontainers.ldap.GeorchestraLdapContainer;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = GeorchestraGatewayApplication.class)
@ActiveProfiles({ "basicldap" })
@AutoConfigureWebTestClient(timeout = "PT20S")
@Disabled("ExtendedLdapAuthenticationProvider being built instead of a Basic one after https://github.com/georchestra/gateway/pull/50/files ?")
@Testcontainers(disabledWithoutDocker = true)
public class BasicLdapAuthenticationIT {

    @Container
    public static GeorchestraLdapContainer ldap = new GeorchestraLdapContainer();

    private @Autowired WebTestClient testClient;

    @Test
    void testWhoamiNoPasswordRevealed() {
        testClient.get().uri("/whoami")//
                .header("Authorization", "Basic dGVzdGFkbWluOnRlc3RhZG1pbg==") // testadmin:testadmin
                .exchange()//
                .expectStatus()//
                .is2xxSuccessful()//
                .expectBody(String.class)//
                .value(Matchers.not(Matchers.containsString("{SHA}")));
    }

}
