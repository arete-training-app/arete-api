-- Workout parts are called program parts, matching program_template_part. Renames keep the data and the deferred
-- order constraint; the table's own default keeps pointing at the renamed sequence.
ALTER TABLE workout_part RENAME TO program_part;
ALTER INDEX workout_part_pkey RENAME TO program_part_pkey;
ALTER SEQUENCE workout_part_id_seq RENAME TO program_part_id_seq;
ALTER TABLE program_part RENAME CONSTRAINT fk_workout_part_program_day TO fk_program_part_program_day;
ALTER TABLE program_part RENAME CONSTRAINT uq_workout_part_day_order TO uq_program_part_day_order;
ALTER TABLE program_part RENAME CONSTRAINT chk_workout_part_order_index TO chk_program_part_order_index;
ALTER TABLE program_part RENAME CONSTRAINT chk_workout_part_duration_minutes TO chk_program_part_duration_minutes;
ALTER INDEX idx_workout_part_program_day_id RENAME TO idx_program_part_program_day_id;
