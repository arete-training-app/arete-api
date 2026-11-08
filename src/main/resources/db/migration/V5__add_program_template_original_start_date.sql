-- The date of position 1 in the source day files, so a program can be created on the template's original dates.
ALTER TABLE program_template ADD COLUMN original_start_date DATE;

-- Templates imported before this column existed never stored their source dates; their import date stands in for
-- them. Re-import such a template to get its real dates.
UPDATE program_template SET original_start_date = created_at::date WHERE original_start_date IS NULL;

ALTER TABLE program_template ALTER COLUMN original_start_date SET NOT NULL;
