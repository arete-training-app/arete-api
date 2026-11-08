package app.arete.arete_api.specification;

import app.arete.arete_api.model.dto.importjob.ImportJobFilterDto;
import app.arete.arete_api.model.entity.ImportJob;
import app.arete.arete_api.util.enums.ImportJobStatus;
import app.arete.arete_api.util.enums.ImportJobType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class ImportJobSpecifications {

    public Specification<ImportJob> buildFilters(final ImportJobFilterDto importJobFilterDto) {
        return Specification.allOf(
                hasType(importJobFilterDto.type()),
                hasStatus(importJobFilterDto.status()),
                hasCatalogEntry(importJobFilterDto.catalogEntry()));
    }

    private static Specification<ImportJob> hasCatalogEntry(final String catalogEntry) {
        if (catalogEntry == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(root.get("catalogEntry"), catalogEntry);
    }

    private static Specification<ImportJob> hasType(final ImportJobType type) {
        if (type == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    private static Specification<ImportJob> hasStatus(final ImportJobStatus status) {
        if (status == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
