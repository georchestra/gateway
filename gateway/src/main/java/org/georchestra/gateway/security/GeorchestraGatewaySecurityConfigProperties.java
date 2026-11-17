/*
 * Copyright (C) 2022 by the geOrchestra PSC
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
package org.georchestra.gateway.security;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

import org.georchestra.gateway.security.ldap.LdapConfigBuilder;
import org.georchestra.gateway.security.ldap.LdapConfigPropertiesValidations;
import org.georchestra.gateway.security.ldap.basic.LdapServerConfig;
import org.georchestra.gateway.security.ldap.extended.ExtendedLdapConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import lombok.Data;
import lombok.Generated;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Configuration properties for geOrchestra Gateway security settings, typically
 * loaded from the geOrchestra data directory's {@literal default.properties}
 * file.
 * <p>
 * Example configuration:
 * </p>
 * 
 * <pre>
 * {@code 
 * ldapHost=localhost
 * ldapPort=389
 * ldapScheme=ldap
 * ldapBaseDn=dc=georchestra,dc=org
 * ldapUsersRdn=ou=users
 * ldapRolesRdn=ou=roles
 * ldapOrgsRdn=ou=orgs
 * }
 * </pre>
 */
@Data
@Generated
@Validated
@Accessors(chain = true)
@ConfigurationProperties(prefix = "georchestra.gateway.security")
public class GeorchestraGatewaySecurityConfigProperties implements Validator {
    /**
     * Flag indicating whether non-existing users should be created in LDAP
     * automatically.
     */
    private boolean createNonExistingUsersInLDAP = true;

    /**
     * Disable LDAP form login while keeping LDAP HTTP Basic authentication enabled.
     */
    private boolean disableLdapFormLogin = false;

    /**
     * User-Agent regular expressions requiring an HTTP Basic challenge for
     * unauthenticated requests, including requests to public services. Expressions
     * match the entire header value. Empty by default (disabled).
     */
    private List<String> httpBasicUserAgentPatterns = List.of();

    /** HTTP Basic session options. Disabled by default. */
    @Valid
    private HttpBasicSession httpBasicSession = new HttpBasicSession();

    @Data
    @Accessors(chain = true)
    public static class HttpBasicSession {
        private boolean enabled = false;
        private Duration reauthenticationInterval = Duration.ofHours(1);

        public HttpBasicSession setReauthenticationInterval(Duration interval) {
            if (interval == null || interval.isZero() || interval.isNegative()) {
                throw new IllegalArgumentException("httpBasicSession.reauthenticationInterval must be positive");
            }
            this.reauthenticationInterval = interval;
            return this;
        }
    }

    /** Fail explicitly rather than silently ignoring the former configuration key. */
    @Deprecated
    public void setHttpBasicSessionEnabled(boolean enabled) {
        throw new IllegalArgumentException("Replace httpBasicSessionEnabled with httpBasicSession.enabled");
    }

    /**
     * Flag indicating whether the user should be redirected to a page causing a
     * configureable delay after a successful login. Defaults to 0, which
     * deactivates the feature.
     */
    private int delayAfterLoginSeconds = 0;
    /**
     * Identifier of the Identity Provider for which UID transformation is disabled.
     * When this value matches an IDP's id, the username from that provider is used
     * as-is (no prefixing and no sanitization). If empty or not matching, UID
     * transformation is applied normally.
     */
    private String disableUidTransformation = "";

    /**
     * Default organization assigned to users when no specific organization is set.
     */
    private String defaultOrganization = "";

    private boolean moderatedSignup = false;

    public boolean isDelayAfterLogin() {
        return delayAfterLoginSeconds > 0;
    }

    /**
     * LDAP server configurations mapped by their respective names.
     */
    @Valid
    private Map<String, Server> ldap = Map.of();

    /**
     * Represents a configured LDAP server.
     */
    @Generated
    public static @Data class Server {

        /**
         * Indicates whether this LDAP configuration is enabled.
         */
        boolean enabled;

        /**
         * Whether the LDAP authentication source shall use geOrchestra-specific
         * extensions. For example, when using the default OpenLDAP database with
         * additional user identity information.
         */
        boolean extended;

        /**
         * URL of the LDAP server.
         */
        private String url;

        /**
         * Flag indicating if the LDAP authentication endpoint is an Active Directory
         * service.
         */
        private boolean activeDirectory;

        /**
         * Base Distinguished Name (DN) of the LDAP directory. This represents the root
         * suffix. Example: {@code dc=georchestra,dc=org}.
         */
        private String baseDn;

        /**
         * Configuration for extracting user information. When {@code activeDirectory}
         * is {@code true}, only {@code searchFilter} is used.
         */
        private Users users;

        /**
         * Configuration for extracting role information. This setting is unused for
         * Active Directory.
         */
        private Roles roles;

        /**
         * Configuration for extracting organization information. Used only for OpenLDAP
         * when {@code extended} is {@code true}.
         */
        private Organizations orgs;

        /**
         * Distinguished Name (DN) of the administrator user used for LDAP
         * authentication operations.
         */
        private String adminDn;

        /**
         * Password for the administrator user used for LDAP authentication operations.
         */
        private String adminPassword;
    }

    /**
     * Configuration for user-related LDAP attributes.
     */
    @Generated
    public static @Data @Accessors(chain = true) class Users {

        /**
         * Relative Distinguished Name (RDN) of the organizational unit containing
         * users. Example: If the full DN is {@code ou=users,dc=georchestra,dc=org}, the
         * RDN is {@code ou=users}.
         */
        private String rdn;

        /**
         * LDAP search filter to find users. Example: {@code (uid={0})} for OpenLDAP or
         * {@code (&(objectClass=user)(userPrincipalName={0}))} for Active Directory.
         */
        private String searchFilter;

        /**
         * Specifies the LDAP attributes to be returned in search results. {@code null}
         * indicates all attributes will be returned. An empty array means no attributes
         * will be returned.
         */
        private @Setter String[] returningAttributes;
    }

    /**
     * Configuration for role-related LDAP attributes.
     */
    @Generated
    public static @Data @Accessors(chain = true) class Roles {
        /**
         * Relative Distinguished Name (RDN) of the organizational unit containing
         * roles. Example: If the full DN is {@code ou=roles,dc=georchestra,dc=org}, the
         * RDN is {@code ou=roles}.
         */
        private String rdn;

        /**
         * LDAP search filter used to determine role membership. Example:
         * {@code (member={0})}.
         */
        private String searchFilter;
    }

    /**
     * Configuration for organization-related LDAP attributes.
     */
    @Generated
    public static @Data @Accessors(chain = true) class Organizations {

        /**
         * Relative Distinguished Name (RDN) of the organizational unit containing
         * organizations. Default value: {@code ou=orgs}.
         */
        private String rdn = "ou=orgs";

        /**
         * Relative Distinguished Name (RDN) of the organizational unit containing
         * pending organizations. Default value: {@code ou=pendingorgs}.
         */
        private String pendingRdn = "ou=pendingorgs";
    }

    /**
     * {@inheritDoc}
     */
    public @Override boolean supports(Class<?> clazz) {
        return GeorchestraGatewaySecurityConfigProperties.class.equals(clazz);
    }

    /**
     * Validates the LDAP configuration properties.
     * 
     * @param target the instance to validate
     * @param errors the validation errors
     */
    @Override
    public void validate(Object target, Errors errors) {
        GeorchestraGatewaySecurityConfigProperties config = (GeorchestraGatewaySecurityConfigProperties) target;
        Map<String, Server> ldapConfig = config.getLdap();
        if (ldapConfig == null || ldapConfig.isEmpty()) {
            return;
        }
        LdapConfigPropertiesValidations validations = new LdapConfigPropertiesValidations();
        ldapConfig.forEach((name, serverConfig) -> validations.validate(name, serverConfig, errors));
    }

    /**
     * Retrieves the list of enabled simple (non-extended) LDAP configurations.
     * 
     * @return a list of basic {@link LdapServerConfig} instances.
     */
    public List<LdapServerConfig> simpleEnabled() {
        LdapConfigBuilder builder = new LdapConfigBuilder();
        return entries().filter(e -> e.getValue().isEnabled()).filter(e -> !e.getValue().isExtended())
                .map(e -> builder.asBasicLdapConfig(e.getKey(), e.getValue())).toList();
    }

    /**
     * Retrieves the list of enabled extended LDAP configurations.
     * 
     * @return a list of {@link ExtendedLdapConfig} instances.
     */
    public List<ExtendedLdapConfig> extendedEnabled() {
        LdapConfigBuilder builder = new LdapConfigBuilder();
        return entries().filter(e -> e.getValue().isEnabled()).filter(e -> !e.getValue().isActiveDirectory())
                .filter(e -> e.getValue().isExtended()).map(e -> builder.asExtendedLdapConfig(e.getKey(), e.getValue()))
                .toList();
    }

    /**
     * Retrieves a stream of LDAP configuration entries.
     * 
     * @return a stream of LDAP server entries.
     */
    private Stream<Entry<String, Server>> entries() {
        return ldap == null ? Stream.empty() : ldap.entrySet().stream();
    }
}
