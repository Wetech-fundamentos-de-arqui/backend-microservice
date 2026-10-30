-- Persist the sequence of GPS points. The temporary key preserves the scan order
-- of any existing unordered collection while assigning a separate index per trip.
ALTER TABLE trip_route_coordinates
    ADD COLUMN migration_row_id BIGINT NOT NULL AUTO_INCREMENT UNIQUE,
    ADD COLUMN point_index INT NULL;

UPDATE trip_route_coordinates AS points
JOIN (
    SELECT migration_row_id,
           ROW_NUMBER() OVER (PARTITION BY trip_id ORDER BY migration_row_id) - 1 AS position_in_trip
    FROM trip_route_coordinates
) AS positions ON points.migration_row_id = positions.migration_row_id
SET points.point_index = positions.position_in_trip;

ALTER TABLE trip_route_coordinates
    DROP COLUMN migration_row_id,
    MODIFY COLUMN point_index INT NOT NULL,
    ADD PRIMARY KEY (trip_id, point_index);
