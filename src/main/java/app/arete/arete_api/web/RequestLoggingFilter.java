package app.arete.arete_api.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Logs one line per request: method, path with query, status and duration. Ordered before Spring Security, so
 * rejected requests (401, 403, CSRF) are logged too. Bodies, headers and cookies are never logged, since they carry
 * credentials.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final FilterChain filterChain) throws ServletException, IOException {
        final long startNanos = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            final long durationMillis = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.info("{} {} -> {} ({} ms)",
                    request.getMethod(), describePath(request), response.getStatus(), durationMillis);
        }
    }

    private static String describePath(final HttpServletRequest request) {
        final String queryString = request.getQueryString();
        return queryString == null ? request.getRequestURI() : request.getRequestURI() + "?" + queryString;
    }
}
