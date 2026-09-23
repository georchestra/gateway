package org.georchestra.gateway.autoconfigure.accounts;

import org.georchestra.gateway.accounts.admin.CreateAccountUserCustomizer;
import org.georchestra.gateway.app.GeorchestraGatewayApplication;
import org.georchestra.gateway.security.ldap.extended.ExtendedLdapConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@SpringBootTest(classes = GeorchestraGatewayApplication.class)
@AutoConfigureWebTestClient(timeout = "PT200S")
@ActiveProfiles({ "createaccount" })
class ConditionalOnCreateLdapAccountsTest {

    @DynamicPropertySource
    static void activateCreateAccountsInLdap(DynamicPropertyRegistry registry) {
        registry.add("testcontainers.georchestra.ldap.host", () -> "localhost");
        registry.add("testcontainers.georchestra.ldap.port", () -> 389);
    }

    @Autowired
    List<ExtendedLdapConfig> configs;

    @Autowired
    CreateAccountUserCustomizer createAccountUserCustomizer;

    @Test
    void testBeansPresence() {
        assertThat(configs).isNotEmpty();
        assertThat(createAccountUserCustomizer).isNotNull();

    }
}
