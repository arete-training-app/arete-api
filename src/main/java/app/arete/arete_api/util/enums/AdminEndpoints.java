package app.arete.arete_api.util.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum AdminEndpoints {
    /**
     * Endpoints only admins may call, relative to the servlet context path.
     */
    ADMIN_API("/admin/**");

    private final String value;

    public static String[] patterns() {
        return Arrays.stream(values()).map(AdminEndpoints::getValue).toArray(String[]::new);
    }
}
