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
package org.georchestra.gateway.autoconfigure.app;

import org.georchestra.gateway.filter.global.ApplicationErrorGatewayFilterFactory;
import org.georchestra.gateway.filter.global.MdcUserAndOrgGlobalFilter;
import org.georchestra.gateway.filter.global.LoginParamRedirectGatewayFilterFactory;
import org.georchestra.gateway.filter.global.ResolveTargetGlobalFilter;
import org.georchestra.gateway.filter.headers.HeaderFiltersConfiguration;
import org.georchestra.gateway.logging.mdc.config.AuthenticationMdcConfigProperties;
import org.georchestra.gateway.model.GatewayConfigProperties;
import org.georchestra.gateway.model.GeorchestraTargetConfig;
import org.geoserver.cloud.gateway.filter.GlobalUriFilter;
import org.geoserver.cloud.gateway.filter.RouteProfileGatewayFilterFactory;
import org.geoserver.cloud.gateway.filter.StripBasePathGatewayFilterFactory;
import org.geoserver.cloud.gateway.predicate.RegExpQueryRoutePredicateFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationEntryPoint;

import java.util.Optional;

/**
 * Auto-configuration for geOrchestra gateway filters and predicates.
 * <p>
 * This configuration registers custom filters and predicates that enhance
 * Spring Cloud Gateway's request handling capabilities. It ensures that
 * necessary filters are available before {@link GatewayAutoConfiguration} is
 * loaded.
 * </p>
 *
 * <p>
 * This class also imports {@link HeaderFiltersConfiguration} and enables
 * configuration properties via {@link GatewayConfigProperties}.
 * </p>
 *
 * @see GatewayAutoConfiguration
 * @see HeaderFiltersConfiguration
 * @see GatewayConfigProperties
 */
@AutoConfiguration
@AutoConfigureBefore(GatewayAutoConfiguration.class)
@Import(HeaderFiltersConfiguration.class)
@EnableConfigurationProperties({ GatewayConfigProperties.class, AuthenticationMdcConfigProperties.class })
public class FiltersAutoConfiguration {

    /**
     * Registers a {@link GlobalFilter} that resolves the target configuration for
     * each incoming request.
     * <p>
     * This filter extracts the matched route's {@link GeorchestraTargetConfig} and
     * saves it for later processing in the request-response lifecycle.
     * </p>
     *
     * @param config the gateway configuration properties
     * @return an instance of {@link ResolveTargetGlobalFilter}
     */
    @Bean
    ResolveTargetGlobalFilter resolveTargetWebFilter(GatewayConfigProperties config) {
        return new ResolveTargetGlobalFilter(config);
    }

    /**
     * Registers a {@link GlobalFilter} that adds user and org-related MDC (Mapping
     * Diagnostic Context) if respectively logging.mdc.include.user.id = true and
     * logging.mdc.include.user.org= true
     * <p>
     *
     * @param authConfig the logging auth configuration properties
     * @return an instance of {@link MdcUserAndOrgGlobalFilter}
     */
    @Bean
    MdcUserAndOrgGlobalFilter resolveGeorchestraUserMdcWebFilter(AuthenticationMdcConfigProperties authConfig) {
        return new MdcUserAndOrgGlobalFilter(authConfig);
    }

    /**
     * Registers a gateway filter factory that processes login-related query
     * parameters.
     *
     * @return an instance of {@link LoginParamRedirectGatewayFilterFactory}
     */
    @Bean
    LoginParamRedirectGatewayFilterFactory loginParamRedirectGatewayFilterFactory(
            Optional<RedirectServerAuthenticationEntryPoint> redirectServerAuthenticationEntryPoint) {
        LoginParamRedirectGatewayFilterFactory loginParamRedirectGatewayFilterFactory = new LoginParamRedirectGatewayFilterFactory();
        loginParamRedirectGatewayFilterFactory.setRedirectServerAuthenticationEntryPoint(
                redirectServerAuthenticationEntryPoint.orElse(new RedirectServerAuthenticationEntryPoint("/login")));
        return loginParamRedirectGatewayFilterFactory;
    }

    /**
     * Registers a {@link GlobalFilter} that prevents already percent-encoded
     * request URIs from being encoded again when proxied (e.g. {@code %27} becoming
     * {@code %2527}).
     *
     * @return an instance of {@link GlobalUriFilter}
     */
    @Bean
    GlobalUriFilter globalUriFilter() {
        return new GlobalUriFilter();
    }

    /**
     * Registers a custom route predicate factory that allows matching query
     * parameters based on regular expressions.
     *
     * @return an instance of {@link RegExpQueryRoutePredicateFactory}
     */
    @Bean
    RegExpQueryRoutePredicateFactory regExpQueryRoutePredicateFactory() {
        return new RegExpQueryRoutePredicateFactory();
    }

    /**
     * Registers a gateway filter factory that enables or disables routes based on
     * the active Spring profiles.
     *
     * @return an instance of {@link RouteProfileGatewayFilterFactory}
     */
    @Bean
    RouteProfileGatewayFilterFactory routeProfileGatewayFilterFactory() {
        return new RouteProfileGatewayFilterFactory();
    }

    /**
     * Registers a gateway filter factory that strips the base path from incoming
     * requests.
     *
     * @return an instance of {@link StripBasePathGatewayFilterFactory}
     */
    @Bean
    StripBasePathGatewayFilterFactory stripBasePathGatewayFilterFactory() {
        return new StripBasePathGatewayFilterFactory();
    }

    /**
     * Registers a gateway filter factory that handles application-level errors
     * gracefully.
     *
     * @return an instance of {@link ApplicationErrorGatewayFilterFactory}
     */
    @Bean
    ApplicationErrorGatewayFilterFactory applicationErrorGatewayFilterFactory() {
        return new ApplicationErrorGatewayFilterFactory();
    }
}
