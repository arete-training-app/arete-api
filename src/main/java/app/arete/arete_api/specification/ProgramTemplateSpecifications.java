package app.arete.arete_api.specification;

import app.arete.arete_api.model.dto.programtemplate.ProgramTemplateFilterDto;
import app.arete.arete_api.model.entity.ProgramTemplate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class ProgramTemplateSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    public Specification<ProgramTemplate> buildFilters(final ProgramTemplateFilterDto programTemplateFilterDto) {
        return Specification.allOf(nameContains(programTemplateFilterDto.name()));
    }

    private static Specification<ProgramTemplate> nameContains(final String name) {
        if (name == null || name.isBlank()) {
            return Specification.unrestricted();
        }
        final String pattern = "%" + escapeLike(name.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern, LIKE_ESCAPE);
    }

    private static String escapeLike(final String value) {
        return value
                .replace(String.valueOf(LIKE_ESCAPE), String.valueOf(LIKE_ESCAPE) + LIKE_ESCAPE)
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
    }
}
