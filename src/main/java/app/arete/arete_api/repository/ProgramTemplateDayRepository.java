package app.arete.arete_api.repository;

import app.arete.arete_api.model.entity.ProgramTemplateDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProgramTemplateDayRepository extends JpaRepository<ProgramTemplateDay, Long> {

    List<ProgramTemplateDay> findAllByProgramTemplateIdAndDayPositionBetweenOrderByDayPosition(
            final Long programTemplateId,
            final int fromPosition,
            final int toPosition);
}
