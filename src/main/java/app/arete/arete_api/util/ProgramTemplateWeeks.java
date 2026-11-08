package app.arete.arete_api.util;

import lombok.experimental.UtilityClass;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * Places template positions in Monday-based weeks. Position 1 fell on the template's original start date, so every
 * position's weekday is known without storing it, and a program can keep each workout on its original weekday.
 */
@UtilityClass
public class ProgramTemplateWeeks {

    /**
     * The days of the first week before position 1: 0 when the source started on a Monday, 6 on a Sunday.
     */
    public static int leadingDays(final LocalDate originalStartDate) {
        return originalStartDate.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue();
    }

    /**
     * The 1-based Monday-based week of a position, counting the source's partial first week as week 1.
     */
    public static int weekOfPosition(final int position, final LocalDate originalStartDate) {
        return (position - 1 + leadingDays(originalStartDate)) / Constants.DAYS_PER_WEEK + 1;
    }

    public static DayOfWeek dayOfWeekOfPosition(final int position, final LocalDate originalStartDate) {
        return originalStartDate.plusDays(position - 1L).getDayOfWeek();
    }

    /**
     * The date position 1 lands on when the template starts in the week of {@code startWeek}: the same weekday as in
     * the source, so every workout keeps its weekday.
     */
    public static LocalDate startDateInWeek(final LocalDate startWeek, final LocalDate originalStartDate) {
        return startWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .plusDays(leadingDays(originalStartDate));
    }
}
