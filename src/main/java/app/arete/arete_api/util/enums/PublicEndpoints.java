package app.arete.arete_api.util.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum PublicEndpoints {
    /**
     * Public endpoints, relative to the servlet context path. Everything else requires a valid access token.
     */
    REGISTER("/auth/register"),
    LOGIN("/auth/login"),
    GOOGLE_LOGIN("/auth/google"),
    REFRESH("/auth/refresh"),
    LOGOUT("/auth/logout"),
    CSRF("/auth/csrf"),
    ERROR("/error"),
    SWAGGER_UI_PAGE("/swagger-ui.html"),
    SWAGGER_UI("/swagger-ui/**"),
    SWAGGER_CONFIG("/v3/api-docs/**"),
    OPENAPI_SPECS("/openapi/**");

    private final String value;

    public static String[] patterns() {
        return Arrays.stream(values()).map(PublicEndpoints::getValue).toArray(String[]::new);
    }

    public static boolean isPublic(final String path) {
        final PathContainer pathContainer = PathContainer.parsePath(path);
        return Arrays.stream(values())
                .anyMatch(endpoint -> PathPatternParser.defaultInstance.parse(endpoint.getValue()).matches(pathContainer));
    }
}
