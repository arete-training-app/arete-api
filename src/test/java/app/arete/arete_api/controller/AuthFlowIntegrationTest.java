package app.arete.arete_api.controller;

import app.arete.arete_api.MutableClock;
import app.arete.arete_api.TestcontainersConfiguration;
import app.arete.arete_api.exception.UnauthorizedException;
import app.arete.arete_api.model.dto.auth.GoogleIdentity;
import app.arete.arete_api.security.GoogleIdTokenVerifier;
import app.arete.arete_api.util.Constants;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, AuthFlowIntegrationTest.ClockConfiguration.class})
class AuthFlowIntegrationTest {

    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";
    private static final String PASSWORD = "Str0ngP@ss";
    private static final String GOOGLE_ID_TOKEN = "google-id-token";
    private static final Duration PAST_ROTATION_GRACE_PERIOD = Duration.ofMinutes(1);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MutableClock mutableClock;

    @MockitoBean
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfiguration {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.now());
        }
    }

    @Test
    void csrfEndpointSetsReadableCsrfCookie() throws Exception {
        final MockHttpServletResponse response = mockMvc.perform(get("/auth/csrf")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(204);
        final Cookie csrfCookie = response.getCookie(CSRF_COOKIE);
        assertThat(csrfCookie).isNotNull();
        assertThat(csrfCookie.isHttpOnly()).isFalse();
        assertThat(csrfCookie.getPath()).isEqualTo("/");
    }

    @Test
    void registerSetsHttpOnlySessionCookiesAndMeReturnsTheUser() throws Exception {
        final String email = uniqueEmail();
        final MockHttpServletResponse registerResponse = register(email);

        assertThat(registerResponse.getStatus()).isEqualTo(201);
        final Cookie accessCookie = registerResponse.getCookie(Constants.ACCESS_TOKEN_COOKIE);
        final Cookie refreshCookie = registerResponse.getCookie(Constants.REFRESH_TOKEN_COOKIE);
        assertThat(accessCookie).isNotNull();
        assertThat(accessCookie.isHttpOnly()).isTrue();
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.isHttpOnly()).isTrue();
        assertThat(refreshCookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(registerResponse.getContentAsString()).contains(email).doesNotContain(PASSWORD);

        final MockHttpServletResponse meResponse = getMe(accessCookie);
        assertThat(meResponse.getStatus()).isEqualTo(200);
        assertThat(meResponse.getContentAsString())
                .contains(email)
                .contains("\"passwordSet\":true")
                .contains("\"roles\":[\"USER\"]");
    }

    @Test
    void registerWithTakenEmailIsConflict() throws Exception {
        final String email = uniqueEmail();
        register(email);

        final MockHttpServletResponse response = register(email.toUpperCase());

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    }

    @Test
    void registerWithInvalidBodyIsBadRequestWithFieldErrors() throws Exception {
        final MockHttpServletResponse response = perform(withCsrf(post("/auth/register"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"J","email":"not-an-email","password":"short","confirmPassword":"short"}
                        """));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("\"errors\"").contains("email").contains("password");
    }

    @Test
    void meWithoutCookieIsUnauthorizedProblem() throws Exception {
        final MockHttpServletResponse response = perform(get("/me"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    }

    @Test
    void meWithGarbageCookieIsUnauthorizedProblem() throws Exception {
        final MockHttpServletResponse response = getMe(new Cookie(Constants.ACCESS_TOKEN_COOKIE, "garbage"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("Invalid or expired access token");
    }

    @Test
    void loginWithWrongPasswordAndUnknownEmailGiveTheSameUnauthorized() throws Exception {
        final String email = uniqueEmail();
        register(email);

        final MockHttpServletResponse wrongPassword = login(email, "WrongPassword1");
        final MockHttpServletResponse unknownEmail = login(uniqueEmail(), PASSWORD);

        assertThat(wrongPassword.getStatus()).isEqualTo(401);
        assertThat(unknownEmail.getStatus()).isEqualTo(401);
        assertThat(wrongPassword.getContentAsString()).contains(Constants.INVALID_CREDENTIALS_DETAIL);
        assertThat(unknownEmail.getContentAsString()).contains(Constants.INVALID_CREDENTIALS_DETAIL);
    }

    @Test
    void loginWithCorrectPasswordSetsSessionCookies() throws Exception {
        final String email = uniqueEmail();
        register(email);

        final MockHttpServletResponse response = login(email, PASSWORD);

        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(getMe(response.getCookie(Constants.ACCESS_TOKEN_COOKIE)).getStatus()).isEqualTo(200);
    }

    @Test
    void refreshRotatesTokenAndReuseOfOldTokenRevokesTheFamily() throws Exception {
        final Cookie originalRefreshCookie = register(uniqueEmail()).getCookie(Constants.REFRESH_TOKEN_COOKIE);

        final MockHttpServletResponse firstRefresh = refresh(originalRefreshCookie);
        assertThat(firstRefresh.getStatus()).isEqualTo(204);
        final Cookie rotatedRefreshCookie = firstRefresh.getCookie(Constants.REFRESH_TOKEN_COOKIE);
        assertThat(rotatedRefreshCookie.getValue()).isNotEqualTo(originalRefreshCookie.getValue());
        assertThat(getMe(firstRefresh.getCookie(Constants.ACCESS_TOKEN_COOKIE)).getStatus()).isEqualTo(200);

        // Replaying the rotated-out token inside the grace period is treated as a race: rejected, family kept.
        assertThat(refresh(originalRefreshCookie).getStatus()).isEqualTo(401);
        final MockHttpServletResponse secondRefresh = refresh(rotatedRefreshCookie);
        assertThat(secondRefresh.getStatus()).isEqualTo(204);

        // Replaying a rotated-out token after the grace period is reuse: the whole family dies, newest token included.
        final Cookie newestRefreshCookie = secondRefresh.getCookie(Constants.REFRESH_TOKEN_COOKIE);
        mutableClock.advance(PAST_ROTATION_GRACE_PERIOD);
        assertThat(refresh(rotatedRefreshCookie).getStatus()).isEqualTo(401);
        assertThat(refresh(newestRefreshCookie).getStatus()).isEqualTo(401);
    }

    @Test
    void refreshWithoutCookieIsUnauthorized() throws Exception {
        assertThat(perform(withCsrf(post("/auth/refresh"))).getStatus()).isEqualTo(401);
    }

    @Test
    void logoutClearsCookiesAndKillsTheRefreshToken() throws Exception {
        final Cookie refreshCookie = register(uniqueEmail()).getCookie(Constants.REFRESH_TOKEN_COOKIE);

        final MockHttpServletResponse logoutResponse =
                perform(withCsrf(post("/auth/logout")).cookie(refreshCookie));

        assertThat(logoutResponse.getStatus()).isEqualTo(204);
        assertThat(logoutResponse.getCookie(Constants.ACCESS_TOKEN_COOKIE).getMaxAge()).isZero();
        assertThat(logoutResponse.getCookie(Constants.REFRESH_TOKEN_COOKIE).getMaxAge()).isZero();
        assertThat(refresh(refreshCookie).getStatus()).isEqualTo(401);
    }

    @Test
    void stateChangingRequestWithoutCsrfTokenIsForbiddenProblem() throws Exception {
        final MockHttpServletResponse response = perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(uniqueEmail(), PASSWORD)));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("CSRF");
    }

    @Test
    void updateMeChangesTheFullName() throws Exception {
        final Cookie accessCookie = register(uniqueEmail()).getCookie(Constants.ACCESS_TOKEN_COOKIE);

        final MockHttpServletResponse response = perform(withCsrf(put("/me"))
                .cookie(accessCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"Jane Renamed"}
                        """));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("Jane Renamed");
    }

    @Test
    void googleLoginCreatesUserAndFindsItBySubjectNextTime() throws Exception {
        final String email = uniqueEmail();
        final String subject = UUID.randomUUID().toString();
        when(googleIdTokenVerifier.verify(anyString())).thenReturn(new GoogleIdentity(subject, email, true, "Gina Google"));

        final MockHttpServletResponse firstLogin = loginWithGoogle();
        assertThat(firstLogin.getStatus()).isEqualTo(204);
        final String firstMe = getMe(firstLogin.getCookie(Constants.ACCESS_TOKEN_COOKIE)).getContentAsString();
        assertThat(firstMe)
                .contains(email)
                .contains("\"passwordSet\":false")
                .contains("\"emailVerified\":true")
                .contains("\"providers\":[\"GOOGLE\"]");

        // Same Google account, different email at Google: still the same Arete user.
        when(googleIdTokenVerifier.verify(anyString()))
                .thenReturn(new GoogleIdentity(subject, uniqueEmail(), true, "Gina Google"));
        final MockHttpServletResponse secondLogin = loginWithGoogle();
        final String secondMe = getMe(secondLogin.getCookie(Constants.ACCESS_TOKEN_COOKIE)).getContentAsString();
        assertThat(secondMe).contains(email);
    }

    @Test
    void googleLoginLinksUnverifiedPasswordAccountAndRemovesItsPassword() throws Exception {
        final String email = uniqueEmail();
        final Cookie squatterRefreshCookie = register(email).getCookie(Constants.REFRESH_TOKEN_COOKIE);
        when(googleIdTokenVerifier.verify(anyString()))
                .thenReturn(new GoogleIdentity(UUID.randomUUID().toString(), email, true, "Real Owner"));

        final MockHttpServletResponse googleLogin = loginWithGoogle();

        assertThat(googleLogin.getStatus()).isEqualTo(204);
        assertThat(getMe(googleLogin.getCookie(Constants.ACCESS_TOKEN_COOKIE)).getContentAsString())
                .contains("\"passwordSet\":false")
                .contains("\"providers\":[\"GOOGLE\"]");
        assertThat(login(email, PASSWORD).getStatus()).isEqualTo(401);
        assertThat(refresh(squatterRefreshCookie).getStatus()).isEqualTo(401);
    }

    @Test
    void googleLoginWithUnverifiedEmailAndNoAccountIsUnauthorized() throws Exception {
        when(googleIdTokenVerifier.verify(anyString()))
                .thenReturn(new GoogleIdentity(UUID.randomUUID().toString(), uniqueEmail(), false, "Unverified"));

        assertThat(loginWithGoogle().getStatus()).isEqualTo(401);
    }

    @Test
    void googleLoginWithInvalidTokenIsUnauthorized() throws Exception {
        when(googleIdTokenVerifier.verify(anyString())).thenThrow(new UnauthorizedException("Invalid Google ID token"));

        assertThat(loginWithGoogle().getStatus()).isEqualTo(401);
    }

    private MockHttpServletResponse register(final String email) throws Exception {
        return perform(withCsrf(post("/auth/register"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"Jane Doe","email":"%s","password":"%s","confirmPassword":"%s"}
                        """.formatted(email, PASSWORD, PASSWORD)));
    }

    private MockHttpServletResponse login(final String email, final String password) throws Exception {
        return perform(withCsrf(post("/auth/login"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, password)));
    }

    private MockHttpServletResponse loginWithGoogle() throws Exception {
        return perform(withCsrf(post("/auth/google"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"idToken":"%s"}
                        """.formatted(GOOGLE_ID_TOKEN)));
    }

    private MockHttpServletResponse refresh(final Cookie refreshCookie) throws Exception {
        return perform(withCsrf(post("/auth/refresh")).cookie(refreshCookie));
    }

    private MockHttpServletResponse getMe(final Cookie accessCookie) throws Exception {
        return perform(get("/me").cookie(accessCookie));
    }

    private MockHttpServletRequestBuilder withCsrf(final MockHttpServletRequestBuilder requestBuilder) throws Exception {
        final Cookie csrfCookie = mockMvc.perform(get("/auth/csrf")).andReturn().getResponse().getCookie(CSRF_COOKIE);
        return requestBuilder.cookie(csrfCookie).header(CSRF_HEADER, csrfCookie.getValue());
    }

    private MockHttpServletResponse perform(final MockHttpServletRequestBuilder requestBuilder) throws Exception {
        return mockMvc.perform(requestBuilder).andReturn().getResponse();
    }

    private static String loginBody(final String email, final String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
