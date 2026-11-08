package app.arete.arete_api.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * An ownerless blueprint of days at relative positions. It keeps the date its source started on, so a program can be
 * created on the original dates, but no record of which catalog entry it came from. Its days aren't mapped
 * as a collection: they are always loaded by position range through {@code ProgramTemplateDayRepository}, and the
 * database deletes them with the template.
 */
@Table(name = "program_template", schema = "public")
@Getter
@Setter
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class ProgramTemplate extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "content_hash", nullable = false, length = 64, unique = true)
    private String contentHash;

    @Column(name = "day_count", nullable = false)
    private int dayCount;

    @Column(name = "last_position", nullable = false)
    private int lastPosition;

    // The date of position 1 in the source day files
    @Column(name = "original_start_date", nullable = false)
    private LocalDate originalStartDate;
}
