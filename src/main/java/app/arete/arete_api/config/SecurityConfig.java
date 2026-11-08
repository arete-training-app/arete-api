package app.arete.arete_api.config;

import app.arete.arete_api.exception.ForbiddenException;
import app.arete.arete_api.exception.UnauthorizedException;
import app.arete.arete_api.security.JwtAuthenticationFilter;
import app.arete.arete_api.security.JwtService;
import app.arete.arete_api.util.enums.AdminEndpoints;
import app.arete.arete_api.util.enums.PublicEndpoints;
import app.arete.arete_api.util.enums.RoleEnum;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String AUTHENTICATION_REQUIRED_DETAIL = "Authentication required";
    private static final String CSRF_DETAIL = "Missing or invalid CSRF token";
    private static final String ACCESS_DENIED_DETAIL = "Access denied";
    private static final String CSRF_COOKIE_PATH = "/";

    /**
     * The SPA reads this cookie from {@code document.cookie} on its own pages, so it must be scoped to {@code /}.
     * The default path is the servlet context path ({@code /api/v1}), which hides it from the app's pages.
     */
    private static CookieCsrfTokenRepository csrfTokenRepository() {
        final CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.path(CSRF_COOKIE_PATH));
        return repository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            final HttpSecurity http,
            final JwtService jwtService,
            @Qualifier("handlerExceptionResolver") final HandlerExceptionResolver handlerExceptionResolver) {
        http
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PublicEndpoints.patterns()).permitAll()
                        .requestMatchers(AdminEndpoints.patterns()).hasRole(RoleEnum.ADMIN.getValue())
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                handlerExceptionResolver.resolveException(request, response, null,
                                        new UnauthorizedException(AUTHENTICATION_REQUIRED_DETAIL)))
                        .accessDeniedHandler((request, response, exception) ->
                                handlerExceptionResolver.resolveException(request, response, null,
                                        new ForbiddenException(exception instanceof CsrfException
                                                ? CSRF_DETAIL
                                                : ACCESS_DENIED_DETAIL))))
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService, handlerExceptionResolver),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            final UserDetailsService userDetailsService,
            final PasswordEncoder passwordEncoder) {
        final DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider(userDetailsService);
        authenticationProvider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(authenticationProvider);
    }
}
