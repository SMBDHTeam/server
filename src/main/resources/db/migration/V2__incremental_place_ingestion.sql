ALTER TABLE places ADD COLUMN IF NOT EXISTS source_modified_at timestamp;
ALTER TABLE places ADD COLUMN IF NOT EXISTS last_seen_at timestamp;
ALTER TABLE places ADD COLUMN IF NOT EXISTS last_synced_at timestamp;
ALTER TABLE places ADD COLUMN IF NOT EXISTS ingestion_status varchar(255);
ALTER TABLE places ADD COLUMN IF NOT EXISTS ingestion_retry_count integer;
ALTER TABLE places ADD COLUMN IF NOT EXISTS ingestion_last_error text;

UPDATE places
SET last_seen_at = COALESCE(last_seen_at, updated_at),
    last_synced_at = COALESCE(last_synced_at, updated_at),
    ingestion_status = COALESCE(ingestion_status, 'SYNCED'),
    ingestion_retry_count = COALESCE(ingestion_retry_count, 0);

ALTER TABLE places ALTER COLUMN last_seen_at SET NOT NULL;
ALTER TABLE places ALTER COLUMN ingestion_status SET NOT NULL;
ALTER TABLE places ALTER COLUMN ingestion_retry_count SET NOT NULL;

CREATE TABLE IF NOT EXISTS place_ingestion_locks (
    lock_name varchar(255) PRIMARY KEY,
    locked_by varchar(255) NOT NULL,
    locked_until timestamp NOT NULL
);

CREATE TABLE IF NOT EXISTS tour_api_request_usage (
    usage_date date PRIMARY KEY,
    requests_used integer NOT NULL,
    updated_at timestamp NOT NULL,
    CONSTRAINT ck_tour_api_request_usage_non_negative CHECK (requests_used >= 0)
);
