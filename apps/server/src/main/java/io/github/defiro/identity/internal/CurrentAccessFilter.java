package io.github.defiro.identity.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives whoever is signed in the roles they have now, for this request only: the session keeps none.
 * Someone who is inactive, or whom defiro does not have, is signed out.
 *
 * <p>Not a bean: Spring Boot would also register it outside the security chain, where it would run
 * first and, running once per request, never run inside.
 */
class CurrentAccessFilter extends OncePerRequestFilter {

    private static final String ROLE = "ROLE_";

    private final AccessCache cache;

    CurrentAccessFilter(AccessCache cache) {
        this.cache = cache;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof OAuth2AuthenticationToken signedIn) {
            Access access = accessOf(signedIn.getName());
            if (access.active()) {
                SecurityContext now = SecurityContextHolder.createEmptyContext();
                now.setAuthentication(with(access, signedIn));
                SecurityContextHolder.setContext(now);
            } else {
                signOut(request);
            }
        }
        chain.doFilter(request, response);
    }

    private Access accessOf(String name) {
        try {
            return cache.of(UUID.fromString(name));
        } catch (IllegalArgumentException noIdOfDefiros) {
            return Access.NONE;
        }
    }

    private static OAuth2AuthenticationToken with(Access access, OAuth2AuthenticationToken signedIn) {
        List<GrantedAuthority> authorities = Stream.concat(
                        signedIn.getAuthorities().stream()
                                .filter(authority -> !authority.getAuthority().startsWith(ROLE)),
                        access.roles().stream().map(role -> new SimpleGrantedAuthority(ROLE + role)))
                .toList();
        OAuth2AuthenticationToken current = new OAuth2AuthenticationToken(
                signedIn.getPrincipal(), authorities, signedIn.getAuthorizedClientRegistrationId());
        current.setDetails(signedIn.getDetails());
        return current;
    }

    private static void signOut(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}
