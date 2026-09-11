-- Planned and spontaneous trips share the existing schedule graph.
ALTER TABLE schedules ADD COLUMN schedule_type varchar(32) NOT NULL DEFAULT 'PLANNED';
ALTER TABLE schedules ADD COLUMN transport_mode varchar(32);
ALTER TABLE schedules ADD COLUMN start_at timestamp with time zone;
ALTER TABLE schedules ADD COLUMN return_by timestamp with time zone;
ALTER TABLE schedules ADD COLUMN estimated_return_at timestamp with time zone;
ALTER TABLE schedules ADD COLUMN spontaneous_metadata_json text;
ALTER TABLE schedules ADD CONSTRAINT ck_schedules_schedule_type
    CHECK (schedule_type IN ('PLANNED', 'SPONTANEOUS'));

-- LocalTime remains for old clients; these columns preserve the date and offset.
ALTER TABLE schedule_stops ADD COLUMN arrive_at_datetime timestamp with time zone;
ALTER TABLE schedule_stops ADD COLUMN depart_at_datetime timestamp with time zone;
ALTER TABLE schedule_stops ADD COLUMN role varchar(32);
ALTER TABLE schedule_stops ADD COLUMN themes_json text NOT NULL DEFAULT '[]';

ALTER TABLE transit_routes ADD COLUMN depart_at_datetime timestamp with time zone;
ALTER TABLE transit_routes ADD COLUMN arrive_at_datetime timestamp with time zone;

-- Reuse the existing creation-request table for atomic spontaneous saves.
ALTER TABLE schedule_creation_requests ALTER COLUMN preview_id DROP NOT NULL;
ALTER TABLE schedule_creation_requests ADD COLUMN user_id bigint REFERENCES users(id);
ALTER TABLE schedule_creation_requests ADD COLUMN request_type varchar(32) NOT NULL DEFAULT 'PLANNED';
ALTER TABLE schedule_creation_requests ADD COLUMN spontaneous_preview_id uuid;
ALTER TABLE schedule_creation_requests DROP CONSTRAINT uk_schedule_creation_requests_key;
ALTER TABLE schedule_creation_requests ADD CONSTRAINT ck_schedule_creation_request_type
    CHECK (request_type IN ('PLANNED', 'SPONTANEOUS'));

-- Legacy planned requests have no owner and retain their former global-key behavior.
CREATE UNIQUE INDEX uk_schedule_creation_requests_legacy_key
    ON schedule_creation_requests(idempotency_key)
    WHERE user_id IS NULL;

-- Authenticated spontaneous requests scope keys to the current user.
CREATE UNIQUE INDEX uk_schedule_creation_requests_user_key
    ON schedule_creation_requests(user_id, idempotency_key)
    WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX uk_schedule_creation_requests_spontaneous_preview
    ON schedule_creation_requests(user_id, spontaneous_preview_id)
    WHERE user_id IS NOT NULL AND spontaneous_preview_id IS NOT NULL;

CREATE INDEX idx_schedules_user_type_start
    ON schedules(user_id, schedule_type, start_date);
