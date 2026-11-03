/*
 * Copyright (C) 2026 by the geOrchestra PSC
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
package org.georchestra.gateway.session.redis;

import java.time.Duration;

import org.georchestra.gateway.security.ldap.extended.GeorchestraUserNamePasswordAuthenticationToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.session.config.ReactiveSessionRepositoryCustomizer;
import org.springframework.session.data.redis.ReactiveRedisSessionRepository;
import org.springframework.session.data.redis.config.annotation.web.server.EnableRedisWebSession;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * Configures Redis-backed reactive session management for the geOrchestra
 * Gateway.
 * <p>
 * This configuration enables Spring Session to use Redis as the session store
 * for reactive web sessions. When enabled, user sessions are stored in Redis,
 * allowing them to be shared across multiple gateway instances and persisted
 * across application restarts.
 * </p>
 * <p>
 * The configuration creates a reactive Redis connection factory for session
 * storage.
 * </p>
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableRedisWebSession
public class RedisSessionConfiguration {

    @Value("${spring.session.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.session.redis.port:6379}")
    private int redisPort;

    @Value("${spring.session.redis.password:}")
    private String redisPassword;

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName(redisHost);
        redisConfig.setPort(redisPort);

        if (StringUtils.hasText(redisPassword)) {
            redisConfig.setPassword(redisPassword);
        }

        var factory = new LettuceConnectionFactory(redisConfig);
        factory.setValidateConnection(false); // Disable validation for better performance
        factory.setShareNativeConnection(true); // Share connections
        factory.setEagerInitialization(true);
        return factory;
    }

    @Bean
    public RedisTemplate<String, String> redisTemplate(LettuceConnectionFactory lettuceConnectionFactory) {
        RedisTemplate<String, String> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(lettuceConnectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        return redisTemplate;
    }

    /**
     * Applies {@code spring.session.redis.timeout} to Redis sessions.
     * <p>
     * {@link EnableRedisWebSession} disables Spring Boot's session
     * auto-configuration and defaults to a 12 hours maximum inactive interval,
     * ignoring the standard session timeout property.
     * </p>
     */
    @Bean
    ReactiveSessionRepositoryCustomizer<ReactiveRedisSessionRepository> sessionTimeoutCustomizer(
            @Value("${spring.session.redis.timeout:12h}") Duration timeout) {
        return repository -> repository.setDefaultMaxInactiveInterval(timeout);
    }

    /**
     * Configures JSON-based Spring Data Redis serializer for better performance.
     * <p>
     * Uses Jackson for JSON serialization instead of Java serialization, which is
     * faster and produces smaller payloads. This significantly improves session
     * read/write performance.
     * </p>
     * <p>
     * The ObjectMapper is configured with Spring Security's Jackson2 modules to
     * properly serialize/deserialize Spring Security types, including custom
     * authentication tokens.
     * </p>
     *
     * @return a configured {@link RedisSerializer} using Jackson JSON serialization
     */
    @Bean
    public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        return new GenericJackson2JsonRedisSerializer(createObjectMapper());
    }

    static ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        // Enable default typing for polymorphic deserialization
        objectMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder().allowIfSubType("java.util.").allowIfSubType("java.lang.")
                        .allowIfSubType("java.time.")
                        // Allow Spring Security internals
                        .allowIfSubType("org.springframework.security.")
                        // Allow geOrchestra classes
                        .allowIfSubType("org.georchestra.").allowIfSubTypeIsArray().build(),
                ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);

        // Configure visibility to use fields for serialization
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
        objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

        // Register Spring Security's Jackson modules for proper Security type handling
        objectMapper
                .registerModules(SecurityJackson2Modules.getModules(RedisSessionConfiguration.class.getClassLoader()));

        // Register mixin for custom GeorchestraUserNamePasswordAuthenticationToken
        objectMapper.addMixIn(GeorchestraUserNamePasswordAuthenticationToken.class,
                GeorchestraUserNamePasswordAuthenticationTokenMixin.class);

        return objectMapper;
    }
}
