package app.arete.arete_api.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Constants {

    public static final String ACCESS_TOKEN_COOKIE = "access_token";
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    public static final String ACCESS_TOKEN_COOKIE_PATH = "/";

    public static final String ROLES_CLAIM = "roles";
    public static final String ROLE_PREFIX = "ROLE_";

    public static final int MIN_JWT_SECRET_BYTES = 32;
    public static final int REFRESH_TOKEN_BYTES = 32;

    public static final int FULL_NAME_MAX_LENGTH = 100;

    // Matches the `Size` parameter maximum in common.yaml
    public static final int MAX_PAGE_SIZE = 100;

    public static final int DAYS_PER_WEEK = 7;

    public static final String GENERIC_ERROR_DETAIL = "An unexpected error occurred";
    public static final String INVALID_CREDENTIALS_DETAIL = "Invalid email or password";
    public static final String VALIDATION_FAILED_DETAIL = "Request validation failed";
    public static final String ERRORS_PROPERTY = "errors";
}
