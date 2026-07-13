ALTER TABLE places ADD COLUMN IF NOT EXISTS ingestion_next_retry_at timestamp;
