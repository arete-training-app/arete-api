package app.arete.arete_api.controller;

import app.arete.arete_api.TestcontainersConfiguration;
import app.arete.arete_api.util.Constants;
import app.arete.arete_api.util.enums.RoleEnum;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Shared setup for the program API tests: a signed-in user, CSRF-protected requests and program creation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
abstract class AbstractProgramIntegrationTest {

    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";
    private static final String PASSWORD = "Str0ngP@ss";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    protected long createProgramAndGetId(final Cookie accessCookie) throws Exception {
        final String body = createProgram(accessCookie, "Athlete").getContentAsString();
        return JsonPath.<Number>read(body, "$.id").longValue();
    }

    protected MockHttpServletResponse createProgram(final Cookie accessCookie, final String name) throws Exception {
        return perform(withCsrf(post("/programs"))
                .cookie(accessCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s"}
                        """.formatted(name)));
    }

    protected Cookie registerAndGetAccessCookie() throws Exception {
        final String email = "user-" + UUID.randomUUID() + "@example.com";
        return perform(withCsrf(post("/auth/register"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"Jane Doe","email":"%s","password":"%s","confirmPassword":"%s"}
                        """.formatted(email, PASSWORD, PASSWORD)))
                .getCookie(Constants.ACCESS_TOKEN_COOKIE);
    }

    /**
     * Registers a user, grants the admin role in the database and logs in again, because roles are read from the
     * token issued at login.
     */
    protected Cookie registerAdminAndGetAccessCookie() throws Exception {
        final String email = "admin-" + UUID.randomUUID() + "@example.com";
        perform(withCsrf(post("/auth/register"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"Admin User","email":"%s","password":"%s","confirmPassword":"%s"}
                        """.formatted(email, PASSWORD, PASSWORD)));
        jdbcTemplate.update(
                "INSERT INTO user_role (user_id, role) SELECT id, ? FROM users WHERE email = ?",
                RoleEnum.ADMIN.name(),
                email);
        return perform(withCsrf(post("/auth/login"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, PASSWORD)))
                .getCookie(Constants.ACCESS_TOKEN_COOKIE);
    }

    protected MockHttpServletRequestBuilder withParams(
            final MockHttpServletRequestBuilder requestBuilder,
            final String... paramNamesAndValues) {
        for (int index = 0; index < paramNamesAndValues.length; index += 2) {
            requestBuilder.param(paramNamesAndValues[index], paramNamesAndValues[index + 1]);
        }
        return requestBuilder;
    }

    protected MockHttpServletRequestBuilder withCsrf(final MockHttpServletRequestBuilder requestBuilder)
            throws Exception {
        final Cookie csrfCookie = mockMvc.perform(get("/auth/csrf")).andReturn().getResponse().getCookie(CSRF_COOKIE);
        return requestBuilder.cookie(csrfCookie).header(CSRF_HEADER, csrfCookie.getValue());
    }

    protected MockHttpServletResponse perform(final MockHttpServletRequestBuilder requestBuilder) throws Exception {
        return mockMvc.perform(requestBuilder).andReturn().getResponse();
    }

    protected static List<String> readStrings(final String json, final String path) {
        return JsonPath.read(json, path);
    }
}
