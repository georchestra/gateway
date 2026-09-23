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
package org.georchestra.gateway.security.oauth2;

import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.georchestra.security.model.GeorchestraUser;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;

import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuration properties for extracting custom OpenID Connect (OIDC) claims.
 * <p>
 * This class allows configuring how user information such as ID, roles, and
 * organization details are extracted from an OAuth2/OIDC authentication token
 * using JSONPath expressions.
 * </p>
 */
@ConfigurationProperties(prefix = "georchestra.gateway.security.oidc.claims")
@Slf4j(topic = "org.georchestra.gateway.security.oauth2")
public @Data class OpenIdConnectCustomClaimsConfigProperties {

    private JsonPathExtractor id = new JsonPathExtractor();
    private RolesMapping roles = new RolesMapping();
    private JsonPathExtractor organization = new JsonPathExtractor();
    private JsonPathExtractor organizationUid = new JsonPathExtractor();
    private JsonPathExtractor familyName = new JsonPathExtractor();
    private JsonPathExtractor givenName = new JsonPathExtractor();
    private JsonPathExtractor email = new JsonPathExtractor();

    private Map<String, OpenIdConnectCustomClaimsConfigProperties> provider = new HashMap<>();

    /**
     * Retrieves the JSONPath extractor configuration for extracting the user ID.
     *
     * @return an {@link Optional} containing the {@link JsonPathExtractor} for ID
     *         extraction.
     */
    public Optional<JsonPathExtractor> id() {
        return Optional.ofNullable(id);
    }

    /**
     * Retrieves the configuration for role mapping.
     *
     * @return an {@link Optional} containing the {@link RolesMapping}
     *         configuration.
     */
    public Optional<RolesMapping> roles() {
        return Optional.ofNullable(roles);
    }

    /**
     * Retrieves the JSONPath extractor configuration for extracting the
     * organization.
     *
     * @return an {@link Optional} containing the {@link JsonPathExtractor} for
     *         organization extraction.
     */
    public Optional<JsonPathExtractor> organization() {
        return Optional.ofNullable(organization);
    }

    /**
     * Retrieves the JSONPath extractor configuration for extracting the
     * organization'field orgUniqueId (not UUID).
     *
     * @return an {@link Optional} containing the {@link JsonPathExtractor} for
     *         organizationUid extraction.
     */
    public Optional<JsonPathExtractor> organizationUid() {
        return Optional.ofNullable(organizationUid);
    }

    /**
     * Retrieves the JSONPath extractor configuration for extracting the family
     * name.
     *
     * @return an {@link Optional} containing the {@link JsonPathExtractor} for
     *         family name extraction.
     */
    public Optional<JsonPathExtractor> familyName() {
        return Optional.ofNullable(familyName);
    }

    /**
     * Retrieves the JSONPath extractor configuration for extracting the given name.
     *
     * @return an {@link Optional} containing the {@link JsonPathExtractor} for
     *         given name extraction.
     */
    public Optional<JsonPathExtractor> givenName() {
        return Optional.ofNullable(givenName);
    }

    /**
     * Retrieves the JSONPath extractor configuration for extracting the email.
     *
     * @return an {@link Optional} containing the {@link JsonPathExtractor} for
     *         email extraction.
     */
    public Optional<JsonPathExtractor> email() {
        return Optional.ofNullable(email);
    }

    /**
     * Extract a provider claims mapping.
     *
     * @return an {@link Optional} containing the
     *         {@link OpenIdConnectCustomClaimsConfigProperties} provider claims
     *         mapping.
     */
    public Optional<OpenIdConnectCustomClaimsConfigProperties> getProviderConfig(@NonNull String providerName) {
        return Optional.ofNullable(provider.get(providerName));
    }

    /**
     * Configuration for extracting roles from OIDC claims.
     * <p>
     * This class defines transformation rules for role extraction, including case
     * formatting, normalization, and whether extracted roles should replace or
     * append to existing ones.
     * </p>
     */
    @Accessors(chain = true)
    public static @Data class RolesMapping {

        private JsonPathExtractor json = new JsonPathExtractor();

        /**
         * Whether to return mapped role names in uppercase.
         */
        private boolean uppercase = true;

        /**
         * Whether to normalize role names by removing special characters and replacing
         * spaces with underscores.
         */
        private boolean normalize = true;

        /**
         * Whether to append the extracted roles to existing roles (true), or replace
         * them (false).
         */
        private boolean append = true;

        private boolean splitcsv = false;

        /**
         * A literal prefix to strip from each extracted role name, if present, before
         * applying uppercase/normalize transforms.
         */
        private String removePrefix;

        /**
         * Retrieves the JSONPath extractor for roles.
         *
         * @return an {@link Optional} containing the {@link JsonPathExtractor} for role
         *         extraction.
         */
        public Optional<JsonPathExtractor> json() {
            return Optional.ofNullable(json);
        }

        /**
         * Extracts and applies roles from the provided claims to the given user.
         *
         * @param claims The OIDC claims from which roles should be extracted.
         * @param target The {@link GeorchestraUser} to which the roles should be
         *               applied.
         */
        public void apply(Map<String, Object> claims, GeorchestraUser target) {
            json().ifPresent(oidcClaimsConfig -> {
                List<String> rawValues = oidcClaimsConfig.extract(claims);
                List<String> oidcRoles = rawValues.stream().map(this::applyTransforms).flatMap(List::stream).toList();
                // Ensure the resulting is mutable

                if (oidcRoles.isEmpty()) {
                    return;
                }
                if (!append) {
                    target.getRoles().clear();
                }
                target.getRoles().addAll(0, oidcRoles);
            });
        }

        /**
         * Applies configured transformations to a role value.
         *
         * @param value The original role value.
         * @return The transformed role value.
         */
        private List<String> applyTransforms(String value) {
            Function<String, String> removePrefixF = StringUtils.hasText(removePrefix) ? this::removePrefix
                    : Function.identity();
            Function<String, String> uppercaseF = isUppercase() ? String::toUpperCase : Function.identity();
            Function<String, String> normalizeF = isNormalize() ? this::normalize : Function.identity();
            Stream<String> valueAsStream = isSplitcsv() ? Stream.of(value.split(";")) : Stream.of(value);
            return valueAsStream.map(removePrefixF).map(uppercaseF).map(normalizeF).toList();
        }

        /**
         * Strips {@link #removePrefix} from the beginning of {@code value}, if present.
         *
         * @param value The role value.
         * @return The role value without its leading prefix.
         */
        private String removePrefix(@NonNull String value) {
            return value.startsWith(removePrefix) ? value.substring(removePrefix.length()) : value;
        }

        /**
         * Normalizes a role string by:
         * <ul>
         * <li>Applying Unicode Normalization (NFC form).</li>
         * <li>Removing diacritical marks (accents).</li>
         * <li>Replacing whitespace with underscores.</li>
         * <li>Removing special characters.</li>
         * </ul>
         *
         * @param value The original role string.
         * @return The normalized role string.
         */
        public String normalize(@NonNull String value) {
            // Apply Unicode Normalization (NFC: a + ◌̂ = â)
            String normalized = Normalizer.normalize(value, Form.NFC);

            // Remove diacritical marks
            normalized = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

            // Replace all whitespace with underscores
            normalized = normalized.replaceAll("\\s+", "_");

            // Remove remaining special characters
            return normalized.replaceAll("[^a-zA-Z0-9_]", "");
        }
    }

    /**
     * Extracts values from OIDC claims using JSONPath expressions.
     */
    @Accessors(chain = true)
    public static @Data class JsonPathExtractor {

        /**
         * List of JSONPath expressions to extract values from OIDC claims.
         * <p>
         * Example:
         * </p>
         * 
         * <pre>
         * {@code
         * [
         *     [
         *       {
         *         "name": "GDI FTTH Planer (extern)",
         *         "targetSystem": "gdiFibreAdmin",
         *         "parameter": []
         *       }
         *     ]
         * ]
         * }
         * </pre>
         * 
         * The JSONPath expression `$.groups_json[0][0].name` extracts the first group
         * name, while `$.groups_json..['name']` extracts all group names into a list.
         */
        private List<String> path = new ArrayList<>();

        private List<String> value = new ArrayList<>();

        public JsonPathExtractor() {
        }

        /**
         * Constructor to pass path param directly.
         *
         * @param path
         */
        public JsonPathExtractor(List<String> path) {
            this.path = path;
        }

        /**
         * Extracts values from the provided OIDC claims using the configured JSONPath
         * expressions.
         *
         * @param claims The OIDC claims map.
         * @return A list of extracted values.
         */
        public @NonNull List<String> extract(@NonNull Map<String, Object> claims) {
            List<String> result = this.path.stream().map(jsonPathExpression -> this.extract(jsonPathExpression, claims))
                    .flatMap(List::stream).toList();
            if (!result.isEmpty()) {
                return result;
            } else {
                return value;
            }
        }

        /**
         * Extracts values from the given claims using a single JSONPath expression.
         *
         * @param jsonPathExpression The JSONPath expression.
         * @param claims             The claims map.
         * @return A list of extracted values.
         */
        private List<String> extract(final String jsonPathExpression, Map<String, Object> claims) {
            if (!StringUtils.hasText(jsonPathExpression)) {
                return List.of();
            }

            // if we call claims.get(key) and the result is a JSON object,
            // the json api used is a shaded version of org.json at package
            // com.nimbusds.jose.shaded.json, we don't want to use that
            // since it's obviously internal to com.nimbusds.jose
            // JsonPath works fine with it though, as it's designed
            // to work on POJOS, JSONObject is a Map and JSONArray is a List so it's ok
            DocumentContext context = JsonPath.parse(claims);
            Object matched;

            try {
                matched = context.read(jsonPathExpression);
            } catch (PathNotFoundException e) {
                log.warn("JSONPath expression {} not found in claims", jsonPathExpression, e);
                return List.of();
            }

            if (matched == null) {
                log.warn("The JSONPath expression {} evaluates to null", jsonPathExpression);
                return List.of();
            }

            final List<?> list = (matched instanceof List<?> l) ? l : List.of(matched);

            return IntStream.range(0, list.size()).mapToObj(list::get).filter(Objects::nonNull)
                    .map(value -> validateValueIsString(jsonPathExpression, value)).toList();
        }

        /**
         * Ensures that extracted values are of type {@link String}.
         *
         * @param jsonPathExpression The JSONPath expression used.
         * @param value The extracted value.
         * @return The extracted value as a string.
         * @throws IllegalStateException If the extracted value is not a string.
         */
        private String validateValueIsString(final String jsonPathExpression, @NonNull Object value) {
            if (value instanceof String val) {
                return val;
            }
            throw new IllegalStateException("The JSONPath expression %s evaluates to %s instead of String. Value: %s".formatted(
                    jsonPathExpression, value.getClass().getCanonicalName(), value));
        }
    }
}