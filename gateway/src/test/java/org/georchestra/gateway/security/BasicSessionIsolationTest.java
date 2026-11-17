package org.georchestra.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.WebSession;

import reactor.core.publisher.Mono;

class BasicSessionIsolationTest {
    private static final String CONTEXT = WebSessionServerSecurityContextRepository.DEFAULT_SPRING_SECURITY_CONTEXT_ATTR_NAME;
    private static final String VERIFIED = SessionAwareBasicAuthentication.VERIFIED_AT;
    private final MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/wms"));
    private final WebSession session = exchange.getSession().block();
    private final SessionAwareBasicAuthentication basic = new SessionAwareBasicAuthentication(
            token -> Mono.error(new BadCredentialsException("rejected")), Duration.ofHours(1), Clock.systemUTC());

    private Authentication user(String name) {
        return UsernamePasswordAuthenticationToken.authenticated(name, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private Authentication oidc() {
        var principal = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")), Map.of("sub", "alice"),
                "sub");
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "keycloak");
    }

    private void seed(Authentication authentication, boolean form) {
        session.start();
        session.getAttributes().put(CONTEXT, new SecurityContextImpl(authentication));
        if (form) {
            session.getAttributes().put(VERIFIED + ".form", true);
        }
    }

    private void assertPreservedOnSuccess(Authentication existing, boolean form, String basicName) throws Exception {
        seed(existing, form);
        String id = session.getId();
        var before = new HashMap<>(session.getAttributes());
        repository().save(exchange, new SecurityContextImpl(user(basicName))).block();
        assertThat(session.getId()).isEqualTo(id);
        assertThat(session.getAttributes()).isEqualTo(before);
        assertThat(session.isExpired()).isFalse();
    }

    private ServerSecurityContextRepository repository() throws Exception {
        var type = Class.forName(SessionAwareBasicAuthentication.class.getName() + "$BasicRepository");
        var constructor = type.getDeclaredConstructor(SessionAwareBasicAuthentication.class);
        constructor.setAccessible(true);
        return (ServerSecurityContextRepository) constructor.newInstance(basic);
    }

    @Test
    void successfulTechnicalBasicDoesNotReplaceOidcOrRotateItsSession() throws Exception {
        assertPreservedOnSuccess(oidc(), false, "geoserver_privileged_user");
    }

    @Test
    void successfulBasicWithSameNameDoesNotReplaceFormSession() throws Exception {
        assertPreservedOnSuccess(user("alice"), true, "alice");
    }

    @Test
    void successfulOtherBasicIdentityDoesNotReplaceExistingBasicSession() throws Exception {
        assertPreservedOnSuccess(user("alice"), false, "bob");
    }

    @SuppressWarnings("unchecked")
    private void failedVerification(Authentication existing, boolean form, String login, String requested,
            boolean invalidates) {
        seed(existing, form);
        session.getAttributes().put(VERIFIED + ".login", login);
        session.getAttributes().put(VERIFIED, 0L);
        session.getAttributes().put(VERIFIED + ".identity", ":" + existing.getName());
        var tracked = spy(session);
        Mono<Authentication> result = ReflectionTestUtils.invokeMethod(basic, "verify", exchange, tracked,
                UsernamePasswordAuthenticationToken.unauthenticated(requested, "wrong"), existing);
        assertThatThrownBy(result::block).isInstanceOf(BadCredentialsException.class);
        if (invalidates) {
            verify(tracked).invalidate();
        } else {
            verify(tracked, never()).invalidate();
        }
    }

    @Test
    void failedOtherBasicIdentityDoesNotInvalidateExistingSession() {
        failedVerification(user("alice"), false, "alice", "bob", false);
    }

    @Test
    void failedBasicWithSameNameDoesNotInvalidateFormSession() {
        failedVerification(user("alice"), true, "alice", "alice", false);
    }

    @Test
    void failedBasicDoesNotInvalidateOidcSession() {
        failedVerification(oidc(), false, "alice", "alice", false);
    }

    @Test
    void failedRenewalOfOwnBasicSessionStillInvalidatesIt() {
        failedVerification(user("alice"), false, "alice", "alice", true);
    }

    private void verified(Authentication authentication) throws Exception {
        var type = Class.forName(SessionAwareBasicAuthentication.class.getName() + "$Verified");
        var constructor = type.getDeclaredConstructor(Authentication.class, long.class, String.class);
        constructor.setAccessible(true);
        exchange.getAttributes().put(VERIFIED + ".result",
                constructor.newInstance(authentication, System.currentTimeMillis(), authentication.getName()));
    }

    @Test
    void newBasicLoginStillPersistsAndRotatesSessionId() throws Exception {
        session.start();
        String oldId = session.getId();
        Authentication alice = user("alice");
        verified(alice);
        repository().save(exchange, new SecurityContextImpl(alice)).block();
        assertThat(session.getId()).isNotEqualTo(oldId);
        assertThat(session.getAttribute(VERIFIED + ".login").toString()).isEqualTo("alice");
        SecurityContextImpl saved = session.getAttribute(CONTEXT);
        assertThat(saved.getAuthentication()).isSameAs(alice);
    }

    @Test
    void ownBasicRenewalStillReplacesAuthenticationWithoutRotation() throws Exception {
        seed(user("alice"), false);
        String id = session.getId();
        Authentication renewed = user("alice");
        verified(renewed);
        repository().save(exchange, new SecurityContextImpl(renewed)).block();
        assertThat(session.getId()).isEqualTo(id);
        SecurityContextImpl saved = session.getAttribute(CONTEXT);
        assertThat(saved.getAuthentication()).isSameAs(renewed);
        assertThat(session.getAttribute(VERIFIED + ".login").toString()).isEqualTo("alice");
    }
}
