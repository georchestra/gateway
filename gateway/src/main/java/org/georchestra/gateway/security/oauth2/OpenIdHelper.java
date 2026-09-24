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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * geOrchestra.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.georchestra.gateway.security.oauth2;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.georchestra.security.model.GeorchestraUser;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Utility class for working with OpenID Connect and JWTs.
 * <p>
 * This class provides utility functions for decoding JSON Web Tokens (JWT) and
 * transforming client responses containing JWTs into JSON format. The methods
 * in this class help extract claims from JWTs and convert them into usable data
 * formats for further processing.
 * </p>
 * <ul>
 * <li>{@link #decodeJwt(String)} decodes a JWT and returns the claims as a
 * map.</li>
 * <li>{@link #transformJWTClientResponseToJSON()} is a response filter function
 * that intercepts client responses with a content type of
 * {@code application/jwt} and transforms them into JSON format, updating the
 * response's content type to {@code application/json}.</li>
 * </ul>
 */
@Slf4j(topic = "org.georchestra.gateway.security.oauth2")
public final class OpenIdHelper {

    /**
     * Utility function to decode JWT with Nimbus and read claims.
     * 
     * @param jwt
     * @return
     */
    public static Map<String, Object> decodeJwt(String jwt) {
        try {
            // Parse and decode the JWT using Nimbus
            JWT parsedJwt = JWTParser.parse(jwt);
            return parsedJwt.getJWTClaimsSet().getClaims();
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to decode JWT", e);
        }
    }

    /**
     * Filter function to transform JWT to JSON.
     * 
     * <p>
     * Spring does not support application/jwt response. This allow to intercept
     * lient response and transform body content from JWT to JSON. More details:
     * https://github.com/georchestra/gateway/issues/168
     * <p>
     * 
     * @return An {@link ExchangeFilterFunction} used by {@link WebClient}
     */
    // Spring does not support application/jwt response. This allow to intercept
    // client response and transform body content from JWT to JSON.
    // More details: https://github.com/georchestra/gateway/issues/168
    public static ExchangeFilterFunction transformJWTClientResponseToJSON() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            if (clientResponse.headers().contentType().isPresent()
                    && clientResponse.headers().contentType().get().toString().startsWith("application/jwt")) {
                return clientResponse.bodyToMono(String.class).flatMap(jwt -> {
                    try {
                        // Decode JWT to JSON
                        Map<String, Object> claims = decodeJwt(jwt);
                        // JSON to String
                        String json = new ObjectMapper().writeValueAsString(claims);

                        // Replace body by a valid JSON
                        return Mono.just(clientResponse.mutate()
                                .headers(httpHeaders -> httpHeaders.setContentType(MediaType.APPLICATION_JSON))
                                .body(Flux.just(
                                        new DefaultDataBufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8))))
                                .build());
                    } catch (Exception e) {
                        return Mono.error(new RuntimeException("Failed to decode JWT", e));
                    }
                });
            }
            return Mono.just(clientResponse);
        });
    }

}
