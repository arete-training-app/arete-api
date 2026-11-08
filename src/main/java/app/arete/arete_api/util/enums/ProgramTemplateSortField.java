package app.arete.arete_api.util.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Fields a program template list can be sorted by; the value is the entity property.
 */
@Getter
@RequiredArgsConstructor
public enum ProgramTemplateSortField {
    NAME("name"),
    CREATED_AT("createdAt"),
    DAY_COUNT("dayCount");

    private final String value;
}
