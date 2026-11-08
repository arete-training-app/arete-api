package app.arete.arete_api.security;

import app.arete.arete_api.exception.UnauthorizedException;
import app.arete.arete_api.util.Constants;
import app.arete.arete_api.util.enums.PublicEndpoints;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Authenticates requests from the access token cookie. Not a Spring bean on purpose: it is added to the
 * security chain in {@code SecurityConfig}, and registering it as a bean would also run it as a plain servlet filter.
 */

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final HandlerExceptionResolver handlerExceptionResolver;
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        return PublicEndpoints.isPublic(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
            final @NonNull HttpServletRequest request,
            final @NonNull HttpServletResponse response,
            final @NonNull FilterChain filterChain) throws ServletException, IOException {
        final Cookie accessTokenCookie = WebUtils.getCookie(request, Constants.ACCESS_TOKEN_COOKIE);
        if (accessTokenCookie == null || accessTokenCookie.getValue().isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            authenticate(jwtService.parseAccessToken(accessTokenCookie.getValue()));
        } catch (final UnauthorizedException exception) {
            log.debug("Rejected access token: {}", exception.getMessage());
            securityContextHolderStrategy.clearContext();
            handlerExceptionResolver.resolveException(request, response, null, exception);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(final Jwt jwt) {
        final List<String> roles = jwt.getClaimAsStringList(Constants.ROLES_CLAIM);
        final List<SimpleGrantedAuthority> authorities = roles == null
                ? List.of()
                : roles.stream().map(role -> new SimpleGrantedAuthority(Constants.ROLE_PREFIX + role)).toList();
        final UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(Objects.requireNonNull(jwt.getSubject()), null, authorities);
        final SecurityContext securityContext = securityContextHolderStrategy.createEmptyContext();
        securityContext.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(securityContext);
    }
}
