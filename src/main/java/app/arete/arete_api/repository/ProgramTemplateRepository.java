package app.arete.arete_api.repository;

import app.arete.arete_api.model.entity.ProgramTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProgramTemplateRepository
        extends JpaRepository<ProgramTemplate, Long>, JpaSpecificationExecutor<ProgramTemplate> {

    Optional<ProgramTemplate> findByContentHash(final String contentHash);

    List<ProgramTemplate> findAllByContentHashIn(final Collection<String> contentHashes);
}
