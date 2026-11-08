package app.arete.arete_api.util.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Fields an import job list can be sorted by; the value is the entity property.
 */
@Getter
@RequiredArgsConstructor
public enum ImportJobSortField {
    CREATED_AT("createdAt"),
    UPDATED_AT("updatedAt");

    private final String value;
}
