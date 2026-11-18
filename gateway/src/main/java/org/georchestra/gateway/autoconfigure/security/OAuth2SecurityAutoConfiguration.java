/*
 * Copyright (C) 2021 by the geOrchestra PSC
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
package org.georchestra.gateway.autoconfigure.security;

import org.georchestra.gateway.security.oauth2.BearerTokenConfiguration;
import org.georchestra.gateway.security.oauth2.OAuth2Configuration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Auto-configuration for OAuth2-based authentication in geOrchestra.
 * <p>
 * This configuration conditionally enables or disables OAuth2 security based on
 * the property {@code georchestra.gateway.security.oauth2.enabled}.
 * </p>
 *
 * <p>
 * It imports either:
 * <ul>
 * <li>{@link Enabled} when OAuth2 is enabled.</li>
 * <li>{@link Disabled} when OAuth2 is disabled (default).</li>
 * </ul>
 * </p>
 *
 * @see OAuth2Configuration
 */
@AutoConfiguration
@Slf4j(topic = "org.georchestra.gateway.autoconfigure.security")
@Import({ OAuth2SecurityAutoConfiguration.Enabled.class, OAuth2SecurityAutoConfiguration.Disabled.class })
public class OAuth2SecurityAutoConfiguration {

    private static final String ENABLED_PROP = "georchestra.gateway.security.oauth2.enabled";

    /**
     * Configuration that enables OAuth2 security when explicitly enabled.
     * <p>
     * This configuration is loaded if
     * {@code georchestra.gateway.security.oauth2.enabled} is set to {@code true}.
     * </p>
     */
    @Import({ OAuth2Configuration.class, BearerTokenConfiguration.class })
    @ConditionalOnProperty(name = ENABLED_PROP, havingValue = "true", matchIfMissing = false)
    static @Configuration class Enabled {

        /**
         * Logs a message indicating that OAuth2 security is enabled.
         */
        @PostConstruct
        public void log() {
            log.info("georchestra OAuth2 security enabled");
        }
    }

    /**
     * Configuration that disables OAuth2 security by default.
     * <p>
     * This configuration is loaded if
     * {@code georchestra.gateway.security.oauth2.enabled} is set to {@code false}
     * or is missing.
     * </p>
     */
    @ConditionalOnProperty(name = ENABLED_PROP, havingValue = "false", matchIfMissing = true)
    static @Configuration class Disabled {

        /**
         * Logs a message indicating that OAuth2 security is disabled.
         */
        @PostConstruct
        public void log() {
            log.info("georchestra OAuth2 security disabled");
        }
    }
}
