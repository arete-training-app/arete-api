package app.arete.arete_api.model.entity;

import app.arete.arete_api.util.Constants;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Table(
        name = "program_template_day",
        schema = "public",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_program_template_day_template_position",
                columnNames = {"program_template_id", "day_position"})
)
@Getter
@Setter
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class ProgramTemplateDay extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_template_id", nullable = false)
    private ProgramTemplate programTemplate;

    // 1-based offset from the template's first day
    @Column(name = "day_position", nullable = false)
    private int dayPosition;

    // Days are loaded by position range; batching loads their parts in one query instead of one per day
    @Builder.Default
    @BatchSize(size = Constants.MAX_PAGE_SIZE)
    @OneToMany(mappedBy = "programTemplateDay", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private Set<ProgramTemplatePart> parts = new LinkedHashSet<>();

    /**
     * Adds the parts after the existing ones, in the given order.
     */
    public void appendParts(final List<ProgramTemplatePart> orderedParts) {
        int orderIndex = this.parts.size();
        for (final ProgramTemplatePart part : orderedParts) {
            part.setProgramTemplateDay(this);
            part.setOrderIndex(orderIndex++);
            this.parts.add(part);
        }
    }
}
