ALTER TABLE questions ADD COLUMN IF NOT EXISTS min_selections integer NOT NULL DEFAULT 1;
ALTER TABLE questions ADD COLUMN IF NOT EXISTS max_selections integer NOT NULL DEFAULT 1;

ALTER TABLE places ADD COLUMN IF NOT EXISTS place_url text;

CREATE TABLE schedule_previews (
    id uuid PRIMARY KEY,
    status varchar(32) NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    time_zone varchar(64) NOT NULL,
    lodging_mode varchar(32) NOT NULL,
    route_coverage varchar(64) NOT NULL,
    input_json text NOT NULL,
    resolved_days_json text NOT NULL,
    resolved_end_constraint_json text,
    applied_defaults_json text NOT NULL,
    interpreted_prompt_json text NOT NULL,
    warnings_json text NOT NULL,
    conflicts_json text NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    consumed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL
);

ALTER TABLE schedules ADD COLUMN IF NOT EXISTS preview_id uuid REFERENCES schedule_previews(id);
ALTER TABLE schedules ADD COLUMN IF NOT EXISTS time_zone varchar(64);
ALTER TABLE schedules ADD COLUMN IF NOT EXISTS lodging_mode varchar(32);
ALTER TABLE schedules ADD COLUMN IF NOT EXISTS route_coverage varchar(64);
ALTER TABLE schedules ADD COLUMN IF NOT EXISTS planning_warnings_json text;
ALTER TABLE schedules ALTER COLUMN end_place_name DROP NOT NULL;
ALTER TABLE schedules ALTER COLUMN end_longitude DROP NOT NULL;
ALTER TABLE schedules ALTER COLUMN end_latitude DROP NOT NULL;
CREATE UNIQUE INDEX uk_schedules_preview_id ON schedules(preview_id) WHERE preview_id IS NOT NULL;

ALTER TABLE schedule_days ADD COLUMN IF NOT EXISTS start_location_source varchar(32);
ALTER TABLE schedule_days ADD COLUMN IF NOT EXISTS end_location_source varchar(32);
ALTER TABLE schedule_days ALTER COLUMN start_place_name DROP NOT NULL;
ALTER TABLE schedule_days ALTER COLUMN start_longitude DROP NOT NULL;
ALTER TABLE schedule_days ALTER COLUMN start_latitude DROP NOT NULL;
ALTER TABLE schedule_days ALTER COLUMN end_place_name DROP NOT NULL;
ALTER TABLE schedule_days ALTER COLUMN end_longitude DROP NOT NULL;
ALTER TABLE schedule_days ALTER COLUMN end_latitude DROP NOT NULL;

CREATE TABLE schedule_creation_requests (
    id uuid PRIMARY KEY,
    idempotency_key varchar(128) NOT NULL,
    preview_id uuid NOT NULL REFERENCES schedule_previews(id),
    request_hash varchar(64) NOT NULL,
    status varchar(32) NOT NULL,
    schedule_id uuid REFERENCES schedules(id),
    response_status integer,
    response_json text,
    last_error_code varchar(128),
    created_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone,
    expires_at timestamp with time zone NOT NULL,
    CONSTRAINT uk_schedule_creation_requests_key UNIQUE (idempotency_key)
);

ALTER TABLE schedule_stops ADD COLUMN IF NOT EXISTS fixed_starts_at timestamp with time zone;
ALTER TABLE schedule_stops ADD COLUMN IF NOT EXISTS fixed_ends_at timestamp with time zone;

CREATE TABLE schedule_fixed_events (
    id uuid PRIMARY KEY,
    schedule_id uuid NOT NULL REFERENCES schedules(id),
    schedule_stop_id uuid NOT NULL UNIQUE REFERENCES schedule_stops(id),
    client_event_id varchar(255) NOT NULL,
    name varchar(255) NOT NULL,
    starts_at timestamp with time zone NOT NULL,
    ends_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT uk_schedule_fixed_events_client UNIQUE (schedule_id, client_event_id)
);
